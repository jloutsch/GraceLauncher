package com.galaxyrio.gracelauncher.data.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.util.LruCache
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.galaxyrio.gracelauncher.data.EnglishLabelReader
import com.galaxyrio.gracelauncher.data.LauncherLabelOrder
import java.time.LocalDate
import com.galaxyrio.gracelauncher.data.ItemIcon
import org.xmlpull.v1.XmlPullParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class IconPackInfo(val packageName: String, val label: String, val icon: ImageBitmap?, val englishLabel: String? = null)
enum class IconPackStatus { System, Ready, Unavailable }

class IconPackRepository(context: Context) {
    private val context = context.applicationContext
    private val pm = this.context.packageManager
    private val stateLock = Any()
    private val cacheMutex = Mutex()
    private val discoveryMutex = Mutex()
    private data class PackEntry(val pack: LoadedIconPack?, val expiresAt: Long)
    private data class DiscoveryEntry(val packs: List<IconPackInfo>, val expiresAt: Long)
    private var configuration = IconResourceConfiguration.read(this.context)
    private var revision = 0L
    private var discovered: DiscoveryEntry? = null
    private var lastDiscovered = emptyList<IconPackInfo>()
    private val cachedPacks = object : LinkedHashMap<String, PackEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PackEntry>?): Boolean = size > 12
    }
    private val packageChanges = object : LauncherApps.Callback() {
        private fun changed(packages: Collection<String>, user: UserHandle) {
            if (user == Process.myUserHandle()) invalidate(packages)
        }
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(listOf(packageName), user)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(listOf(packageName), user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(listOf(packageName), user)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            changed(packageNames.toList(), user)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            changed(packageNames.toList(), user)
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = changed(packageNames.toList(), user)
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = changed(packageNames.toList(), user)
    }
    // This repository and its callback live for the application process. The callback
    // only changes small cache metadata; resource/XML work always stays on IO.
    private val observesPackages = runCatching {
        this.context.getSystemService(LauncherApps::class.java)
            .registerCallback(packageChanges, Handler(Looper.getMainLooper()))
        true
    }.getOrDefault(false)

    fun invalidate(packageNames: Collection<String>? = null) = synchronized(stateLock) {
        revision++
        discovered = null
        if (packageNames == null) cachedPacks.clear() else {
            packageNames.forEach(cachedPacks::remove)
            lastDiscovered = lastDiscovered.filterNot { it.packageName in packageNames }
        }
    }

    /** Includes explicit per-item packs, which need not appear in the enabled-pack list. */
    fun consumeDueRetries(): Boolean = synchronized(stateLock) {
        refreshConfiguration()
        val now = SystemClock.elapsedRealtime()
        val expired = cachedPacks.filterValues { it.pack == null && now >= it.expiresAt }.keys.toList()
        if (expired.isEmpty()) return@synchronized false
        expired.forEach(cachedPacks::remove)
        revision++
        true
    }

    /** Rendered catalog snapshots must not bypass a pack update or a due failure retry. */
    fun cacheRevision(): Long = synchronized(stateLock) {
        refreshConfiguration()
        revision
    }

    /** Called under stateLock; ordinary Activity recreation must retain resource caches. */
    private fun refreshConfiguration() {
        val current = IconResourceConfiguration.read(context)
        if (configuration != current) {
            configuration = current
            revision++
            discovered = null
            cachedPacks.clear()
        }
    }

    suspend fun iconNames(packageName: String): List<String> = withContext(Dispatchers.IO) {
        val pack = load(packageName) ?: return@withContext emptyList()
        val names = linkedSetOf<String>()
        // drawable.xml contains alternates which do not have an appfilter mapping.
        val resources = safely { pm.getResourcesForApplication(packageName) } ?: return@withContext emptyList()
        fun read(parser: XmlPullParser) {
            var nodes = 0
            while (parser.eventType != XmlPullParser.END_DOCUMENT && ++nodes < 200_000) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() }?.let(names::add)
                }
                parser.next()
            }
        }
        @SuppressLint("DiscouragedApi") val xml = resources.getIdentifier("drawable", "xml", packageName)
        if (xml != 0) safely { resources.getXml(xml).use(::read) }
        if (names.isEmpty()) safely {
            resources.assets.open("drawable.xml").use { stream -> read(Xml.newPullParser().apply { setInput(stream, null) }) }
        }
        names.addAll(pack.definition.icons.values)
        pack.definition.calendars.values.forEach { prefix -> (1..31).forEach { names.add("$prefix$it") } }
        names.toList()
    }

    internal suspend fun selectedIcon(choice: ItemIcon): PackIcon? = withContext(Dispatchers.IO) {
        if (choice.kind != "pack") null else load(choice.source)?.namedIcon(choice.name)
    }

    internal suspend fun matchingIconName(packageName: String, component: ComponentName): String? = withContext(Dispatchers.IO) {
        val pack = load(packageName) ?: return@withContext null
        pack.definition.candidates(component.flattenToString(), LocalDate.now().dayOfMonth)
            .firstOrNull { pack.namedIcon(it) != null }
    }

    suspend fun installedPacks(): List<IconPackInfo> = withContext(Dispatchers.IO) {
        discoveryMutex.withLock { discoverPacks() }
    }

    private suspend fun discoverPacks(): List<IconPackInfo> {
        while (true) {
            currentCoroutineContext().ensureActive()
            val request = synchronized(stateLock) {
                refreshConfiguration()
                discovered?.takeIf { SystemClock.elapsedRealtime() < it.expiresAt }
                    ?.let { return it.packs }
                revision
            }
            val packages = linkedSetOf<String>()
            var complete = true
            discoveryIntents().forEach { intent ->
                currentCoroutineContext().ensureActive()
                @Suppress("DEPRECATION")
                val matches = safely { pm.queryIntentActivities(intent, 0) }
                if (matches == null) complete = false
                matches.orEmpty().forEach { result ->
                    result.activityInfo?.applicationInfo?.takeIf { it.enabled }?.packageName?.let(packages::add)
                }
            }
            val englishLabels = EnglishLabelReader(context)
            val result = packages.mapNotNull { pkg ->
                safely {
                    val app = pm.getApplicationInfo(pkg, 0)
                    val icon = safely { renderIcon(pm.getApplicationIcon(app)).asImageBitmap() }
                    if (icon == null) complete = false
                    IconPackInfo(pkg, pm.getApplicationLabel(app).toString(),
                        icon, englishLabel = englishLabels.label(app))
                }.also { if (it == null) complete = false }
            }.sortedWith(compareBy(LauncherLabelOrder) { it.label })
            synchronized(stateLock) {
                refreshConfiguration()
                if (revision == request) {
                    // A temporarily unavailable package provider must not become a
                    // permanent empty result for this process.
                    val expires = if (complete && result.isNotEmpty() && observesPackages) Long.MAX_VALUE
                        else SystemClock.elapsedRealtime() + RetryDelayMillis
                    val visible = if (complete) result else (result + lastDiscovered)
                        .distinctBy { it.packageName }.sortedWith(compareBy(LauncherLabelOrder) { it.label })
                    if (complete) lastDiscovered = result
                    discovered = DiscoveryEntry(visible, expires)
                    return visible
                }
            }
        }
    }

    internal suspend fun load(packageName: String?): LoadedIconPack? = withContext(Dispatchers.IO) {
        if (packageName == null) return@withContext null
        cacheMutex.withLock { loadPack(packageName) }
    }

    private suspend fun loadPack(packageName: String): LoadedIconPack? {
        while (true) {
            currentCoroutineContext().ensureActive()
            val request = synchronized(stateLock) {
                refreshConfiguration()
                // Check memory before touching PackageManager. A catalog may ask
                // for the same enabled pack once per app and designer layer.
                cachedPacks[packageName]?.takeIf { SystemClock.elapsedRealtime() < it.expiresAt }
                    ?.let { return it.pack }
                revision
            }
            val loaded = safely {
                val resources = pm.getResourcesForApplication(packageName)
                val coroutine = currentCoroutineContext()
                val definition = readDefinition(resources, packageName) { coroutine.ensureActive() }
                    ?: return@safely null
                val themed = pm.queryIntentActivities(Intent(ThemedIconAction).setPackage(packageName), 0).isNotEmpty()
                LoadedIconPack(packageName, resources, definition, themed)
            }
            synchronized(stateLock) {
                refreshConfiguration()
                if (revision == request) {
                    val expires = if (loaded != null && observesPackages) Long.MAX_VALUE
                        else SystemClock.elapsedRealtime() + RetryDelayMillis
                    cachedPacks[packageName] = PackEntry(loaded, expires)
                    return loaded
                }
            }
        }
    }

    @SuppressLint("DiscouragedApi") // Resource names belong to an installed pack, not our generated R class.
    private fun readDefinition(resources: Resources, pkg: String, checkCancelled: () -> Unit): IconPackDefinition? {
        // Android's resource manager includes installed density/language splits.
        // Try alternate locations if a pack ships an invalid/stale compiled XML.
        val xmlId = resources.getIdentifier("appfilter", "xml", pkg)
        if (xmlId != 0) safely { resources.getXml(xmlId).use { parseIconPack(it, checkCancelled) } }?.let { return it }
        val rawId = resources.getIdentifier("appfilter", "raw", pkg)
        if (rawId != 0) safely {
            resources.openRawResource(rawId).use { stream ->
                parseIconPack(Xml.newPullParser().apply { setInput(stream, null) }, checkCancelled)
            }
        }?.let { return it }
        return safely {
            resources.assets.open("appfilter.xml").use { stream ->
                parseIconPack(Xml.newPullParser().apply { setInput(stream, null) }, checkCancelled)
            }
        }
    }

    private fun discoveryIntents() = listOf(
        "org.adw.launcher.THEMES", "org.adw.ActivityStarter.THEMES", "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme", "ch.deletescape.lawnchair.ICONPACK",
        "com.sonymobile.home.ICON_PACK", ThemedIconAction,
    ).map(::Intent) + listOf(
        "com.teslacoilsw.launcher.THEME", "com.anddoes.launcher.THEME", "com.fede.launcher.THEME_ICONPACK",
    ).map { Intent(Intent.ACTION_MAIN).addCategory(it) }

    companion object {
        private const val ThemedIconAction = "app.lawnchair.icons.THEMED_ICON"
        private const val RetryDelayMillis = 15_000L
        @Volatile private var instance: IconPackRepository? = null
        fun get(context: Context): IconPackRepository = instance ?: synchronized(this) {
            instance ?: IconPackRepository(context.applicationContext).also { instance = it }
        }
    }
}

/** Resource choices relevant to icon artwork, without window/orientation churn. */
internal data class IconResourceConfiguration(val density: Int, val nightMode: Int, val locales: String) {
    companion object {
        fun read(context: Context): IconResourceConfiguration = context.resources.configuration.let {
            IconResourceConfiguration(it.densityDpi, it.uiMode and Configuration.UI_MODE_NIGHT_MASK,
                it.locales.toLanguageTags())
        }
    }
}

internal data class PackIcon(val bitmap: ImageBitmap, val monochrome: ImageBitmap? = null, val monochromeScale: Float = 1.4f)

/** Parsed once per package version. Drawable bitmaps are bounded and reused across refreshes. */
internal class LoadedIconPack(
    val packageName: String,
    private val resources: Resources,
    internal val definition: IconPackDefinition,
    private val themed: Boolean,
) {
    internal val cacheKey = Any()
    private val bitmaps = LruCache<String, Bitmap>(96)

    fun hasCalendarFor(component: ComponentName): Boolean = definition.hasCalendarFor(component.flattenToString())

    @Synchronized
    fun namedIcon(name: String): PackIcon? {
        val drawable = drawable(name) ?: return null
        val bitmap = bitmap(name) ?: return null
        val mono = if (drawable is AdaptiveIconDrawable && Build.VERSION.SDK_INT >= 33) drawable.monochrome else null
        val themedLayer = mono ?: if (themed) (drawable as? AdaptiveIconDrawable)?.foreground ?: drawable else null
        return PackIcon(bitmap.asImageBitmap(), safely { themedLayer?.let(::renderIcon)?.asImageBitmap() },
            if (drawable is AdaptiveIconDrawable) 1.4f else 0.76f)
    }

    @Synchronized
    fun designDrawable(name: String): Drawable? = drawable(name)

    @Synchronized
    fun designDrawableFor(component: ComponentName): Drawable? =
        definition.candidates(component.flattenToString(), LocalDate.now().dayOfMonth).firstNotNullOfOrNull(::drawable)

    @Synchronized
    fun designLayers(name: String, size: Int): IconLayers? = drawable(name)?.let { iconLayers(it, size, themed) }

    @Synchronized
    fun designLayersFor(component: ComponentName, size: Int): IconLayers? =
        designDrawableFor(component)?.let { iconLayers(it, size, themed) }

    @Synchronized
    fun iconFor(component: ComponentName, original: Drawable?, day: Int = LocalDate.now().dayOfMonth): PackIcon? {
        definition.candidates(component.flattenToString(), day).forEach { name ->
            val drawable = drawable(name) ?: return@forEach
            val bitmap = bitmap(name) ?: return@forEach
            val mono = if (drawable is AdaptiveIconDrawable && Build.VERSION.SDK_INT >= 33) drawable.monochrome else null
            val themedLayer = mono ?: if (themed) (drawable as? AdaptiveIconDrawable)?.foreground ?: drawable else null
            return PackIcon(bitmap.asImageBitmap(), safely { themedLayer?.let(::renderIcon)?.asImageBitmap() },
                if (drawable is AdaptiveIconDrawable) 1.4f else 0.76f)
        }
        if (original == null) return null
        val key = component.flattenToString()
        fun choose(names: List<String>): Bitmap? = names.takeIf { it.isNotEmpty() }
            ?.let { bitmap(it[Math.floorMod(key.hashCode(), it.size)]) }
        val back = choose(definition.backgrounds)
        val mask = choose(definition.masks)
        val upon = choose(definition.overlays)
        if (back == null && mask == null && upon == null) return null
        // ADW masks erase the opaque part of the mask from the scaled original,
        // not the background. Stable selection prevents colors changing on reload.
        return safely {
            PackIcon(composeFallbackIcon(renderIcon(original), back, mask, upon, definition.scale).asImageBitmap())
        }
    }

    @SuppressLint("DiscouragedApi") // Names come from the external pack's appfilter.xml.
    private fun drawable(name: String): Drawable? = safely {
        val clean = name.removePrefix("@drawable/").removePrefix("@mipmap/")
        if (!clean.matches(Regex("[a-zA-Z0-9_]+"))) return@safely null
        val id = resources.getIdentifier(clean, "drawable", packageName).takeIf { it != 0 }
            ?: resources.getIdentifier(clean, "mipmap", packageName)
        if (id == 0) null else resources.getDrawable(id, null).mutate()
    }

    private fun bitmap(name: String): Bitmap? = bitmaps[name] ?: safely {
        drawable(name)?.let(::renderIcon)?.also { bitmaps.put(name, it) }
    }
}

internal fun renderIcon(drawable: Drawable, size: Int = 144): Bitmap {
    val output = createBitmap(size, size)
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
    val scale = size.toFloat() / maxOf(width, height)
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    val bounds = Rect(drawable.bounds)
    try {
        drawable.setBounds((size - targetWidth) / 2, (size - targetHeight) / 2,
            (size + targetWidth) / 2, (size + targetHeight) / 2)
        drawable.draw(Canvas(output))
    } finally { drawable.bounds = bounds }
    return output
}

internal fun composeFallbackIcon(original: Bitmap, back: Bitmap?, mask: Bitmap?, upon: Bitmap?, scale: Float): Bitmap {
    val size = original.width
    val output = createBitmap(size, size)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val full = Rect(0, 0, size, size)
    back?.let { canvas.drawBitmap(it, null, full, paint) }
    val layer = canvas.saveLayer(0f, 0f, size.toFloat(), size.toFloat(), null)
    val inset = (1f - scale) * size / 2f
    canvas.drawBitmap(original, null, RectF(inset, inset, size - inset, size - inset), paint)
    if (mask != null) {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        canvas.drawBitmap(mask, null, full, paint)
        paint.xfermode = null
    }
    canvas.restoreToCount(layer)
    upon?.let { canvas.drawBitmap(it, null, full, paint) }
    return output
}

private inline fun <T> safely(block: () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { null }
