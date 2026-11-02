package com.galaxyrio.gracelauncher.ui.overlays

import android.content.ComponentName
import android.appwidget.AppWidgetManager
import android.os.Process
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.data.LauncherProfiles
import com.galaxyrio.gracelauncher.data.LauncherProfileType
import com.galaxyrio.gracelauncher.ui.LauncherActions
import com.galaxyrio.gracelauncher.ui.LauncherUiState
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherSymbol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun HomeWidgetSheet(uiState: LauncherUiState, actions: LauncherActions, onChange: (LauncherOverlay?) -> Unit, custom: Boolean) {
    val settings = uiState.settings
    val widget = settings.homeLayout
    val component = widget.widgetProvider?.let(ComponentName::unflattenFromString)
    val context = LocalContext.current
    val owner by produceState<LauncherApp?>(null, context, widget.widgetId, component) {
        value = null
        value = withContext(Dispatchers.IO) {
            val provider = component ?: return@withContext null
            val info = runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(widget.widgetId) }
                .getOrNull()?.takeIf { it.provider == provider } ?: return@withContext null
            val profile = LauncherProfiles(context).snapshot().profiles.firstOrNull { it.user == info.profile }
                ?.takeIf { it.isPublic } ?: return@withContext null
            val foreignUser = profile.user.takeUnless { it == Process.myUserHandle() }
            LauncherApp(provider, widget.widgetLabel ?: provider.packageName, null,
                user = foreignUser, userSerial = profile.serial.takeIf { foreignUser != null },
                isWorkProfile = profile.type == LauncherProfileType.Work,
                isCloneProfile = profile.type == LauncherProfileType.Clone)
        }
    }
    val app = owner?.let { target ->
        // The provider's actual user distinguishes personal and cloned copies of one package.
        uiState.apps.firstOrNull { it.packageName == target.packageName && it.user == target.user &&
            it.shortcut == null && it.folderId == null } ?: target
    }
    fun navigate(page: String) { onChange(LauncherOverlay.SettingsDestination(page)) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (custom && app != null) { AppIcon(app, size = 34.dp); Spacer(Modifier.width(20.dp)) }
            Text(if (custom) widget.widgetLabel ?: stringResource(R.string.widget_home_menu) else stringResource(R.string.widget_home_menu),
                fontSize = 25.sp, fontWeight = FontWeight.Medium)
        }
        if (custom) {
            WidgetAction(LauncherSymbol.Home, stringResource(R.string.edit_homepage)) { onChange(LauncherOverlay.Homepage()) }
            if (app != null) WidgetAction(LauncherSymbol.Info, stringResource(R.string.app_info)) { onChange(null); actions.appInfo(app) }
            WidgetAction(LauncherSymbol.Resize, stringResource(R.string.widget_move_resize)) { actions.moveWidget() }
            WidgetAction(LauncherSymbol.Edit, stringResource(R.string.widget_configuration)) { onChange(null); actions.configureWidget() }
            WidgetAction(LauncherSymbol.Delete, stringResource(R.string.widget_remove)) { onChange(null); actions.removeWidget() }
        } else {
            WidgetAction(LauncherSymbol.Clock, stringResource(R.string.settings_clock_style), arrow = true) { navigate("ClockStyle") }
            WidgetAction(LauncherSymbol.Calendar, stringResource(R.string.widget_calendar),
                stringResource(R.string.settings_calendar_agenda_summary), arrow = true) { navigate("Calendar") }
            WidgetAction(LauncherSymbol.Weather, stringResource(R.string.settings_weather), arrow = true) { navigate("Weather") }
            WidgetToggle(stringResource(R.string.settings_media_player), stringResource(R.string.media_player_summary), settings.mediaPlayer) { enabled ->
                actions.updateSettings { it.copy(mediaPlayer = enabled) }
                if (enabled && !uiState.media.hasAccess) actions.requestMediaAccess()
            }
            WidgetToggle(stringResource(R.string.settings_show_battery), null, settings.showBatteryPercentage) { enabled ->
                actions.updateSettings { it.copy(showBatteryPercentage = enabled) }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 8.dp),
            thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        if (!custom) WidgetAction(LauncherSymbol.Home, stringResource(R.string.edit_homepage), arrow = true) { onChange(LauncherOverlay.Homepage()) }
        WidgetAction(LauncherSymbol.Settings, stringResource(R.string.grace_settings), arrow = !custom) { onChange(LauncherOverlay.Settings) }
    }
}

@Composable
private fun WidgetAction(symbol: LauncherSymbol, label: String, summary: String? = null, arrow: Boolean = false, onClick: () -> Unit) {
    PanelAction(symbol, label, iconColumnWidth = 34.dp, iconTextSpacing = 20.dp, summary = summary,
        trailing = if (arrow) ({ Icon(painterResource(R.drawable.ms_expand_more), null, Modifier.rotate(-90f).size(24.dp)) }) else null,
        onClick = onClick)
}

@Composable
private fun WidgetToggle(label: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
        .toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp)
            if (summary != null) Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked, onCheckedChange = null)
    }
}
