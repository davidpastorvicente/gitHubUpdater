# AGENTS.md

## Scope
- Single-module Android app. Only Gradle project is `:app`.

## Verify
- Default verification: `./gradlew :app:assembleDebug`
- Release build: `./gradlew :app:assembleRelease --build-cache`
- If Gradle/build state looks stale: `./gradlew clean :app:assembleDebug`
- There are currently no repo tests under `app/src/test` or `app/src/androidTest`; unless you add tests, `:app:assembleDebug` is the meaningful verification step.

## Release
- `versionName` comes from `git describe --tags --abbrev=0` with leading `v` stripped.
- `versionCode` comes from `git rev-list --count HEAD`.
- Release signing uses `RELEASE_KEYSTORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- If those env vars are missing, release builds fall back to the debug signing config.
- The GitHub Actions release workflow only runs on pushed tags matching `v*` and renames the APK to `GitHubUpdater-<version>.apk` before publishing.

## Architecture
- App entrypoint is `GitHubUpdaterApplication`, which creates the manual singleton `AppContainer` in `di/AppContainer.kt`.
- `MainActivity` hosts the Compose UI; `MainViewModel` owns refresh, install, settings, import/export, and history loading.
- `MainScreen` has two distinct flows: compact phone navigation and expanded split-pane layout. Preserve both when changing navigation or screen structure.
- `AppRepository` merges catalog, settings, installed-app state, persisted release cache, and remote GitHub release data.
- Install flow is `DownloadService` -> `ReleaseInstaller` -> `PackageInstaller` -> `InstallResultReceiver` / `InstallResultEvents`.
- `DownloadService` is a foreground service and holds wake/Wi-Fi locks while downloads are active.
- Real-time package add/replace/remove handling lives in a `BroadcastReceiver` inside `MainActivity`, which calls `refreshLocalStatus()`.
- `AppRepository.loadManagedApps()` can throw `PartialLoadException` with partial app data plus deduplicated per-app errors; UI should keep partial results visible.
- Version history is loaded lazily through `AppRepository.loadHistory()`; do not assume the main list has full release history.

## Storage
- App catalog is user-managed and stored in SharedPreferences `app_catalog`, key `apps`.
- Settings are stored in SharedPreferences `github_updater_preferences`.
- Do not assume an `assets/apps.json` seed file exists; there is no `app/src/main/assets` catalog in this repo.
- Exported app JSON intentionally strips the internal sequential `id` field. Import reassigns IDs from `1..N`.
- `packageName` must stay unique across catalog entries.
- Default APK download location is `Downloads/GitHubUpdater`, or a user-selected SAF tree URI.

## Refresh And Network
- `refresh()` allows remote GitHub calls and still uses the service cache.
- `refreshNow()` forces fresh remote calls.
- `refreshLocalStatus()` is the no-network path: it calls `loadManagedApps(... useCachedRemoteDataOnly = true)`.
- Startup behavior depends on persisted `refreshOnStart`: enabled -> `refresh()`, disabled -> `refreshLocalStatus()`.
- `GitHubReleasesService` uses raw `HttpURLConnection`, not Retrofit/OkHttp.
- REST/GraphQL responses are cached in memory for 15 minutes inside `GitHubReleasesService`.
- Main-list batching uses GitHub GraphQL only when a token is configured; otherwise it falls back to per-app REST requests.
- Apps with `releaseRegex` fetch more releases (`perPage = 30`); otherwise the main list fetches only the latest release.

## Matching Rules
- `releaseRegex` is matched against GitHub release `tagName`, not the display title.
- `apkRegex` is matched with `Regex(...).containsMatchIn(asset.name)`, and only `.apk` assets are eligible.
- `versionRegexTarget` chooses whether `versionRegex` runs against the APK filename or the release tag.
- `resolvedVersionName()` falls back to extracting a semver-like value from `tagName`, then `name`, then strips a leading `v`/`V` from `tagName`.
- If you change release/version matching, keep `AddEditAppScreen`, `ReleaseMatching.kt`, and `AppRepository` aligned.

## UI Gotchas
- Changelog rendering uses `compose-richtext` markdown in `VersionHistoryScreen`, not a WebView.
- `MainViewModel` currently reuses `errorMessage` for both real errors and transient download-source messages (`Downloading from local mirror` / `Downloading from GitHub`).
- Download notifications use `DownloadService.CHANNEL_ID` (`download_progress`), and the channel is created in `MainActivity.onCreate()`.
- The manifest enables `android:usesCleartextTraffic="true"` because the local mirror feature supports plain `http://` LAN endpoints.

## Pi Mirror
- Local mirror support lives under `pi-mirror/`.
- Mirror URL format must stay `{mirrorBaseUrl}/{owner}/{repo}/{tagName}/{filename}` to match both the app downloader and `pi-mirror/README.md`.
