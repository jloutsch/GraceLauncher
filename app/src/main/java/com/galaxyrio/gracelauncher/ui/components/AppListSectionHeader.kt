package com.galaxyrio.gracelauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.data.CategoryIcon
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance

/** The real drawer and appearance preview share one offset for every section heading. */
@Composable
internal fun AppListSectionHeader(label: String, modifier: Modifier = Modifier, icon: CategoryIcon? = null) {
    val labelStyle = listLabelStyle()
    val style = TextStyle(fontFamily = labelStyle.fontFamily, fontSize = 18.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Normal, shadow = LocalLauncherAppearance.current.textShadow)
    val headerModifier = modifier.height(44.dp)
        .absoluteOffset(x = LocalListAppearance.current.symbolHorizontalOffset.dp)
        .padding(start = LauncherLayout.ContentInset, end = LauncherLayout.ContentInset, top = 12.dp)
        .semantics { heading() }
    if (icon == null) Text(label, headerModifier, color = labelStyle.color, style = style)
    else {
        val letterSize = rememberTextMeasurer().measure("C", style = style, maxLines = 1).size
        Box(headerModifier) {
            // Preserve the letter column when a wider symbol replaces its glyph.
            CategoryIndicator(icon, Modifier.layout { measurable, constraints ->
                val indicator = measurable.measure(constraints)
                val width = constraints.constrainWidth(letterSize.width)
                val height = constraints.constrainHeight(letterSize.height)
                layout(width, height) {
                    indicator.placeRelative((width - indicator.width) / 2, (height - indicator.height) / 2)
                }
            }.size(24.dp), tint = labelStyle.color, contentDescription = label)
        }
    }
}
