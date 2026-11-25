package com.galaxyrio.gracelauncher.platform

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.RemoteViews
import java.lang.ref.WeakReference
import kotlin.math.abs

/** Stable across process restarts. Setup activities allocate IDs but only HOME listens. */
class HomeWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
    var onProvidersUpdated: (() -> Unit)? = null
    private val copies = mutableMapOf<Int, MutableList<WeakReference<HomeWidgetHostView>>>()
    private val registeredCopies = mutableMapOf<Int, WeakReference<HomeWidgetHostView>>()
    private var rebindingView: HomeWidgetHostView? = null

    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView {
        // createView is the public API that registers the host's callback target.
        // Returning a live copy lets us restore it without accessing hidden setListener APIs.
        rebindingView?.takeIf { it.appWidgetId == appWidgetId }?.let { return it }
        val view = HomeWidgetHostView(context)
        val views = copies.getOrPut(appWidgetId) { mutableListOf() }
        views.removeAll { it.get() == null }
        views += WeakReference(view)
        registeredCopies[appWidgetId] = WeakReference(view)
        // Android registers just the most recently created view for each widget ID.
        // A reusable stack may also be visible in HOME, a pop-up and its editor.
        // Mirror content updates to its other live copies without registering a new host.
        view.onContentUpdated = { source, remoteViews ->
            views.removeAll { it.get() == null }
            views.forEach { reference ->
                reference.get()?.takeIf { it !== source && it.isAttachedToWindow }
                    ?.applyMirroredContent(remoteViews)
            }
        }
        view.onAttached = { attached ->
            val registered = registeredCopies[appWidgetId]?.get()
            if (registered !== attached && registered?.isAttachedToWindow != true) rebindView(attached)
        }
        return view
    }

    internal fun releaseView(view: HomeWidgetHostView) {
        val id = view.appWidgetId
        val views = copies[id]
        views?.removeAll { it.get() == null || it.get() === view }
        view.onContentUpdated = null
        view.onAttached = null
        if (registeredCopies[id]?.get() === view) {
            registeredCopies.remove(id)
            // Collection data-change callbacks only go to the registered copy. In
            // particular, leaving an editor must not leave its detached preview registered.
            views?.asReversed()?.firstNotNullOfOrNull { it.get()?.takeIf { copy -> copy.isAttachedToWindow } }
                ?.let(::rebindView)
        }
        if (views.isNullOrEmpty()) copies.remove(id)
    }

    /** Give an active placement the collection callbacks instead of a retained off-screen copy. */
    internal fun promoteView(view: HomeWidgetHostView) {
        val views = copies[view.appWidgetId] ?: return
        views.removeAll { it.get() == null }
        if (views.size > 1 && registeredCopies[view.appWidgetId]?.get() !== view) rebindView(view)
    }

    private fun rebindView(view: HomeWidgetHostView) {
        if (rebindingView != null) return
        val info = view.appWidgetInfo ?: return
        val id = view.appWidgetId
        val previous = registeredCopies[id]
        rebindingView = view
        registeredCopies[id] = WeakReference(view)
        try {
            createView(view.context, id, info)
        } catch (error: RuntimeException) {
            if (previous == null) registeredCopies.remove(id) else registeredCopies[id] = previous
            Log.w("GraceWidgetHost", "Could not restore visible copy id=$id", error)
        } finally {
            rebindingView = null
        }
    }

    override fun onProvidersChanged() { onProvidersUpdated?.invoke() }
    override fun onAppWidgetRemoved(appWidgetId: Int) {
        copies.remove(appWidgetId)
        registeredCopies.remove(appWidgetId)
        super.onAppWidgetRemoved(appWidgetId)
        onProvidersUpdated?.invoke()
    }

    /** Setup activities sharing this host must not reclaim each other's in-flight IDs. */
    fun trackPendingId(id: Int) { pendingIds += id }
    fun untrackPendingId(id: Int) { pendingIds -= id }
    fun isPendingId(id: Int): Boolean = id in pendingIds

    companion object {
        const val HOST_ID = 0x47524143
        private val pendingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    }
}

/** Observe the native widget's gestures without stealing its buttons or scrolling. */
class HomeWidgetHostView(context: Context) : AppWidgetHostView(context) {
    internal var onContentUpdated: ((HomeWidgetHostView, RemoteViews?) -> Unit)? = null
    internal var onAttached: ((HomeWidgetHostView) -> Unit)? = null
    var onWidgetLongPress: (() -> Unit)? = null
    var onWidgetDragStart: ((Float, Float) -> Unit)? = null
    var onWidgetDrag: ((Float, Float) -> Unit)? = null
    var onWidgetDragEnd: ((Boolean) -> Unit)? = null
    var interactionEnabled = true
    var hapticsEnabled = true
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var held = false
    private var lastRawX = 0f
    private var lastRawY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable {
        if (onWidgetLongPress == null || !isAttachedToWindow || !interactionEnabled) return@Runnable
        held = true
        val cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, downY, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        parent?.requestDisallowInterceptTouchEvent(true)
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onWidgetDragStart?.invoke(downX, downY) ?: onWidgetLongPress?.invoke()
    }

    init { isClickable = true }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        // Also applies after provider metadata changes and public createView re-registration.
        // This changes the host inset only; provider-owned padding/margins are untouched.
        setPadding(0, 0, 0, 0)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        onContentUpdated?.invoke(this, remoteViews)
    }

    internal fun applyMirroredContent(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onAttached?.invoke(this)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (held) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    onWidgetDrag?.invoke(event.rawX - lastRawX, event.rawY - lastRawY)
                    lastRawX = event.rawX; lastRawY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    held = false
                    onWidgetDragEnd?.invoke(event.actionMasked != MotionEvent.ACTION_UP)
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return true
        }
        if (!interactionEnabled) { removeCallbacks(longPress); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downTime = event.downTime
                lastRawX = event.rawX; lastRawY = event.rawY
                removeCallbacks(longPress)
                postDelayed(longPress, widgetLongPressTimeout(ViewConfiguration.getLongPressTimeout().toLong()))
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) removeCallbacks(longPress)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        if (held) onWidgetDragEnd?.invoke(true)
        held = false
        super.onDetachedFromWindow()
    }
}
