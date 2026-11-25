package com.galaxyrio.gracelauncher.ui.theme

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

private class ShadowTextLayout {
    var layout: TextLayoutResult? = null
    val mask = FontShadowMaskCache()
}

/** M3 remains responsible for measurement, accessibility, inline content and the foreground text. */
@Composable
internal fun FontShadowText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null, fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1, onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current,
) {
    val effect = resolvedFontShadow()
    if (effect.spread > 0f && effect.shadow != Shadow.None) {
        FontShadowText(AnnotatedString(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily,
            letterSpacing, textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines,
            onTextLayout = onTextLayout, style = style)
    } else MaterialText(text, modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap,
        maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style.copy(shadow = effect.shadow))
}

@Composable
internal fun FontShadowText(
    text: AnnotatedString, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null, fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1, inlineContent: Map<String, InlineTextContent> = emptyMap(),
    onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current,
) {
    val effect = resolvedFontShadow()
    val expanded = effect.spread > 0f && effect.shadow != Shadow.None
    val layout = if (expanded) remember { ShadowTextLayout() } else null
    val shadowModifier = if (!expanded) Modifier else Modifier.drawWithContent {
        layout?.layout?.let { result ->
            layout.mask.draw(drawContext.canvas.nativeCanvas, result, result.size.width.toFloat(), result.size.height.toFloat(),
                inkPadding = result.multiParagraph.height.coerceAtMost(128f) / 2, effect = effect) { target, spread ->
                val canvas = Canvas(target)
                result.multiParagraph.paint(canvas, color = Color.White, shadow = Shadow.None,
                    drawStyle = Stroke(spread * 2, join = StrokeJoin.Round))
                result.multiParagraph.paint(canvas, color = Color.White, shadow = Shadow.None, drawStyle = Fill)
            }
        }
        drawContent()
    }
    MaterialText(text, modifier.then(shadowModifier), color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        inlineContent = inlineContent, onTextLayout = { result: TextLayoutResult -> layout?.layout = result; onTextLayout?.invoke(result) },
        style = style.copy(shadow = if (expanded) Shadow.None else effect.shadow))
}
