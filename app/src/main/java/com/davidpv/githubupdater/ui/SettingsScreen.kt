package com.davidpv.githubupdater.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.davidpv.githubupdater.data.model.AppSettings
import com.davidpv.githubupdater.data.model.ThemeMode
import com.davidpv.githubupdater.data.model.VersionCompareDepth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: AppSettings,
    downloadLocationSummary: String,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDeleteApkAfterInstall: (Boolean) -> Unit,
    onSetRefreshOnStart: (Boolean) -> Unit,
    onSetVersionCompareDepth: (VersionCompareDepth) -> Unit,
    onSetGitHubToken: (String) -> Unit,
    onSetMirrorBaseUrl: (String) -> Unit = {},
    onPickDownloadFolder: () -> Unit,
    onUseDefaultDownloadLocation: () -> Unit,
    modifier: Modifier = Modifier,
    onImportConfig: () -> Unit = {},
    onExportConfig: () -> Unit = {},
    onClearApps: () -> Unit = {},
    currentAppsJson: String = "[]",
    onBulkEditApps: (String) -> String? = { null },
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var showBulkEditor by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear app data?") },
            text = { Text("This will remove all apps from your catalog. Settings will not be affected. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearApps()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showBulkEditor) {
        BulkEditDialog(
            initialJson = currentAppsJson,
            onDismiss = { showBulkEditor = false },
            onSave = onBulkEditApps,
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
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
                    title = "Refresh on start",
                    subtitle = "Fetch latest releases from GitHub when the app opens",
                    checked = settings.refreshOnStart,
                    onCheckedChange = onSetRefreshOnStart,
                )
                SettingsSwitchRow(
                    title = "Delete APKs after install",
                    subtitle = "Remove downloaded APK files automatically",
                    checked = settings.deleteApkAfterInstall,
                    onCheckedChange = onSetDeleteApkAfterInstall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!settings.usesDefaultDownloadDirectory) {
                            OutlinedButton(onClick = onUseDefaultDownloadLocation) {
                                Text("Use default")
                            }
                        }
                        Button(onClick = onPickDownloadFolder) {
                            Text("Choose")
                        }
                    }
                }
                VersionCompareDepthDropdown(
                    selected = settings.versionCompareDepth,
                    onSelected = onSetVersionCompareDepth,
                )
            }
        }

        // App Configuration
        item {
            SettingsSection(title = "GitHub") {
                Text(
                    text = "Optional token for authenticated requests and GraphQL batching",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GitHubTokenField(
                    value = settings.gitHubToken.orEmpty(),
                    onValueChange = onSetGitHubToken,
                )
                Text(
                    text = "Local mirror URL (e.g. http://raspberrypi:8080). When set, APKs are fetched from your local mirror over Wi-Fi first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MirrorBaseUrlField(
                    value = settings.mirrorBaseUrl.orEmpty(),
                    onValueChange = onSetMirrorBaseUrl,
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showBulkEditor = true }) {
                        Text("Bulk edit")
                    }
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Clear all")
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubTokenField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != text) text = value
    }
    val trimmedText = text.trim()
    val savedValue = value.trim()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .border(width = 1.dp, color = borderColor, shape = shape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "ghp_...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    text = ""
                    onValueChange("")
                },
                enabled = text.isNotEmpty() || value.isNotEmpty(),
            ) {
                Text("Reset")
            }
            Button(
                onClick = {
                    onValueChange(trimmedText)
                    focusManager.clearFocus(force = true)
                },
                enabled = trimmedText != savedValue,
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun MirrorBaseUrlField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != text) text = value
    }
    val trimmedText = text.trim().trimEnd('/')
    val savedValue = value.trim().trimEnd('/')
    val shape = RoundedCornerShape(12.dp)
    val borderColor = MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .border(width = 1.dp, color = borderColor, shape = shape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "http://raspberrypi:8080",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    text = ""
                    onValueChange("")
                },
                enabled = text.isNotEmpty() || value.isNotEmpty(),
            ) {
                Text("Reset")
            }
            Button(
                onClick = {
                    onValueChange(trimmedText)
                    focusManager.clearFocus(force = true)
                },
                enabled = trimmedText != savedValue,
            ) {
                Text("Save")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionCompareDepthDropdown(
    selected: VersionCompareDepth,
    onSelected: (VersionCompareDepth) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Version comparison",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Segments to compare for updates",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                VersionCompareDepth.entries.forEach { depth ->
                    DropdownMenuItem(
                        text = { Text(depth.label) },
                        onClick = {
                            onSelected(depth)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private val VersionCompareDepth.label: String
    get() = when (this) {
        VersionCompareDepth.All -> "All segments"
        VersionCompareDepth.Patch -> "Up until patch"
        VersionCompareDepth.Minor -> "Up until minor"
        VersionCompareDepth.Major -> "Major only"
    }

@Composable
private fun BulkEditDialog(
    initialJson: String,
    onDismiss: () -> Unit,
    onSave: (String) -> String?,
) {
    var text by remember { mutableStateOf(initialJson) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Bulk edit apps") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Edit the JSON array below and save to apply changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val shape = RoundedCornerShape(8.dp)
                val borderColor = if (errorMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; errorMessage = null },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp)
                        .border(1.dp, borderColor, shape)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                )
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onSave(text)
                if (result == null) {
                    onDismiss()
                } else {
                    errorMessage = result
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    )
}
