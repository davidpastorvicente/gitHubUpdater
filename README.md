[![Release APK](https://github.com/davidpastorvicente/gitHubUpdater/actions/workflows/release-apk.yml/badge.svg)](https://github.com/davidpastorvicente/gitHubUpdater/actions/workflows/release-apk.yml)

<h1><img src="app/src/main/ic_launcher-playstore.png" alt="App icon" height="48" valign="middle"> GitHub Updater</h1>

An Android app for tracking and installing upstream APK releases outside of Google Play.

## Features

- **Latest release tracking** — shows the newest available version for each configured app
- **Installed version detection** — shows the currently installed version on the device when present
- **Install / update flow** — keeps the main action focused on install or update, with uninstall support
- **Version history** — opens a separate detail screen with release history and changelog viewing
- **Download progress and APK reuse** — caches valid downloads to avoid re-downloading the same APK
- **Settings and config management** — supports theme, download location, APK cleanup, and config import/export

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + manual DI |
| Networking | `HttpURLConnection` + GitHub REST / GraphQL |
| Storage | SharedPreferences |
| Async | Kotlin Coroutines + Flow |
| Serialization | kotlinx.serialization |

## Requirements

- Android **10+** (API 29)
- Target SDK **36**
- JDK **17**

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

## Getting Started

Clone the repo and open it in Android Studio. See [`.github/copilot-instructions.md`](.github/copilot-instructions.md) for architecture details, build commands, and project conventions.

## Build

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (requires signing env vars)
./gradlew :app:assembleRelease --build-cache
```

Debug output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Usage

### Managing apps

Apps are configured directly inside the app. Tap the **+** button on the main screen to add a new app configuration.

Each entry can define:
- display name
- Android package name
- GitHub release owner/repo
- optional APK regex without the `.apk` suffix
- optional version regex without the `.apk` suffix to extract the installed app version from the APK filename

You can **Test** a configuration before saving to verify it fetches releases and matches APK assets.

To edit or delete an existing app, open its version history and tap the **edit** icon.

### Import / Export

From Settings, you can import or export your app list as a JSON file. The JSON format is:

```json
[
  {
    "displayName": "Twitter",
    "packageName": "com.twitter.android",
    "releaseOwner": "crimera",
    "releaseRepo": "twitter-apk",
    "apkRegex": "^twitter-piko-v.*"
  }
]
```

## CI

Every push that runs the release workflow builds an APK through GitHub Actions and publishes it from `.github/workflows/release-apk.yml`.

## Notes

- Fetches release data from the GitHub releases API, with token-backed GraphQL batching for the main list when configured
- Filters assets with the optional `apkRegex` rule, then enforces the `.apk` suffix internally
- Applies the same internal `.apk` suffix rule to `versionRegex`
- Downloads APKs into `Download/GitHubUpdater/` by default and can switch to a custom folder from Settings
- Deletes installed APKs automatically by default, with a Settings toggle to keep them
- Hands installation off to the Android package installer with user confirmation
