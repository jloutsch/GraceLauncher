package com.galaxyrio.gracelauncher.data

import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
import android.graphics.drawable.Drawable
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import com.galaxyrio.gracelauncher.SettingsActivity
import com.galaxyrio.gracelauncher.data.icons.IconPackRepository
import com.galaxyrio.gracelauncher.data.icons.IconDominantColor
import com.galaxyrio.gracelauncher.data.icons.IconSnapshotCache
import com.galaxyrio.gracelauncher.data.icons.IconResourceConfiguration
import com.galaxyrio.gracelauncher.data.icons.ProfileIconSource
import com.galaxyrio.gracelauncher.data.icons.renderIcon
import com.galaxyrio.gracelauncher.data.icons.PackIcon
import com.galaxyrio.gracelauncher.data.icons.iconBitmapBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class WorkProfile(val user: UserHandle, val serial: Long)

data class AppCatalog(
    val apps: List<LauncherApp>,
    val workProfiles: List<WorkProfile>,
    val complete: Boolean,
)

class AppRepository(context: Context, private val iconPacks: IconPackRepository = IconPackRepository.get(context)) {
    private val context = context.applicationContext
    private val packageManager = this.context.packageManager
    private val launcherApps by lazy { this.context.getSystemService(LauncherApps::class.java) }
    private val profiles by lazy { LauncherProfiles(this.context) }
    private data class AppSourceSignature(val configuration: IconResourceConfiguration,
        val label: String, val installedAt: Long, val updatedAt: Long, val version: Long,
        val apkPaths: List<String>, val flags: Int, val iconResource: Int, val revision: Long,
        val profileType: LauncherProfileType, val generation: Long)
    private val personalSources = IconSnapshotCache<AppSourceSignature, LauncherApp>(
        maxBytes = 32 * 1024 * 1024, sizeOf = { iconBitmapBytes(it.icon, it.monochromeIcon) })
    private val profileSources = IconSnapshotCache<AppSourceSignature, LauncherApp>()
    private data class PackSignature(val source: Any?,
        val packs: List<Any>, val day: Int?)
    private val packedIcons = IconSnapshotCache<PackSignature, Pair<String, PackIcon>>(maxBytes = 8 * 1024 * 1024,
        sizeOf = { iconBitmapBytes(it.second.bitmap, it.second.monochrome) })
    private val packageRevisions = mutableMapOf<Pair<UserHandle, String>, Long>()
    private val invalidations = Any()
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()
    private var allGeneration = 0L
    private var profilesGeneration = 0L
    private val pendingPackages = mutableMapOf<Pair<UserHandle, String>, Long>()
    private val blockedUsers = mutableMapOf<UserHandle, Long>()
    private data class RemovedProfile(val serial: Long?, val at: Long)
    private val removedUsers = mutableMapOf<UserHandle, RemovedProfile>()
    private val knownSerials = mutableMapOf<UserHandle, Long>()
    private val modelMutex = Mutex()
    private var lastSnapshot = LauncherProfileSnapshot(emptyList(), complete = false)
    private var snapshotAt = -PROFILE_CHECK_INTERVAL
    private var observedProfilesGeneration = -1L
    private data class Retry(val token: Long, val at: Long, var announced: Boolean = false)
    private data class ActivityQuery(val activities: List<LauncherActivityInfo>?, val stamp: Long,
        val error: Exception? = null)
    private data class ActivityFingerprint(val component: ComponentName, val source: String?,
        val splits: List<String>, val flags: Int)
    private class ProfileModel(val serial: Long, val type: LauncherProfileType) {
        val activities = linkedMapOf<ComponentName, LauncherActivityInfo>()
        val apps = linkedMapOf<ComponentName, LauncherApp>()
        val packageRetries = mutableMapOf<String, Retry>()
        var fullRetry: Retry? = null
        var fullError: Exception? = null
        var fullFailureRevision = -1L
        var initialized = false
        var generation = -1L
        var configuration: IconResourceConfiguration? = null
    }
    private val models = mutableMapOf<UserHandle, ProfileModel>()
    private val inventoryQueries = mutableMapOf<UserHandle, ActivityQuery>()
    private val inventories = mutableMapOf<UserHandle, List<ActivityFingerprint>>()

    private val packageChanges = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = invalidateApps(user, listOf(packageName))
        override fun onPackageRemoved(packageName: String, user: UserHandle) = invalidateApps(user, listOf(packageName))
        override fun onPackageChanged(packageName: String, user: UserHandle) = invalidateApps(user, listOf(packageName))
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            invalidateApps(user, packageNames.toList())
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            invalidateApps(user, packageNames.toList())
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) =
            invalidateApps(user, packageNames.toList())
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) =
            invalidateApps(user, packageNames.toList())
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<android.content.pm.ShortcutInfo>, user: UserHandle) {
            // Shortcut changes require decoration again, but not another installed-app query.
            synchronized(invalidations) { _revision.value++ }
        }
    }
    private val profileChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val user = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
                else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER)
                }
            synchronized(invalidations) {
                if (user != null) {
                    when (intent.action) {
                        Intent.ACTION_MANAGED_PROFILE_REMOVED, Intent.ACTION_PROFILE_REMOVED ->
                            removedUsers[user] = RemovedProfile(knownSerials[user], SystemClock.elapsedRealtime())
                        Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE, Intent.ACTION_PROFILE_INACCESSIBLE,
                        Intent.ACTION_PROFILE_UNAVAILABLE -> blockedUsers[user] = SystemClock.elapsedRealtime()
                        else -> { removedUsers.remove(user); blockedUsers.remove(user) }
                    }
                }
                profilesGeneration++
                _revision.value++
            }
        }
    }

    init {
        // These observers live with the process model, including while every Activity is stopped.
        launcherApps.registerCallback(packageChanges, Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(this.context, profileChanges, IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            if (Build.VERSION.SDK_INT >= 31) {
                addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
                addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                addAction(Intent.ACTION_PROFILE_ADDED)
                addAction(Intent.ACTION_PROFILE_REMOVED)
            }
            if (Build.VERSION.SDK_INT >= 35) {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Package changes invalidate only that installation's artwork, including the personal profile. */
    fun invalidateApps(user: UserHandle, packages: Collection<String>) {
        if (user == Process.myUserHandle()) iconPacks.invalidate(packages)
        synchronized(invalidations) {
            val stamp = _revision.value + 1
            packages.forEach { name ->
                val key = user to name
                packageRevisions[key] = stamp
                pendingPackages[key] = stamp
            }
            _revision.value = stamp
        }
    }

    fun invalidateProfiles() = synchronized(invalidations) {
        profilesGeneration++
        _revision.value++
    }

    /** Explicit recovery only; changes to presentation do not invalidate the installed-app model. */
    fun invalidateAll() = synchronized(invalidations) {
        allGeneration++
        profilesGeneration++
        _revision.value++
    }

    suspend fun loadApps(iconPackPackage: String?): List<LauncherApp> = loadApps(listOfNotNull(iconPackPackage))

    /** Cheap startup validation; no labels, artwork, icon packs or foreign resources are loaded. */
    internal suspend fun launchableComponents(): Set<ComponentName> = withContext(Dispatchers.IO) {
        launcherApps.getActivityList(null, Process.myUserHandle()).map { it.componentName }.toSet()
    }

    suspend fun profileSnapshot(force: Boolean = false): LauncherProfileSnapshot = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            // A failed package/profile does not keep the entire catalog dirty. Announce its
            // retry once, on a later resume, so successfully loaded users remain reusable.
            val retries = models.values.flatMap { listOfNotNull(it.fullRetry) + it.packageRetries.values }
            if (retries.any { !it.announced && now >= it.at }) {
                retries.filter { now >= it.at }.forEach { it.announced = true }
                synchronized(invalidations) { _revision.value++ }
            }
            val generation = synchronized(invalidations) { profilesGeneration }
            if (!force && generation == observedProfilesGeneration && now - snapshotAt < PROFILE_CHECK_INTERVAL) {
                return@withLock lastSnapshot
            }
            val discovered = profiles.snapshot()
            val firstDiscovery = lastSnapshot.profiles.isEmpty()
            val previous = lastSnapshot.profiles.associateBy { it.user }
            val discoveredUsers = discovered.profiles.map { it.user }.toSet()
            val candidates = discovered.profiles.map { profile ->
                val old = previous[profile.user]?.takeIf { it.serial == profile.serial }
                // A failed state query cannot accidentally unlock a previously locked profile.
                profile.copy(quietMode = profile.quietMode ?: old?.quietMode,
                    unlocked = profile.unlocked ?: old?.unlocked)
            } + if (discovered.discoveryComplete) emptyList() else previous.values.filter {
                it.user !in discoveredUsers && it.isPublic
            }
            val visible = synchronized(invalidations) {
                candidates.filter { profile ->
                    val removed = removedUsers[profile.user]
                    // Some OEMs omit the added broadcast when recreating a clone with the same
                    // user ID. A new serial identifies a different installation unambiguously.
                    if (removed != null && profile.serial >= 0 && discovered.discoveryComplete &&
                        (removed.serial != null && removed.serial != profile.serial ||
                            removed.serial == null && now - removed.at >= PROFILE_CHECK_INTERVAL)) {
                        removedUsers.remove(profile.user)
                        blockedUsers.remove(profile.user)
                    }
                    profile.user !in removedUsers
                }.map { profile ->
                    if (profile.serial >= 0) knownSerials[profile.user] = profile.serial
                    val blockedAt = blockedUsers[profile.user]
                    if (blockedAt != null && now - blockedAt >= PROFILE_CHECK_INTERVAL &&
                        profile.quietMode == false && profile.unlocked == true && profile.isPublic) {
                        blockedUsers.remove(profile.user)
                    }
                    if (profile.user in blockedUsers) profile.copy(type = LauncherProfileType.Unavailable) else profile
                }
            }.toMutableList()
            var complete = discovered.complete
            var inventoryChanged = false
            val components = mutableMapOf<UserHandle, Set<ComponentName>>()
            for ((index, profile) in visible.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (profile.user == Process.myUserHandle() || !readable(profile)) continue
                // On a cold start, let loadCatalog publish personal rows before querying any
                // foreign inventory. Later resumes use this throttled OEM reconciliation.
                if (firstDiscovery) continue
                val stamp = synchronized(invalidations) { _revision.value }
                try {
                    // OEMs sometimes omit package callbacks for clones. Keep this fallback off
                    // the normal resume path and retain the query for the subsequent model load.
                    val activities = launcherApps.getActivityList(null, profile.user)
                    val inventory = activities.map(::fingerprint).sortedBy { it.component.flattenToString() }
                    inventoryChanged = inventoryChanged || inventories[profile.user] != inventory
                    inventories[profile.user] = inventory
                    inventoryQueries[profile.user] = ActivityQuery(activities, stamp)
                    components[profile.user] = activities.map { it.componentName }.toSet()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("AppRepository", "Unable to check launcher activities for ${profile.user}", error)
                    complete = false
                    inventoryQueries[profile.user] = ActivityQuery(null, stamp, error)
                    if (error is SecurityException) {
                        // Access denial is not a valid reason to keep exposing an old profile.
                        visible[index] = profile.copy(type = LauncherProfileType.Unavailable)
                    } else lastSnapshot.launcherComponents[profile.user]?.let { components[profile.user] = it }
                }
            }
            val snapshot = discovered.copy(profiles = visible, complete = complete, launcherComponents = components)
            val allowed = visible.filter(::readable).map { it.user }.toSet()
            models.keys.retainAll(allowed)
            inventoryQueries.keys.retainAll(allowed)
            inventories.keys.retainAll(allowed)
            if (snapshot != lastSnapshot || inventoryChanged || !complete) {
                synchronized(invalidations) { _revision.value++ }
            }
            lastSnapshot = snapshot
            snapshotAt = now
            observedProfilesGeneration = generation
            snapshot
        }
    }

    suspend fun loadApps(iconPackPackages: List<String> = emptyList()): List<LauncherApp> =
        loadCatalog(profileSnapshot(), iconPackPackages).apps

    /** Like Lawnchair, retain the process model and update only changed installations. */
    suspend fun loadCatalog(snapshot: LauncherProfileSnapshot, iconPackPackages: List<String>,
        onPersonalApps: (suspend (List<LauncherApp>) -> Unit)? = null): AppCatalog = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            val apps = mutableListOf<LauncherApp>()
            var complete = snapshot.complete
            val visible = snapshot.profiles.filter(::readable).filter { profile ->
                synchronized(invalidations) { profile.user !in blockedUsers && profile.user !in removedUsers }
            }.sortedBy { it.user != Process.myUserHandle() }
            models.keys.retainAll(visible.map { it.user }.toSet())
            for (profile in visible) {
                currentCoroutineContext().ensureActive()
                val model = models[profile.user]?.takeIf { it.serial == profile.serial && it.type == profile.type }
                    ?: ProfileModel(profile.serial, profile.type).also { models[profile.user] = it }
                complete = refreshProfile(profile, model) && complete
                val profileApps = model.apps.values.sortedWith(LauncherAppOrder)
                apps += profileApps
                if (profile.user == Process.myUserHandle() && onPersonalApps != null) {
                    onPersonalApps(applyIconPacks(profileApps, iconPackPackages))
                }
            }
            AppCatalog(applyIconPacks(apps.distinctBy(LauncherApp::key).sortedWith(LauncherAppOrder), iconPackPackages),
                snapshot.profiles.filter { it.type == LauncherProfileType.Work }.map { WorkProfile(it.user, it.serial) },
                complete)
        }
    }

    private fun readable(profile: LauncherProfile): Boolean = profile.isPublic &&
        (profile.user == Process.myUserHandle() || (profile.quietMode != true && profile.unlocked != false))

    private fun fingerprint(activity: LauncherActivityInfo): ActivityFingerprint {
        val info = activity.applicationInfo
        return ActivityFingerprint(activity.componentName, info.sourceDir, info.splitSourceDirs.orEmpty().toList(), info.flags)
    }

    /** modelMutex is held; package callbacks only touch the short, separate invalidation lock. */
    private suspend fun refreshProfile(profile: LauncherProfile, model: ProfileModel): Boolean {
        val now = SystemClock.elapsedRealtime()
        val generation = synchronized(invalidations) { allGeneration }
        val pending = synchronized(invalidations) {
            pendingPackages.filterKeys { it.first == profile.user }.mapKeys { it.key.second }
        }
        val configuration = IconResourceConfiguration.read(context)
        val needsFull = !model.initialized || model.generation != generation || model.fullRetry != null
        val staged = inventoryQueries.remove(profile.user)
        val retry = model.fullRetry
        if (staged == null && needsFull && retry != null && retry.token == generation && now < retry.at &&
            pending.values.none { it > model.fullFailureRevision }) {
            // Keep a disk preview visible when the very first personal inventory failed.
            if (profile.user == Process.myUserHandle() && !model.initialized) {
                throw model.fullError ?: IllegalStateException("Personal app inventory is temporarily unavailable")
            }
            return false
        }
        val query = if (staged != null) staged else if (needsFull) {
            val stamp = synchronized(invalidations) { _revision.value }
            try { ActivityQuery(launcherApps.getActivityList(null, profile.user), stamp) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { ActivityQuery(null, stamp, error) }
        } else null
        if (query?.error != null) {
            model.fullRetry = Retry(generation, now + PROFILE_CHECK_INTERVAL)
            model.fullError = query.error
            model.fullFailureRevision = query.stamp
            if (query.error is SecurityException && profile.user != Process.myUserHandle()) {
                model.apps.clear()
                model.activities.clear()
                model.initialized = false
            }
            if (profile.user == Process.myUserHandle() && !model.initialized) throw query.error
            Log.w("AppRepository", "Unable to refresh launcher apps for ${profile.user}", query.error)
            return false
        }
        val rebuild = linkedSetOf<String>()
        if (query?.activities != null) {
            val incoming = query.activities.associateBy { it.componentName }
            if (needsFull) rebuild += (model.activities.keys + incoming.keys).map { it.packageName }
            else {
                (model.activities.keys + incoming.keys).forEach { component ->
                    val before = model.activities[component]?.let(::fingerprint)
                    val after = incoming[component]?.let(::fingerprint)
                    if (before != after) rebuild += component.packageName
                }
            }
            model.activities.clear()
            model.activities.putAll(incoming)
            model.initialized = true
            model.generation = generation
            model.fullRetry = null
            model.fullError = null
        }
        if (model.configuration != configuration) rebuild += model.activities.keys.map { it.packageName }
        val requested = linkedSetOf<String>().apply {
            addAll(rebuild)
            addAll(pending.keys)
            addAll(model.packageRetries.keys)
        }
        for (packageName in requested) {
            currentCoroutineContext().ensureActive()
            val token = pending[packageName] ?: model.packageRetries[packageName]?.token ?: synchronized(invalidations) { _revision.value }
            val failed = model.packageRetries[packageName]
            if (packageName !in rebuild && failed != null && failed.token == token && now < failed.at) continue
            try {
                // A callback arriving after the full query needs its own package query. Older
                // callbacks are already represented by the inventory we are about to consume.
                val queryPackage = packageName in pending && (query == null || pending.getValue(packageName) > query.stamp) ||
                    (failed != null && packageName !in rebuild && query == null)
                val sourceRevision = synchronized(invalidations) { packageRevisions[profile.user to packageName] ?: 0L }
                val activities = if (queryPackage) launcherApps.getActivityList(packageName, profile.user)
                    else model.activities.values.filter { it.componentName.packageName == packageName }
                val replacement = buildApps(profile, activities, generation, mapOf(packageName to sourceRevision))
                val missingArtwork = replacement.any { it.icon == null }
                val usable = replacement.map { app ->
                    if (app.icon == null) model.apps[app.componentName]?.takeIf { it.icon != null } ?: app else app
                }
                model.activities.keys.removeAll { it.packageName == packageName }
                activities.forEach { model.activities[it.componentName] = it }
                model.apps.keys.removeAll { it.packageName == packageName }
                usable.forEach { model.apps[it.componentName] = it }
                if (missingArtwork) model.packageRetries[packageName] = Retry(token, now + PROFILE_CHECK_INTERVAL)
                else model.packageRetries.remove(packageName)
                pending[packageName]?.let { consumed -> synchronized(invalidations) {
                    val key = profile.user to packageName
                    if (pendingPackages[key] == consumed) pendingPackages.remove(key)
                } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("AppRepository", "Unable to refresh $packageName for ${profile.user}", error)
                model.packageRetries[packageName] = Retry(token, now + PROFILE_CHECK_INTERVAL)
                if (error is SecurityException && profile.user != Process.myUserHandle()) {
                    model.apps.clear()
                    model.activities.clear()
                    model.initialized = false
                    model.fullRetry = Retry(generation, now + PROFILE_CHECK_INTERVAL)
                    model.fullError = error
                    model.fullFailureRevision = synchronized(invalidations) { _revision.value }
                    return false
                }
            }
        }
        model.configuration = configuration
        if (profile.user != Process.myUserHandle() && model.initialized) {
            inventories[profile.user] = model.activities.values.map(::fingerprint).sortedBy { it.component.flattenToString() }
        }
        return model.fullRetry == null && model.packageRetries.isEmpty()
    }

    suspend fun loadPrivateApps(user: UserHandle, serial: Long, iconPackPackages: List<String>): List<LauncherApp> =
        loadProfileApps(LauncherProfile(user, serial, LauncherProfileType.Private), iconPackPackages)

    private suspend fun loadProfileApps(profile: LauncherProfile, iconPackPackages: List<String>): List<LauncherApp> = withContext(Dispatchers.IO) {
        applyIconPacks(buildApps(profile, launcherApps.getActivityList(null, profile.user),
            synchronized(invalidations) { allGeneration }), iconPackPackages)
    }

    private suspend fun buildApps(profile: LauncherProfile, activities: List<LauncherActivityInfo>, generation: Long,
        sourceRevisions: Map<String, Long>? = null): List<LauncherApp> {
        val coroutine = currentCoroutineContext()
        val configuration = IconResourceConfiguration.read(context)
        val personal = profile.user == Process.myUserHandle()
        // Private icons belong only to the unlocked UI session, never the process-wide model.
        val sources = if (profile.type == LauncherProfileType.Private) null else if (personal) personalSources else profileSources
        val englishLabels = EnglishLabelReader(context)
        val packages = mutableMapOf<String, PackageInfo?>()
        return activities.asSequence()
            .mapNotNull { activity ->
                coroutine.ensureActive()
                val component = activity.componentName
                // Expose only our settings entry, never the launcher/HOME activity.
                if (component.packageName == context.packageName &&
                    component.className != SettingsActivity::class.java.name) return@mapNotNull null
                val label = runCatching { activity.label.toString().trim() }.getOrDefault("")
                    .ifBlank { component.shortClassName }
                val packageInfo = if (personal) packages.getOrPut(component.packageName) {
                    @Suppress("DEPRECATION")
                    runCatching { packageManager.getPackageInfo(component.packageName, 0) }.getOrNull()
                } else null
                val info = activity.applicationInfo
                val revision = sourceRevisions?.get(component.packageName)
                    ?: synchronized(invalidations) { packageRevisions[profile.user to component.packageName] ?: 0L }
                val installedAt = runCatching { activity.firstInstallTime }.getOrDefault(packageInfo?.firstInstallTime ?: 0L)
                val activityInfo = if (Build.VERSION.SDK_INT >= 31) runCatching { activity.activityInfo }.getOrNull() else if (personal) {
                    @Suppress("DEPRECATION")
                    runCatching { packageManager.getActivityInfo(component, 0) }.getOrNull()
                } else null
                val signature = AppSourceSignature(configuration, label, installedAt,
                    packageInfo?.lastUpdateTime ?: 0, packageInfo?.longVersionCode ?: 0,
                    listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty(), info.flags,
                    activityInfo?.iconResource ?: info.icon, revision, profile.type, generation)
                val key = if (personal) component.flattenToString() else "profile:${profile.serial}:${component.flattenToString()}"
                sources?.get(key, signature) ?: run {
                    val drawable = runCatching { activity.getIcon(0) }.getOrNull()
                    val source = if (personal) null else runCatching { drawable?.let(ProfileIconSource::fromDrawable) }.getOrNull()
                    makeApp(component, label, drawable, info.flags).let { app -> app.copy(
                        // Preserve existing personal favorites, renames and icon overrides.
                        user = profile.user.takeUnless { personal }, userSerial = profile.serial.takeUnless { personal },
                        isPrivateSpace = profile.type == LauncherProfileType.Private,
                        isWorkProfile = profile.type == LauncherProfileType.Work,
                        isCloneProfile = profile.type == LauncherProfileType.Clone,
                        firstInstallTime = installedAt, isLauncherSettings = component.packageName == context.packageName,
                        englishLabel = activityInfo?.let(englishLabels::label), iconSourceRevision = signature,
                        profileIconSource = source, icon = source?.layers()?.original?.asImageBitmap() ?: app.icon)
                    }.also { app ->
                        coroutine.ensureActive()
                        if (app.icon != null) sources?.put(key, signature, app)
                    }
                }
            }
            .distinctBy(LauncherApp::key)
            .sortedWith(LauncherAppOrder)
            .toList()
    }

    suspend fun applyIconPacks(apps: List<LauncherApp>, iconPackPackages: List<String>): List<LauncherApp> = withContext(Dispatchers.IO) {
        val packs = normalizeIconPackOrder(iconPackPackages).mapNotNull { iconPacks.load(it) }
        if (packs.isEmpty()) return@withContext apps
        apps.map { app ->
            val signature = PackSignature(app.iconSourceRevision ?: app.profileIconSource?.cacheKey ?: app.icon,
                packs.map { it.cacheKey }, if (packs.any { it.hasCalendarFor(app.componentName) }) LocalDate.now().dayOfMonth else null)
            val (packageName, icon) = (if (app.isPrivateSpace) null else packedIcons.get(app.key, signature)) ?: run {
                val match = firstMatchingPackIcon(packs) { it.iconFor(app.componentName, null) } ?: return@map app
                (match.first.packageName to match.second).also { if (!app.isPrivateSpace) packedIcons.put(app.key, signature, it) }
            }
            app.copy(icon = icon.bitmap, monochromeIcon = icon.monochrome, monochromeScale = icon.monochromeScale,
                iconPackPackage = packageName, themeIconPackPackage = packageName)
        }
    }

    private fun makeApp(component: ComponentName, label: String, drawable: Drawable?, flags: Int): LauncherApp {
        // Sample the provider's original drawable on IO, before considering packs.
        // Only the color survives; previews/drags never reload or resample icons.
        val originalColor = runCatching { drawable?.let {
            val sample = renderIcon(it, 48)
            try { IconDominantColor.of(sample) } finally { sample.recycle() }
        } }.getOrNull()
        val icon = runCatching { drawable?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
        val monochrome = if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                (drawable as? AdaptiveIconDrawable)?.monochrome?.toBitmap(144, 144)?.let { bitmap ->
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    bitmap.takeIf { pixels.count { pixel -> pixel ushr 24 > 32 } >= pixels.size / 100 }?.asImageBitmap()
                }
            }.getOrNull()
        } else null
        return LauncherApp(componentName = component, label = label, icon = icon, monochromeIcon = monochrome,
            isSystemApp = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            isAdaptiveIcon = drawable is AdaptiveIconDrawable, originalIconColor = originalColor)
    }

    fun launch(
        app: LauncherApp,
        sourceBounds: Rect? = null,
        options: Bundle? = null,
    ): Boolean = runCatching {
        val user = app.user ?: Process.myUserHandle()
        if (user == Process.myUserHandle()) {
            // Match Launcher3/Lawnchair: current-user entry activities may perform
            // OEM authentication or forwarding before opening the actual app.
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.componentName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .also { it.sourceBounds = sourceBounds }
            context.startActivity(intent, options)
        } else {
            // Never fall back to the personal installation of a cross-profile app.
            context.getSystemService(LauncherApps::class.java).startMainActivity(
                app.componentName, user, sourceBounds, options,
            )
        }
    }.onFailure { error ->
        Log.e("AppRepository", "Unable to launch ${app.componentName} for ${app.user ?: Process.myUserHandle()}", error)
    }.isSuccess

    companion object {
        private const val PROFILE_CHECK_INTERVAL = 30_000L
        @Volatile private var instance: AppRepository? = null

        fun get(context: Context, iconPacks: IconPackRepository = IconPackRepository.get(context)): AppRepository =
            instance ?: synchronized(this) {
                instance ?: AppRepository(context.applicationContext, iconPacks).also { instance = it }
            }
    }
}
