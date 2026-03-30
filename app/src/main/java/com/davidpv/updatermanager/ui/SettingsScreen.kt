package com.davidpv.updatermanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidpv.updatermanager.data.model.AppSettings
import com.davidpv.updatermanager.data.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: AppSettings,
    downloadLocationSummary: String,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDeleteApkAfterInstall: (Boolean) -> Unit,
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    modifier: Modifier = Modifier,
    onImportConfig: () -> Unit = {},
    onExportConfig: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Appearance
        item {
            SettingsSection(title = "Appearance") {
                ThemeDropdown(
                    selected = settings.themeMode,
                    onSelected = onSetThemeMode,
                )
                SettingsSwitchRow(
                    title = "Dynamic colors",
                    subtitle = "Follow wallpaper-based Material colors",
                    checked = settings.useDynamicColor,
                    onCheckedChange = onSetDynamicColor,
                )
            }
        }

        // Downloads
        item {
            SettingsSection(title = "Downloads") {
                SettingsSwitchRow(
                    title = "Delete APK after install",
                    subtitle = "Remove downloaded APK files automatically",
                    checked = settings.deleteApkAfterInstall,
                    onCheckedChange = onSetDeleteApkAfterInstall,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Download folder",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = downloadLocationSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPickDownloadFolder) {
                        Text("Choose folder")
                    }
                    if (!settings.usesDefaultDownloadDirectory) {
                        OutlinedButton(onClick = onUseDefaultDownloadLocation) {
                            Text("Use default")
                        }
                    }
                }
            }
        }

        // App Configuration
        item {
            SettingsSection(title = "App configuration") {
                Text(
                    text = "Import or export your app list as a JSON file",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onImportConfig) {
                        Text("Import")
                    }
                    OutlinedButton(onClick = onExportConfig) {
                        Text("Export")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .widthIn(max = 150.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onSelected(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.System -> "System"
        ThemeMode.Light -> "Light"
        ThemeMode.Dark -> "Dark"
    }

