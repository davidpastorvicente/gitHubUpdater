# gitHubUpdater

`gitHubUpdater` is a small Android app for tracking and installing upstream APK releases outside of Google Play.

Features:
- the latest available app release
- the installed version on the device, if present
- a single action button to install or update
- release history in a separate detail screen
- download progress with reusable APK caching
- a settings page for theme, download location, APK cleanup, and config import/export

## Managing apps

Apps are configured directly inside the app. Tap the **+** button on the main screen to add a new app configuration.

Each entry can define:
- display name
- Android package name
- GitHub release owner/repo
- optional APK regex without the `.apk` suffix
- optional version regex without the `.apk` suffix to extract the installed app version from the APK filename

You can **Test** a configuration before saving to verify it fetches releases and matches APK assets.

To edit or delete an existing app, open its version history and tap the **edit** icon.

## Import / Export

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

## Tech stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin 9
- Gradle version catalog

## Current behavior

- Fetches release data from the GitHub releases API
- Filters assets with the optional `apkRegex` rule, then enforces the `.apk` suffix internally
- If `apkRegex` is omitted, any `.apk` asset is eligible
- Applies the same internal `.apk` suffix rule to `versionRegex`
- Downloads APKs into `Downloads/UpdateManager` by default
- Lets you switch to a custom folder from Settings
- Deletes installed APKs automatically by default, with a Settings toggle to keep them
- Reuses a previously downloaded APK when it is still valid
- Hands installation off to the Android package installer with user confirmation

## Build

From the project root:

```bash
./gradlew :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- This project currently uses a sideload installer flow and is intended for non-Play distribution.
- `local.properties` is intentionally ignored because it contains machine-specific Android SDK paths.
