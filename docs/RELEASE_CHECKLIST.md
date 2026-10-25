# TrickBD Android Release Checklist

## Before build
- [ ] Confirm the public repository `rironib/trickbd` exists and the Releases API is accessible.
- [ ] Confirm `www.trickbd.org` is the canonical website and redirects behave as expected.
- [ ] Review the allowed hosts and add only required trusted domains.
- [ ] Set final `applicationId`, app name, icon, versionName and incremented versionCode.
- [ ] Confirm privacy policy/contact links and rights to publish the TrickBD brand/assets.
- [ ] Check whether the site requires file chooser, camera, location, popup, or download support; add only what is needed.

## Build and test
- [ ] Gradle sync and release build succeed with JDK 17 and SDK 35.
- [ ] Test Android minimum supported API and a current Android release.
- [ ] Test first launch, normal navigation, search, login/session, forms, and theme.
- [ ] Test Back button with history and on home screen.
- [ ] Test airplane mode on launch and while browsing; retry after reconnect.
- [ ] Test DNS failure, server 5xx, HTTP 404, and SSL error behavior.
- [ ] Test external HTTPS links and `tel:`/`mailto:` handlers.
- [ ] Test update API with no update, a newer release, API rate limit/offline, draft, and prerelease.
- [ ] Test update installation from vN to vN+1 using the same package ID and signing key.
- [ ] Inspect signed APK size and run Android lint/security checks.

## Publish
- [ ] Keep signing keystore and passwords in secure storage, never Git.
- [ ] Increment `versionCode` and `versionName`.
- [ ] Create a tag such as `v1.0.1` and publish a non-draft, non-prerelease GitHub Release.
- [ ] Attach one signed APK with a clear `.apk` filename.
- [ ] Confirm the `releases/latest` API response has the expected tag and asset URL.
- [ ] Test download URL and update flow on a real device before announcing the release.
