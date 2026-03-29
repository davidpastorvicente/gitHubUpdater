# updaterManager

`updaterManager` is a small Android app for tracking and installing upstream APK releases outside of Google Play.

Right now it is focused on one GitHub Releases source and shows:
- the latest available app release
- the installed version on the device, if present
- a single action button to install or update
- release history in a separate detail screen
- download progress with reusable APK caching
- a settings page for theme, download location, and APK cleanup

## Adding new apps

App definitions are stored in:

```text
app/src/main/assets/apps.json
```

Each entry can define:
- display name
- Android package name
- GitHub release owner/repo
- optional APK regex without the `.apk` suffix
- optional version regex without the `.apk` suffix to extract the installed app version from the APK filename

So adding a new GitHub-release based app is mostly a data change instead of a Kotlin code change.

## Tech stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin 9
- Gradle version catalog

## Current behavior

- Fetches release data from the GitHub releases API
- Filters assets with the optional `apkRegex` rule in `apps.json`, then enforces the `.apk` suffix internally
- If `apkRegex` is omitted, any `.apk` asset is eligible
- Applies the same internal `.apk` suffix rule to `versionRegex`
- Downloads APKs into `${Environment.getExternalStorageDirectory().path}/Downloads/UpdateManager` by default
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
