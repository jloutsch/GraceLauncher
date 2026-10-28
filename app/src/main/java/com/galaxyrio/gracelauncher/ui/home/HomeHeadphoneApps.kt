package com.galaxyrio.gracelauncher.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxyrio.gracelauncher.R
import com.galaxyrio.gracelauncher.data.LauncherApp
import com.galaxyrio.gracelauncher.ui.components.AppIcon
import com.galaxyrio.gracelauncher.ui.components.LauncherLayout
import com.galaxyrio.gracelauncher.ui.components.LocalLauncherInputEnabled
import com.galaxyrio.gracelauncher.ui.theme.LocalLauncherAppearance

/** Shown under the media player after headphones connect: one tap opens the chosen player. */
@Composable
internal fun HomeHeadphoneApps(
    apps: List<LauncherApp>,
    onLaunch: (LauncherApp) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalLauncherAppearance.current
    val enabled = LocalLauncherInputEnabled.current
    Column(modifier.fillMaxWidth().testTag("home_headphone_apps")
        .padding(horizontal = LauncherLayout.ContentInset, vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ms_headphones), null, Modifier.size(20.dp), tint = appearance.text)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.headphone_apps_connected), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, shadow = appearance.textShadow),
                color = appearance.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onDismiss, enabled = enabled,
                modifier = Modifier.size(40.dp).clip(CircleShape).testTag("home_headphone_apps_dismiss"),
                colors = IconButtonDefaults.iconButtonColors(contentColor = appearance.text)) {
                Icon(painterResource(R.drawable.ms_close), stringResource(R.string.headphone_apps_dismiss), Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            apps.forEach { app ->
                Column(Modifier.width(76.dp).clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = app.label) { onLaunch(app) }
                    .padding(vertical = 6.dp).testTag("home_headphone_app:${app.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(app, size = 44.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(app.label, Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(shadow = appearance.textShadow),
                        color = appearance.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
