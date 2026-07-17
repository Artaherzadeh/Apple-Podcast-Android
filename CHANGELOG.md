# Changelog

All notable changes to the Apple Podcasts Android WebView Wrapper project are documented in this file.

---

## [1.1] - 2026-07-17

### Added
- **Floating Offline Download Banner:** Displays a floating action card when active audio streams are detected, allowing one-click background downloading via Android's native `DownloadManager`. Saved files are named as `[Podcast Name] - [Episode Title].mp3` in the public Downloads directory.
- **Contextual Menu Download Button:** Injects a native-looking "Download Episode" list item with a matching SVG icon directly into Apple's HTML5 contextual menu.
- **Consistent Signing Configs:** Added a persistent local keystore (`app/debug.keystore`) configuration to support seamless app updates without requiring a full reinstallation first.
- **Dynamic Versioning:** Displays the current app version dynamically at the bottom of the options menu footer.

### Changed
- **Gesture Hold Duration:** Reduced the two-finger hold gesture timeout from `1.0s` to `0.5s` for faster accessibility.
- **Aesthetic Overlay Dimming:** Screen background dim level increased to 75% opacity (`0.75f`) when the bottom options dialog is open.
- **Instructional Typography:** Changed first-run instruction tips to a cleaner, regular font weight (`android:textStyle="normal"`).
- **Options Menu Padding:** Restructured top and bottom padding gaps in the dialog sheet to align with the internal divider spacing.

### Fixed
- **Media Controls Play/Pause:** Integrated standard `MediaButtonReceiver` to ensure that play/pause events from lockscreen players, notification widgets, and Bluetooth headsets sync correctly.
- **Notification Timer Drift:** Set playback speed to `0.0f` when paused to stop the lockscreen countdown progress timer.
- **Swipe-Close Cleanup:** Override `onTaskRemoved` in the service to instantly dismiss the notification player when the app is swiped away from Recents.
- **Page Cache Reload:** Clicking "Clear Cache" now forces the active WebView to reload (`webView.reload()`) immediately.
