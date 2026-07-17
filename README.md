# Apple Podcasts for Android

A lightweight, secure, and open-source application that lets you enjoy Apple Podcasts directly on your Android phone. 

This project bridges the gap for Android users, running the official Apple player in a polished fullscreen layout with background playback, native lock screen controls, and standard mobile gestures.

---

## ✨ Features

*   **📺 Immersive Fullscreen:** Automatically fits your phone's screen layout so menus and page text sit comfortably below status bar icons and camera notches.
*   **🚀 Seamless Splash Launch:** Starts up instantly with a smooth, dark launch animation and brand logo display, avoiding any white screen flashes on modern Android versions.
*   **🎵 Lock Screen Player Controls:** Integrates natively with your phone's notification panel and lock screen. Displays your currently playing episode name, creator, and cover artwork, alongside standard Play, Pause, and Seek timeline controls. The timeline timer stops automatically when paused.
*   **📥 Offline Episode Downloads:** Detects active audio streams and pops up a floating download banner. Tap it to download the audio file directly to your phone's public Downloads directory using Android's native `DownloadManager`. Files are automatically named as `[Podcast Name] - [Episode Title].mp3`.
*   **✌️ Quick Controls Menu:** Hold two fingers on the screen for **0.5 seconds** to slide up a helpful options sheet (dimming the background by 75% for legibility):
    *   **Home:** Jump directly back to the Apple Podcasts homepage.
    *   **Go Back:** Go back to the previous screen.
    *   **Force Refresh:** Reload the current screen if it gets stuck.
    *   **Clear Cache:** Free up phone storage space and resets first-run gesture tips.
    *   **Background Playback Settings:** A quick link to resolve system battery limitations for continuous audio.
    *   **GitHub Project:** Open this page to check the source or contribute.
    *   *Also displays current version name in the footer.*
*   **📡 Safe Connection Warning:** Displays a friendly warning screen if your mobile data or Wi-Fi disconnects, with an easy "Try Again" recovery button.
*   **🔒 Safe Navigation:** Links to external sites inside episode descriptions automatically open in safe external tabs, keeping your main listening app secure.

---

## 🔒 Security & Privacy

Privacy is key:
1.  **Direct Connection:** The app connects directly to the official, secure Apple servers (`https://podcasts.apple.com/`).
2.  **No Interception:** The application has no tracking scripts, custom analytics, or ad servers. Session details are stored strictly on your device via standard cookies and local web storage to keep you logged in.
3.  **Encrypted Login:** Login details are managed entirely by the standard, Google-maintained Android System WebView. Keeping your phone's WebView updated via the Play Store ensures maximum security.

---

## 📲 How to Install

You can download the latest version directly to your phone:

1.  Go to the **[Releases](https://github.com/Artaherzadeh/Apple-Podcast-Android/releases)** page on the right-hand sidebar.
2.  Download the latest APK installer file.
3.  Transfer the downloaded file to your phone if downloaded on a PC.
4.  Open your phone's **Files** app, find the APK, and tap it.
5.  If prompted, grant permission to install from "Unknown Sources," then select **Install**.

> [!NOTE]
> **Signature Preservation:** Every build is signed with a consistent debug signature key. This allows you to install future updates over older builds seamlessly without needing to uninstall the application first.

---

## 🤝 Suggestions & Feedback

If you have suggestions, run into bugs, want to propose new features, or contribute to this app:
*   **Report Issues / Request Features:** Please open a ticket on our **[Issues Page](https://github.com/Artaherzadeh/Apple-Podcast-Android/issues)**.
*   **Contribute Code:** Feel free to create a branch and submit a **Pull Request**. We welcome contributions to improve layout compatibility and gesture controls!
