package com.galaxyrio.gracelauncher.ui.widgets

import com.galaxyrio.gracelauncher.ui.theme.WithoutFontShadow

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import com.galaxyrio.gracelauncher.ui.theme.FontShadowText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.WidgetSetupActivity
import com.galaxyrio.gracelauncher.data.HomeLayout
import com.galaxyrio.gracelauncher.ui.components.LauncherIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol

/** Always expose settings; some providers have a configure Activity but omit feature flags. */
@Composable
internal fun WidgetConfigurationButton(layout: HomeLayout, onBeforeOpen: () -> Unit) = WithoutFontShadow {
    AssistChip(onClick = rememberWidgetConfigurationAction(layout, onBeforeOpen),
        label = { Text(stringResource(R.string.widget_settings_button), maxLines = 1) },
        modifier = Modifier.testTag("widget_settings"))
}

/** Shared by chips and connected controls; provider lookup must happen at click time. */
@Composable
internal fun rememberWidgetConfigurationAction(layout: HomeLayout, onBeforeOpen: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val beforeOpen by rememberUpdatedState(onBeforeOpen)
    return remember(context, layout.widgetId) { action@{
        // Look up at click time so provider updates do not leave a stale settings target.
        val info = runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(layout.widgetId) }
            .getOrElse {
                Log.e("GraceWidgetSetup", "settings lookup failed id=${layout.widgetId}", it)
                Toast.makeText(context, R.string.widget_configuration_launch_error, Toast.LENGTH_LONG).show()
                return@action
            }
        if (info?.configure == null) {
            Toast.makeText(context, R.string.widget_no_configuration, Toast.LENGTH_SHORT).show()
            return@action
        }
        beforeOpen()
        runCatching {
            context.startActivity(Intent(context, WidgetSetupActivity::class.java)
                .putExtra(WidgetSetupActivity.EXTRA_CONFIGURE_ID, layout.widgetId))
        }.onFailure {
            Log.e("GraceWidgetSetup", "reconfigure launch failed id=${layout.widgetId} provider=${info.provider}", it)
            Toast.makeText(context, R.string.widget_configuration_launch_error, Toast.LENGTH_LONG).show()
        }
    } }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun WidgetEditButtonGroup(
    fullWidth: Boolean, onFullWidthChange: (Boolean) -> Unit, onSettings: () -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    background: Boolean? = null, onBackgroundChange: ((Boolean) -> Unit)? = null, onDone: (() -> Unit)? = null,
) = WithoutFontShadow {
    val scheme = MaterialTheme.colorScheme
    val fullWidthLabel = stringResource(R.string.popup_widget_full_width)
    val backgroundLabel = stringResource(R.string.popup_widget_background)
    val settingsLabel = stringResource(R.string.widget_settings_button)
    val deleteLabel = stringResource(R.string.settings_delete)
    val doneLabel = stringResource(R.string.done)
    val hasBackground = background != null && onBackgroundChange != null
    val labelStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fullWidthMinimum = with(density) {
        textMeasurer.measure(fullWidthLabel, labelStyle, maxLines = 1, softWrap = false).size.width.toDp()
    }.plus(12.dp).coerceAtLeast(64.dp)
    val backgroundMinimum = with(density) {
        textMeasurer.measure(backgroundLabel, labelStyle, maxLines = 1, softWrap = false).size.width.toDp()
    }.plus(12.dp).coerceAtLeast(64.dp)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val minimumWidth = fullWidthMinimum + if (hasBackground)
                    backgroundMinimum + ButtonGroupDefaults.ConnectedSpaceBetween else 0.dp
                val fits = maxWidth >= minimumWidth
                // Weighted buttons fill the bounded row. Only an overflowing group uses
                // fixed widths in a scroll container, leaving the circular actions visible.
                Row(if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    ToggleButton(fullWidth, onFullWidthChange, enabled = enabled,
                        shapes = if (hasBackground) ButtonGroupDefaults.connectedLeadingButtonShapes()
                            else ToggleButtonDefaults.shapesFor(40.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = (if (fits) Modifier.weight(fullWidthMinimum.value) else Modifier.width(fullWidthMinimum))
                            .height(40.dp).testTag("popup_widget_full_width")
                            .semantics { contentDescription = fullWidthLabel }) {
                        Text(fullWidthLabel, maxLines = 1, softWrap = false,
                            style = labelStyle, textAlign = TextAlign.Center)
                    }
                    if (background != null && onBackgroundChange != null) ToggleButton(background, onBackgroundChange, enabled = enabled,
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = (if (fits) Modifier.weight(backgroundMinimum.value) else Modifier.width(backgroundMinimum))
                            .height(40.dp).testTag("popup_widget_background")
                            .semantics { contentDescription = backgroundLabel }) {
                        Text(backgroundLabel, maxLines = 1, softWrap = false,
                            style = labelStyle, textAlign = TextAlign.Center)
                    }
                }
            }
            FilledIconButton(onSettings, enabled = enabled, modifier = Modifier.size(40.dp).testTag("widget_settings")
                .semantics { contentDescription = settingsLabel }) {
                LauncherIcon(LauncherSymbol.Settings)
            }
            FilledIconButton(onDelete, enabled = enabled, modifier = Modifier.size(40.dp).testTag("widget_delete")
                .semantics { contentDescription = deleteLabel }, colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer)) {
                LauncherIcon(LauncherSymbol.Delete)
            }
            if (onDone != null) FilledIconButton(onDone, enabled = enabled, modifier = Modifier.size(40.dp)
                .testTag("popup_widget_done").semantics { contentDescription = doneLabel }) {
                LauncherIcon(LauncherSymbol.Check)
            }
        }
    }
}
