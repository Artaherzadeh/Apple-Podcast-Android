# Apple Podcasts Android WebView Wrapper

A modern WebView wrapper that packages the Apple Podcasts web player into a native Android app.

## How to build this app in the cloud (No Git or local tools required)

Since you don't have Git, Android Studio, or Java installed on your computer, you can upload the files directly to GitHub using your web browser:

### Step 1: Open the GitHub Upload Page
1. Open your web browser and go to your repository page:
   `https://github.com/Artaherzadeh/Apple-Podcast-Android`
2. Since it is a new empty repository, you will see a setup screen. Click on the link that says **"uploading an existing file"** (it is in the sentence: *"...or get started by creating a new file or uploading an existing file"*).

### Step 2: Drag and Drop the Project Files
1. Open the Windows File Explorer and navigate to your project folder:
   `C:\Users\Alireza\.gemini\antigravity\scratch\ApplePodcastWrapper`
2. Select all the files and folders inside this directory:
   * `.github` (This contains the automated builder workflow)
   * `app`
   * `.gitignore`
   * `build.gradle.kts`
   * `gradle.properties`
   * `README.md`
   * `settings.gradle.kts`
   
   *(Note: If you do not see the `.github` folder, it may be hidden. In Windows File Explorer, click **View** (or **Layout** at the top) -> **Show** -> check **Hidden items** to reveal it).*
3. Drag all these files and folders together and drop them onto the upload area in your browser.
4. Wait for the files to finish uploading (about 15-20 seconds).
5. Scroll down to the bottom of the page.
6. Click the green **Commit changes** button.

### Step 3: Download your APK
1. Once committed, click on the **Actions** tab at the top of your GitHub repository page.
2. You will see a workflow running named **Build Android APK**.
3. Click on the active run (it will have a yellow spinning icon).
4. Once it finishes (about 1.5 to 2 minutes), scroll down to the **Artifacts** section at the bottom.
5. Click on **apple-podcasts-app** to download the ZIP file.
6. Extract the ZIP file to get your `app-debug.apk`.

### Step 4: Install on your Samsung Galaxy A55
1. Transfer the `app-debug.apk` file to your phone (via USB, Google Drive, email, or a messaging app).
2. Open the **My Files** app on your Samsung phone and find the APK.
3. Tap the file to install it. If prompted, toggle the switch to allow installing from "Unknown Sources" or your file manager.
4. Open the **Podcasts** app on your screen!
