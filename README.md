# Apple Podcasts Android

A lightweight, secure, and open-source Android container that wraps the official Apple Podcasts web player into a native-feeling application. 

Since Apple does not provide an official Apple Podcasts app for Android, this project bridges that gap by running the web player in a highly optimized, full-screen Android WebView with background playback capabilities, offline handling, and custom gestures.

---

## ✨ Features

*   **📺 Native Fullscreen Experience:** Completely hides browser URL bars and navigation tabs. Integrated with Android's system bar constraints (`fitsSystemWindows`) so page headers and sign-in buttons never overlap with your status bar icons or front-camera notches.
*   **🎵 Background Playback Optimization:** Configured to prevent Android from pausing the app's web core when minimized or when the screen is locked, allowing uninterrupted podcast streaming.
*   **✌️ Custom Multi-Touch Menu:** Long-press with **two fingers** for 1 second anywhere on the screen to pull up a premium dark-themed options menu:
    *   **Go Back:** Navigate to the previous page in web history.
    *   **Force Refresh:** Force reload the current screen.
    *   **Clear Cache:** Clear heavy image and metadata caches to free up storage, *without* logging you out of your Apple ID.
    *   **Background Playback Setup:** Quick shortcut to disable Android's system battery limitations for continuous background audio.
*   **📡 Custom Offline Handler:** Replaces the generic web "disconnected" error with a premium, Apple-themed offline layout featuring network warning graphics and a manual "Try Again" recovery button. Automatically detects when connectivity is restored and reloads the page.
*   **🔒 Secure Link Routing:** Prevents external links (such as social share buttons or external advertisers) from hijacking the container. Non-Apple links automatically launch in your phone's default system browser.

---

## 🔒 Security & Privacy

Privacy is a core consideration of this project. If you plan to log in with your **Apple ID** to access your personal library and subscriptions, rest assured that this app is fully secure:
1.  **Direct HTTPS Connection:** The application loads directly from the official Apple domain `https://podcasts.apple.com/` using secure TLS/SSL encryption.
2.  **Zero Interception:** The app is a simple presentation container. The code contains **no trackers, analytics, or interception scripts**. Your password and Apple ID credentials go directly to Apple's authentication servers.
3.  **Local Storage only:** Session details (login tokens) are stored directly in secure cookies and standard HTML5 LocalStorage, identical to how Google Chrome or Safari saves sessions.

---

## 📲 How to Install

You can download the pre-compiled installer directly from this repository:

1.  Go to the **[Releases](https://github.com/Artaherzadeh/Apple-Podcast-Android/releases/tag/latest)** section on the right-hand sidebar of this page.
2.  Download the **`Apple Podcasts.apk`** file.
3.  Transfer the `.apk` file to your phone (via USB, Google Drive, email, or a messaging app).
4.  Open your phone's **Files** app, locate the downloaded file, and tap it.
5.  If prompted, grant permission to install from "Unknown Sources", then tap **Install**.

---

## 🤝 Contributing & Feedback

If you have suggestions, run into bugs, want to propose new features, or contribute to the codebase:

*   **Report Issues / Request Features:** Please open a ticket on our **[Issues Page](https://github.com/Artaherzadeh/Apple-Podcast-Android/issues)** describing the problem or suggestion.
*   **Contribute Code:** Feel free to create a branch, implement your changes, and submit a **Pull Request**. We welcome contributions to improve the layout, media optimizations, or gesture controls!
