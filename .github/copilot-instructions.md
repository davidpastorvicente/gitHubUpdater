# GitHub Updater — Copilot Instructions

## Project Overview

GitHub Updater is a single-module Android app for tracking and installing upstream APK releases outside of Google Play.

## Build Commands

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (requires signing env vars)
./gradlew :app:assembleRelease --build-cache

# Clean debug build
./gradlew clean :app:assembleDebug
```

Release signing uses environment variables: `RELEASE_KEYSTORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Falls back to debug signing when absent.

Versioning is automatic: `versionName` from the latest git tag (stripped `v` prefix), `versionCode` from `git rev-list --count HEAD`.

## Tech Stack

- **Language**: Kotlin 2.3.20
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + manual dependency injection
- **Networking**: `HttpURLConnection` + GitHub REST / GraphQL
- **Storage**: SharedPreferences
- **Async**: Kotlin Coroutines + StateFlow / SharedFlow
- **Serialization**: kotlinx.serialization
- **Min SDK**: 29, Target SDK: 36

## Project Structure

```text
app/src/main/java/com/davidpv/githubupdater/
├── data/
│   ├── local/        # SharedPreferences-backed repositories and cache
│   ├── model/        # Domain models
│   └── remote/       # GitHub API service models and requests
├── di/               # Manual app container
├── install/          # Download service, installer, install result handling
└── ui/               # Compose screens, state, navigation, and theme
```

## Architecture

### Layer Overview

- **UI** (`ui/`): Jetpack Compose screens driven by `MainViewModel` via `StateFlow<MainUiState>`. Adaptive layout switches between compact (phone navigation) and expanded (tablet split-pane) based on window width.
- **Data** (`data/`): `AppRepository` fuses remote GitHub releases with local catalog/install state. `GitHubReleasesService` is a raw `HttpURLConnection` client with a 15-minute in-memory cache. Local storage is `SharedPreferences`-backed (`AppCatalogRepository`, `AppSettingsRepository`).
- **Install** (`install/`): `DownloadService` handles APK downloads with wake and Wi-Fi locks. `ReleaseInstaller` passes APKs into Android's `PackageInstaller`. `InstallResultReceiver` handles system callbacks and emits results via `InstallResultEvents`.
- **DI** (`di/AppContainer.kt`): Manual singleton container created in `GitHubUpdaterApplication.onCreate()`.

### Key Data Flow

1. User taps download → `MainViewModel.onPrimaryAction()` → `DownloadService.startDownload()`
2. Service runs `ReleaseInstaller.install()` → `ApkDownloadStore.prepareApk()` → `PackageInstaller` session commit
3. System sends result to `InstallResultReceiver` → emits `InstallResultEvent` → ViewModel clears progress and refreshes status
4. Package change `BroadcastReceiver` in `MainActivity` triggers `refreshLocalStatus()` for real-time install/uninstall updates

### Error Handling

`AppRepository.loadManagedApps()` catches per-app API failures and throws `PartialLoadException` carrying both successfully loaded apps and deduplicated error messages. The ViewModel keeps partial data and shows an error banner.

## Key Conventions

### Code Style

- Minimal comments — only comment genuinely non-obvious logic
- No Copilot co-author trailers in commit messages

### Version Resolution

`AppRepository.resolvedVersionName()` extracts the version from the APK asset filename using `versionRegex` capture group 1. It falls back to the release tag name with the `v` / `V` prefix stripped. Keep this logic aligned across `AppRepository`, `VersionHistoryScreen`, and `AddEditAppScreen`.

### Asset Matching

`apkRegex` is matched against the full asset filename (including `.apk`) using `containsMatchIn`. Only `.apk` files are considered. Leave blank to match any APK. Use `music` to match any APK containing "music"; use `manager\.apk` to match exactly `manager.apk` (excluding `manager-debug.apk`).

### API Calls

`refresh()` uses the 15-minute service cache. `refreshLocalStatus()` uses `useCachedRemoteDataOnly = true` for zero API calls. `refreshNow()` forces fresh API calls. Main-list latest-release refreshes use GitHub GraphQL batching when a token is configured and REST otherwise.

### Storage

- APK downloads go to `Download/GitHubUpdater/` or a user-selected SAF tree URI
- App catalog is stored in SharedPreferences key `"app_catalog"` → `"apps"`
- Settings are stored in SharedPreferences key `"github_updater_preferences"`

### Notifications

Download notifications use channel ID `DownloadService.CHANNEL_ID` (`"download_progress"`). The channel is created in `MainActivity.onCreate()`.
