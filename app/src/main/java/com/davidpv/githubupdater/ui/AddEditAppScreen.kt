package com.davidpv.githubupdater.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidpv.githubupdater.data.matchesAssetRules
import com.davidpv.githubupdater.data.matchesReleaseRules
import com.davidpv.githubupdater.data.model.AppCatalogEntry
import com.davidpv.githubupdater.data.model.GitHubReleaseResponse
import com.davidpv.githubupdater.data.model.VersionRegexTarget
import com.davidpv.githubupdater.data.resolvedVersionName
import com.davidpv.githubupdater.ui.theme.LocalStatusPalette
import kotlinx.coroutines.launch

data class AddEditAppState(
    val displayName: String = "",
    val packageName: String = "",
    val releaseOwner: String = "",
    val releaseRepo: String = "",
    val apkRegex: String = "",
    val releaseRegex: String = "",
    val versionRegex: String = "",
    val versionRegexTarget: VersionRegexTarget = VersionRegexTarget.APK,
)

sealed interface TestResult {
    data class Success(val releaseName: String, val assetName: String, val versionName: String, val foundInNonLatestRelease: Boolean = false) : TestResult
    data class Error(val message: String) : TestResult
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAppScreen(
    existingEntry: AppCatalogEntry?,
    onSave: (AppCatalogEntry) -> Unit,
    onDelete: (() -> Unit)?,
    onTest: suspend (owner: String, repo: String) -> List<GitHubReleaseResponse>,
    onBack: () -> Unit,
) {
    val isEditing = existingEntry != null
    val initialState = remember {
        if (existingEntry != null) {
            AddEditAppState(
                displayName = existingEntry.displayName,
                packageName = existingEntry.packageName,
                releaseOwner = existingEntry.releaseOwner,
                releaseRepo = existingEntry.releaseRepo,
                apkRegex = existingEntry.apkRegex.orEmpty(),
                releaseRegex = existingEntry.releaseRegex.orEmpty(),
                versionRegex = existingEntry.versionRegex.orEmpty(),
                versionRegexTarget = existingEntry.versionRegexTarget,
            )
        } else {
            AddEditAppState()
        }
    }
    var state by remember { mutableStateOf(initialState) }
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var showTestSpinner by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val hasChanges = state != initialState

    fun handleBack() {
        if (hasChanges) showDiscardDialog = true else onBack()
    }

    LaunchedEffect(isTesting) {
        if (isTesting) {
            kotlinx.coroutines.delay(750L)
            showTestSpinner = true
        } else {
            showTestSpinner = false
        }
    }

    val canTest = state.releaseOwner.isNotBlank() && state.releaseRepo.isNotBlank()
    val canSave = state.displayName.isNotBlank() &&
        state.packageName.isNotBlank() &&
        state.releaseOwner.isNotBlank() &&
        state.releaseRepo.isNotBlank()

    BackHandler(enabled = hasChanges) { showDiscardDialog = true }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to go back?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit app" else "New app") },
                navigationIcon = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Back") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = { handleBack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (onDelete != null) {
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text("Delete app") } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete app")
                            }
                        }
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Delete app") },
                                text = { Text("Are you sure you want to remove \"${state.displayName}\" from your catalog?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showDeleteDialog = false
                                        onDelete()
                                    }) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancel")
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "GitHub Release Source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = { state = state.copy(displayName = it); saveError = null },
                    label = { Text("Display name") },
                    placeholder = { Text("e.g. Twitter") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.packageName,
                    onValueChange = { state = state.copy(packageName = it.trim()); saveError = null },
                    label = { Text("Package name") },
                    placeholder = { Text("e.g. com.twitter.android") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.releaseOwner,
                        onValueChange = { state = state.copy(releaseOwner = it.trim()); testResult = null; saveError = null },
                        label = { Text("Owner") },
                        placeholder = { Text("e.g. crimera") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.releaseRepo,
                        onValueChange = { state = state.copy(releaseRepo = it.trim()); testResult = null; saveError = null },
                        label = { Text("Repository") },
                        placeholder = { Text("e.g. twitter-apk") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Text(
                    text = "Filters (optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = state.releaseRegex,
                    onValueChange = { state = state.copy(releaseRegex = it); testResult = null; saveError = null },
                    label = { Text("Release regex") },
                    placeholder = { Text("e.g. youtube-music") },
                    supportingText = { Text("Regex filter on the release tag. Leave blank to use all releases.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.apkRegex,
                    onValueChange = { state = state.copy(apkRegex = it); testResult = null; saveError = null },
                    label = { Text("APK regex") },
                    placeholder = { Text("e.g. ^twitter-piko-v.*") },
                    supportingText = { Text("Regex filter on the APK filename. Leave blank to match any APK.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = state.versionRegex,
                            onValueChange = { state = state.copy(versionRegex = it); testResult = null; saveError = null },
                            label = { Text("Version regex") },
                            placeholder = { Text("e.g. -V(.+)-release") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        VersionRegexTargetDropdown(
                            selected = state.versionRegexTarget,
                            onSelected = { state = state.copy(versionRegexTarget = it); testResult = null },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Text(
                        text = "Extracts version via capture group 1. Leave blank for automatic extraction.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                testResult = runTestConfig(
                                    state = state,
                                    fetchReleases = onTest,
                                )
                                isTesting = false
                            }
                        },
                        enabled = canTest && !isTesting,
                    ) {
                        if (showTestSpinner) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Test")
                    }

                    Button(
                        onClick = {
                            val entry = state.toEntry()
                            runCatching { onSave(entry) }
                                .onFailure { saveError = it.message }
                        },
                        enabled = canSave,
                    ) {
                        Text("Save")
                    }
                }
            }

            testResult?.let { result ->
                item { TestResultCard(result) }
            }

            saveError?.let { error ->
                item { ErrorResultCard(error) }
            }
        }
    }
}

@Composable
private fun LabelValueText(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            softWrap = false,
        )
    }
}

@Composable
private fun TestResultCard(result: TestResult) {
    when (result) {
        is TestResult.Success -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Configuration works!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    LabelValueText("Release tag", result.releaseName)
                    LabelValueText("APK filename", result.assetName)
                    LabelValueText("Version detected", result.versionName)
                }
            }
        }

        is TestResult.Error -> {
            ErrorResultCard(result.message)
        }
    }
}

@Composable
private fun ErrorResultCard(message: String) {
    val palette = LocalStatusPalette.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = palette.warningContainer,
            contentColor = palette.onWarningContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private suspend fun runTestConfig(
    state: AddEditAppState,
    fetchReleases: suspend (owner: String, repo: String) -> List<GitHubReleaseResponse>,
): TestResult {
    return runCatching {
        val releases = fetchReleases(state.releaseOwner, state.releaseRepo)
        if (releases.isEmpty()) return TestResult.Error("No releases found for ${state.releaseOwner}/${state.releaseRepo}.")

        val tempEntry = state.toEntry()
        var isFirstValidRelease = true
        for (release in releases) {
            if (release.draft || release.prerelease) continue
            if (!matchesReleaseRules(release.tagName, tempEntry)) continue
            for (asset in release.assets) {
                if (!matchesAssetRules(asset, tempEntry)) continue
                return TestResult.Success(
                    releaseName = release.tagName,
                    assetName = asset.name,
                    versionName = resolvedVersionName(release.tagName, release.name, asset.name, tempEntry),
                    foundInNonLatestRelease = !isFirstValidRelease,
                )
            }
            // releaseRegex-only mode: no apkRegex required, grab any APK
            if (!tempEntry.apkRegex.isNullOrBlank()) {
                isFirstValidRelease = false
                continue
            }
            val anyApk = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (anyApk != null) {
                return TestResult.Success(
                    releaseName = release.tagName,
                    assetName = anyApk.name,
                    versionName = resolvedVersionName(release.tagName, release.name, anyApk.name, tempEntry),
                    foundInNonLatestRelease = !isFirstValidRelease,
                )
            }
            isFirstValidRelease = false
        }
        TestResult.Error("No matching release/APK found.\nCheck your APK regex and Release regex.")
    }.getOrElse { e ->
        TestResult.Error(e.message ?: "Test failed.")
    }
}

private fun AddEditAppState.toEntry() = AppCatalogEntry(
    displayName = displayName.trim(),
    packageName = packageName.trim(),
    releaseOwner = releaseOwner.trim(),
    releaseRepo = releaseRepo.trim(),
    apkRegex = apkRegex.trim().takeIf { it.isNotBlank() },
    releaseRegex = releaseRegex.trim().takeIf { it.isNotBlank() },
    versionRegex = versionRegex.trim().takeIf { it.isNotBlank() },
    versionRegexTarget = versionRegexTarget,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionRegexTargetDropdown(
    selected: VersionRegexTarget,
    onSelected: (VersionRegexTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(selected.label, style = MaterialTheme.typography.bodyMedium)
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            VersionRegexTarget.entries.forEach { target ->
                DropdownMenuItem(
                    text = { Text(target.label) },
                    onClick = { onSelected(target); expanded = false },
                )
            }
        }
    }
}

private val VersionRegexTarget.label: String
    get() = when (this) {
        VersionRegexTarget.APK -> "APK"
        VersionRegexTarget.Release -> "Release"
    }

