package com.galaxyrio.gracelauncher.ui.components

import kotlin.math.abs
import kotlin.math.atan2

/** Evaluated before consuming movement so rejected swipes can still scroll the list. */
internal class PopupSwipeStart(
    private val touchSlop: Float,
    private val extraDistance: Float,
    private val maxAngleDegrees: Int,
    private val leftEnabled: Boolean,
    private val rightEnabled: Boolean = true,
) {
    enum class Decision { Pending, Reject, Right, Left }

    fun evaluate(x: Float, y: Float): Decision {
        if (x < -touchSlop) return if (leftEnabled) Decision.Left else Decision.Reject
        if (x <= touchSlop) return Decision.Pending
        if (!rightEnabled) return Decision.Reject
        val angle = Math.toDegrees(atan2(abs(y).toDouble(), x.toDouble()))
        if (angle > maxAngleDegrees) return Decision.Reject
        return if (x >= touchSlop + extraDistance) Decision.Right else Decision.Pending
    }
}
