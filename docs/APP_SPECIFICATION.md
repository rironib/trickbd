# TrickBD Android App — Specification

## Goal
Provide a lightweight Android app shell for `https://www.trickbd.org/`, preserving the website's own account/content functionality and adding native navigation, network recovery, and GitHub release update discovery.

## Scope (v1)
- Native WebView as the main surface.
- JavaScript + DOM Storage for the modern website.
- Persistent cookies; third-party cookies off by default.
- HTTPS-only, host allowlist, safe handling of external links.
- Custom local offline/error screen and retry.
- Android Back navigation.
- GitHub Releases latest-release polling on launch/resume (6-hour local interval).
- Update dialog includes release title, version, notes, and APK/release link.
- Release minification/resource shrinking.

## Explicit non-goals for v1
- No silent APK installation.
- No background notification while the app is closed.
- No camera, microphone, geolocation, file upload, or file download permissions unless a verified website requirement is identified.
- No bundled offline copy of the website or user content.
- No embedded GitHub credentials.

## Update API contract
`GET https://api.github.com/repos/{owner}/{repo}/releases/latest`

Required fields: `tag_name`, `draft`, `prerelease`, `html_url`, `body`, and `assets[]` with `name` and `browser_download_url`.

A release is considered only when not draft/prerelease and its numeric semantic version is greater than `BuildConfig.VERSION_NAME`. An APK asset ending in `.apk` is required for the direct download action. If none exists, the app opens the release page instead.

## Acceptance criteria
- App opens the configured website on a clean install with internet access.
- LocalStorage/cookies persist across app restarts where the site uses them.
- Offline startup shows a local retry screen without exposing raw URL/stack trace.
- SSL errors are cancelled and never bypassed.
- Only the configured website hosts load inside WebView.
- Back navigates website history before offering exit.
- GitHub API failure never crashes or blocks the website.
- Same version does not prompt; newer published release prompts once per tag.
- APK updates install only when package ID and signing certificate match the installed app.
