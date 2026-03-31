# Copilot Instructions for GitHub Updater

## Build Commands

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (requires signing env vars)
./gradlew :app:assembleRelease --build-cache

# Clean build
./gradlew clean :app:assembleDebug
```

Release signing uses environment variables: `RELEASE_KEYSTORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Falls back to debug signing when absent.

Versioning is automatic: `versionName` from latest git tag (stripped `v` prefix), `versionCode` from `git rev-list --count HEAD`.

## Architecture

Single-module Android app (`app/`) using **MVVM + Compose** with manual dependency injection.

### Layer Overview

- **UI** (`ui/`): Jetpack Compose screens driven by `MainViewModel` via `StateFlow<MainUiState>`. Adaptive layout switches between compact (phone navigation) and expanded (tablet split-pane) based on window width.
- **Data** (`data/`): `AppRepository` fuses remote GitHub releases with local catalog/install state. `GitHubReleasesService` is a raw `HttpURLConnection` client with 15-min in-memory cache. Local storage is `SharedPreferences`-backed (`AppCatalogRepository`, `AppSettingsRepository`).
- **Install** (`install/`): `DownloadService` (foreground service) handles APK downloads with wake/wifi locks. `ReleaseInstaller` pipes downloaded APKs into Android's `PackageInstaller`. `InstallResultReceiver` handles system callbacks and emits results via `InstallResultEvents` (SharedFlow event bus).
- **DI** (`di/AppContainer.kt`): Manual singleton container created in `GitHubUpdaterApplication.onCreate()`.

### Key Data Flow

1. User taps download → `MainViewModel.onPrimaryAction()` → `DownloadService.startDownload()` (foreground service)
2. Service runs `ReleaseInstaller.install()` → `ApkDownloadStore.prepareApk()` (download + SHA-256 verify) → `PackageInstaller` session commit
3. System sends result to `InstallResultReceiver` → emits `InstallResultEvent` → ViewModel clears progress, refreshes status
4. Package change `BroadcastReceiver` in `MainActivity` triggers `refreshLocalStatus()` for real-time UI updates on install/uninstall

### Error Handling

`AppRepository.loadManagedApps()` catches per-app API failures and throws `PartialLoadException` carrying both the successfully-loaded apps and deduplicated error messages. The ViewModel updates UI with partial data and shows an error banner.

## Conventions

### Version Resolution

`AppRepository.resolvedVersionName()` extracts version from APK asset filename using `versionRegex` capture group 1. Falls back to release tag name with `v`/`V` prefix stripped. This logic must stay consistent across `AppRepository`, `VersionHistoryScreen`, and `AddEditAppScreen` (test result).

### Asset Matching

`apkRegex` in catalog entries matches against asset filenames. The repository appends `\.apk$` automatically — catalog entries should NOT include the `.apk` suffix in their regex.

### API Calls

`refresh()` uses the 15-min service cache. `refreshLocalStatus()` uses `useCachedRemoteDataOnly=true` for zero API calls. `refreshNow()` forces fresh API calls. Minimize GitHub API usage — only call `refresh()` when remote data may have changed (e.g., repo config changed, explicit pull-to-refresh).

### Notification Channel

Download notifications use channel ID `DownloadService.CHANNEL_ID` ("download_progress"). The channel is created in `MainActivity.onCreate()`. The service manages its own notification updates during downloads.

### Storage

- APK downloads go to `Download/GitHubUpdater/` (MediaStore public Downloads) or a user-selected SAF tree URI.
- App catalog stored in SharedPreferences key `"app_catalog"` → `"apps"` as JSON array.
- Settings stored in SharedPreferences key `"github_updater_preferences"`.

### Code Style

- Concise code with minimal comments — only comment when something genuinely needs clarification.
- No Copilot co-author trailers in commit messages.
