package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance

/** Only forwards native press events; Material's ripple owns all drawing and timing. */
internal class MediaTrackRipple {
    val interactions = MutableInteractionSource()
    var coordinates: LayoutCoordinates? = null

    fun positionOf(button: LayoutCoordinates?, position: Offset): Offset {
        val container = coordinates?.takeIf { it.isAttached } ?: return Offset.Unspecified
        val source = button?.takeIf { it.isAttached } ?: return Offset.Unspecified
        val local = if (position.isSpecified) position else Offset(source.size.width / 2f, source.size.height / 2f)
        return container.localPositionOf(source, local)
    }
}

/** Route presses to the widget ripple while keeping the native button's focus and hover feedback. */
internal class MediaButtonIndicationSource(private val source: MutableInteractionSource) : MutableInteractionSource {
    override val interactions = source.interactions.filter { it !is PressInteraction }
    override suspend fun emit(interaction: Interaction) = source.emit(interaction)
    override fun tryEmit(interaction: Interaction): Boolean = source.tryEmit(interaction)
}

@Composable
internal fun ForwardMediaTrackRipple(interactions: InteractionSource, ripple: MediaTrackRipple?,
    buttonCoordinates: () -> LayoutCoordinates?) {
    LaunchedEffect(interactions, ripple) {
        if (ripple == null) return@LaunchedEffect
        val presses = mutableMapOf<PressInteraction.Press, PressInteraction.Press>()
        try {
            interactions.interactions.filterIsInstance<PressInteraction>().collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        val mapped = PressInteraction.Press(ripple.positionOf(buttonCoordinates(), interaction.pressPosition))
                        presses[interaction] = mapped
                        ripple.interactions.emit(mapped)
                    }
                    is PressInteraction.Release -> presses.remove(interaction.press)?.let {
                        ripple.interactions.emit(PressInteraction.Release(it))
                    }
                    is PressInteraction.Cancel -> presses.remove(interaction.press)?.let {
                        ripple.interactions.emit(PressInteraction.Cancel(it))
                    }
                }
            }
        } finally {
            // Leaving the screen or removing a control must not leave a held ripple behind.
            presses.values.forEach { ripple.interactions.tryEmit(PressInteraction.Cancel(it)) }
        }
    }
}

@Composable
internal fun rememberMediaButtonScale(interactions: InteractionSource): State<Float> {
    val scale = remember { Animatable(1f) }
    val pressSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val releaseSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(interactions, pressSpec, releaseSpec) {
        interactions.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(.9f, pressSpec)
                is PressInteraction.Release -> {
                    // A quick tap can press and release in one frame; keep its feedback visible.
                    if (scale.value > .94f) scale.animateTo(.9f, pressSpec)
                    scale.animateTo(1f, releaseSpec)
                }
                is PressInteraction.Cancel -> scale.animateTo(1f, releaseSpec)
            }
        }
    }
    return scale.asState()
}
