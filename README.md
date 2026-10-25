# TrickBD Android WebView App

A minimal native Android wrapper for **https://www.trickbd.org/** with persistent WebView storage, custom offline/error UI, HTTPS/domain navigation rules, Android Back handling, and an optional GitHub Releases update checker.

## Repository status / configuration

- Website: `https://www.trickbd.org/`
- Intended GitHub update repository: `https://github.com/rironib/trickbd`
- The GitHub repository could not be verified as publicly accessible during preparation (the public URL returned 404). The update checker is configured to `rironib/trickbd`; verify that repository exists and publishes releases with an APK asset before shipping. If the actual owner/repository differs, update `GITHUB_OWNER` and `GITHUB_REPO` in `MainActivity.kt`.
- Package ID: `org.trickbd.app` (change before first production release if you need another application ID).
- Version: `1.0.0` / versionCode `1`.

## Features

- Native Android WebView; no third-party runtime libraries.
- JavaScript and DOM Storage enabled for site functionality and LocalStorage.
- Cookies accepted; third-party cookies disabled by default.
- HTTPS-only WebView navigation and allowlist for `trickbd.org` / `www.trickbd.org`.
- External HTTPS links open in the system browser; `tel:`, `mailto:`, `sms:` use Android handlers.
- Custom offline/network/server/SSL error UI with retry.
- Main-frame filtering for network errors; SSL errors are always cancelled.
- Android Back history and exit confirmation.
- GitHub Releases API update checker, semantic numeric version comparison, release notes, and APK asset detection.
- Silent update-check failure; no update server credentials or GitHub token required for public repos.
- R8 minification and resource shrinking enabled for release builds.

## Build

Requirements: Android Studio (current stable), JDK 17, Android SDK Platform 35, Android Gradle Plugin 8.10.1 compatible Gradle distribution.

1. Open this directory in Android Studio.
2. Allow Gradle sync and install any requested SDK components.
3. Run the `app` configuration on an emulator/device.
4. For a release APK, choose **Build > Generate Signed Bundle / APK > APK**, create/use your own signing key, and build `release`.
5. Test the signed APK on a real device before publishing.

The Gradle wrapper files are not included in this source bundle; Android Studio can sync using its configured Gradle installation, or generate a wrapper from a compatible Gradle install with `gradle wrapper`.

## Publishing a GitHub release for in-app updates

1. Increase both `versionCode` and `versionName` in `app/build.gradle.kts` for each app release.
2. Build and sign the APK with the same signing key used for previous releases. Android updates require matching package ID and signing certificate.
3. Create and publish a GitHub Release in the configured public repository, using a tag like `v1.0.1`.
4. Attach one installable `.apk` asset to the release. The updater selects the first `.apk` asset from the latest non-draft, non-prerelease release.
5. Verify `https://api.github.com/repos/rironib/trickbd/releases/latest` returns the release and its `assets[].browser_download_url`.

The app opens the APK URL in the system browser/download handler; it does not silently install APKs. Android will require the user to authorize installation from that source where applicable. For a polished download-and-install flow, implement `DownloadManager`, checksum verification, `FileProvider`, and Android package-installer permission handling as a separate tested enhancement.

## Important limitations to verify

- The site may use authentication, redirects, uploads, camera, location, popups, or third-party domains. This starter intentionally blocks unapproved hosts and does not request camera/location permissions. Add only the domains and permission flows the site actually needs.
- The site URL is not secret; WebView must connect to it. The app avoids showing the URL in its custom error UI but cannot make the destination technically undiscoverable.
- `ACCESS_NETWORK_STATE` checks validated connectivity but does not guarantee the website server is healthy.
- GitHub update checking occurs on app launch/resume, no more often than every six hours. It does not notify users while the app is fully closed.
- The release dialog suppresses repeated prompts for the same tag after the user dismisses it. A new tag can prompt again.
- The updater currently trusts GitHub's HTTPS transport and repository release assets; it does not verify APK SHA-256 or the APK signing certificate before opening the download URL.
- The app's installed APK size cannot be guaranteed to stay below 500 KB. Measure the signed release APK; target SDK, resources, and Android build tooling affect size.

## Security notes

- Never disable SSL certificate validation.
- Never embed secrets or GitHub tokens in the APK.
- Keep the APK signing keystore and passwords out of Git.
- Review the domain allowlist and site terms/privacy policy before publishing.
- Test release updates from version N to N+1 using the same application ID and signing key.

## Files

- `app/src/main/java/org/trickbd/app/MainActivity.kt` — WebView, errors, navigation, and update checker.
- `app/src/main/AndroidManifest.xml` — permissions and activity configuration.
- `app/build.gradle.kts` — Android build, app ID and version.
- `docs/RELEASE_CHECKLIST.md` — release QA checklist.
- `docs/APP_SPECIFICATION.md` — requirements and implementation decisions.
