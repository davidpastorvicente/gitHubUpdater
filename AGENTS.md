# AGENTS.md

## Scope
- Single-module Android app. Only Gradle project is `:app`.

## Build And Verify
- Primary verification command: `./gradlew :app:assembleDebug`
- Release build: `./gradlew :app:assembleRelease --build-cache`
- Clean debug build when build state looks suspicious: `./gradlew clean :app:assembleDebug`
- There are currently no repo test sources under `app/src/test` or `app/src/androidTest`; default to `:app:assembleDebug` unless you add tests.

## Release Behavior
- `versionName` is derived from the latest git tag via `git describe --tags --abbrev=0`, with a leading `v` stripped.
- `versionCode` is derived from `git rev-list --count HEAD`.
- Release signing is controlled by env vars: `RELEASE_KEYSTORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- If those env vars are absent, release builds fall back to the debug signing config.
- GitHub Actions release workflow only runs on pushed tags matching `v*` and renames the APK to `GitHubUpdater-<version>.apk` before creating the GitHub Release.

## Architecture
- App entrypoint is `GitHubUpdaterApplication`, which creates a manual singleton `AppContainer`.
- Main wiring lives in `di/AppContainer.kt`; there is no DI framework.
- `MainActivity` hosts the Compose UI; `MainViewModel` owns refresh, install, settings, import/export, and history-loading flows.
- `AppRepository` is the integration layer that combines:
  - local catalog from `AppCatalogRepository`
  - local settings from `AppSettingsRepository`
  - installed-app inspection
  - persisted release cache
  - remote GitHub release fetches from `GitHubReleasesService`
- Install flow goes through `DownloadService` -> `ReleaseInstaller` -> `PackageInstaller` -> `InstallResultReceiver` / `InstallResultEvents`.

## Storage And Config
- App catalog is user-managed and stored in SharedPreferences `app_catalog`, key `apps`.
- Settings are stored in SharedPreferences `github_updater_preferences`.
- Do not assume an `assets/apps.json` seed file exists; this repo currently has no `app/src/main/assets` catalog.
- Exported app JSON intentionally strips the internal sequential `id` field. Import reassigns IDs from 1..N.
- `packageName` must remain unique across catalog entries.

## Refresh And Network Semantics
- `refresh()` allows remote GitHub calls and still uses the service cache.
- `refreshNow()` forces fresh remote calls.
- `refreshLocalStatus()` is the no-network path: it calls `loadManagedApps(... useCachedRemoteDataOnly = true)`.
- Startup behavior depends on the persisted `refreshOnStart` setting in `MainViewModel.init`: enabled -> `refresh()`, disabled -> `refreshLocalStatus()`.
- `GitHubReleasesService` uses raw `HttpURLConnection`, not Retrofit/OkHttp.
- REST/GraphQL responses are cached in memory for 15 minutes inside `GitHubReleasesService`.
- Main-list batching uses GitHub GraphQL only when a token is configured; otherwise it falls back to per-app REST requests.
- Apps with `releaseRegex` fetch more releases (`perPage = 30`); otherwise the main list fetches only the latest release.

## Matching And Version Rules
- `releaseRegex` is matched against the GitHub release `tagName`, not the display title.
- `apkRegex` is matched with `Regex(...).containsMatchIn(asset.name)` and only `.apk` assets are eligible.
- `versionRegexTarget` explicitly chooses whether `versionRegex` runs against the APK filename or the release tag.
- `resolvedVersionName()` falls back to extracting a semver-like value from `tagName`, then `name`, then strips a leading `v`/`V` from `tagName`.
- If you change release/version matching, keep `AddEditAppScreen`, `ReleaseMatching.kt`, and `AppRepository` aligned.

## UI And Feature Gotchas
- Changelog rendering uses `compose-richtext` markdown in `VersionHistoryScreen`, not a WebView.
- `MainViewModel` currently reuses `errorMessage` for both actual errors and transient download-source messages (`Downloading from local mirror` / `Downloading from GitHub`). Be careful not to break that flow accidentally.
- The manifest has `android:usesCleartextTraffic="true"` because the local mirror feature supports plain `http://` LAN endpoints.

## Pi Mirror
- Local mirror support is part of the repo under `pi-mirror/`.
- Mirror URL format must stay `{mirrorBaseUrl}/{owner}/{repo}/{tagName}/{filename}` to match both the app downloader and `pi-mirror/README.md`.
