@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.galaxyrio.gracelauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private class InputShadowLayout {
    var result: TextLayoutResult? = null
    val mask = FontShadowMaskCache()
}

/** M3's decorators retain the field chrome; the shadow is drawn only behind the editable glyphs. */
@Composable
internal fun FontShadowInput(
    state: TextFieldState, modifier: Modifier = Modifier, enabled: Boolean = true, search: Boolean = false,
    placeholder: (@Composable () -> Unit)? = null, leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null, inputTransformation: InputTransformation? = null,
    onSearch: () -> Unit = {},
) {
    val effect = resolvedFontShadow()
    val layout = remember { InputShadowLayout() }
    val scroll = rememberScrollState()
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val focusManager = LocalFocusManager.current
    val colors = if (search) SearchBarDefaults.inputFieldColors() else OutlinedTextFieldDefaults.colors()
    val textColor = when { !enabled -> colors.disabledTextColor; focused -> colors.focusedTextColor; else -> colors.unfocusedTextColor }
    val expanded = effect.spread > 0f && effect.shadow != Shadow.None
    val style = LocalTextStyle.current.copy(color = textColor, shadow = if (expanded) Shadow.None else effect.shadow)
    val decoration = if (search) TextFieldDefaults.decorator(
        state = state, enabled = enabled, lineLimits = TextFieldLineLimits.SingleLine, interactionSource = interactions,
        placeholder = placeholder, colors = colors, outputTransformation = null,
        leadingIcon = leadingIcon?.let { icon -> { Box(Modifier.offset(x = 4.dp)) { icon() } } },
        trailingIcon = trailingIcon?.let { icon -> { Box(Modifier.offset(x = (-4).dp)) { icon() } } },
        container = {
            val color = when { !enabled -> colors.disabledContainerColor; focused -> colors.focusedContainerColor; else -> colors.unfocusedContainerColor }
            Box(Modifier.background(color, SearchBarDefaults.inputFieldShape))
        },
    ) else OutlinedTextFieldDefaults.decorator(
        state = state, enabled = enabled, lineLimits = TextFieldLineLimits.SingleLine, interactionSource = interactions,
        placeholder = placeholder, colors = colors, leadingIcon = leadingIcon, trailingIcon = trailingIcon, outputTransformation = null,
    )
    val shadowModifier = if (!expanded) Modifier else Modifier.drawWithContent {
        layout.result?.let { result ->
            val canvas = drawContext.canvas.nativeCanvas
            val checkpoint = canvas.save()
            try {
                // The native field scrolls its glyph layer, independently of the fixed decoration.
                val scrollX = if (rtl) (scroll.maxValue - scroll.value).coerceAtLeast(0) else scroll.value
                val outset = effect.spread + effect.shadow.blurRadius * 2 + kotlin.math.abs(effect.shadow.offset.y)
                canvas.clipRect(0f, -outset, size.width, size.height + outset)
                // Cache only the viewport, so a long query cannot allocate a paragraph-wide bitmap.
                layout.mask.draw(canvas, listOf(result, scrollX, size), size.width, size.height,
                    result.multiParagraph.height / 2f, effect) { target, spread ->
                    target.translate(-scrollX.toFloat(), (size.height - result.size.height) / 2f)
                    val maskCanvas = Canvas(target)
                    result.multiParagraph.paint(maskCanvas, Color.White, shadow = Shadow.None,
                        drawStyle = Stroke(spread * 2f, join = StrokeJoin.Round))
                    result.multiParagraph.paint(maskCanvas, Color.White, shadow = Shadow.None, drawStyle = Fill)
                }
            } finally { canvas.restoreToCount(checkpoint) }
        }
        drawContent()
    }
    BasicTextField(state = state,
        modifier = modifier.onPreviewKeyEvent {
            if (search && (it.key == Key.DirectionDown || it.key == Key.NumPadDirectionDown)) {
                focusManager.moveFocus(FocusDirection.Down)
                true
            } else false
        }.then(if (search) Modifier.sizeIn(minWidth = 360.dp, maxWidth = 720.dp, minHeight = SearchBarDefaults.InputFieldHeight)
            else Modifier.sizeIn(minWidth = OutlinedTextFieldDefaults.MinWidth, minHeight = OutlinedTextFieldDefaults.MinHeight)),
        enabled = enabled, lineLimits = TextFieldLineLimits.SingleLine, textStyle = style,
        cursorBrush = SolidColor(colors.cursorColor), interactionSource = interactions, scrollState = scroll,
        inputTransformation = inputTransformation, keyboardOptions = KeyboardOptions(imeAction = if (search) ImeAction.Search else ImeAction.Default),
        onKeyboardAction = if (search) { { onSearch() } } else null,
        onTextLayout = { layout.result = it() },
        decorator = TextFieldDecorator { inner -> decoration.Decoration {
            Box(shadowModifier, propagateMinConstraints = true) { inner() }
        } },
    )
}
