# ModKit CloneKit for Android

ModKit CloneKit is an Android app project designed around the workflow you asked for: browse installed apps, choose one, change its package ID, set a launch orientation, optionally remove its Internet permission, rebuild it, sign it, and save the resulting APK. It also retains per-app profiles and device display/ADB tools.

## What it includes

- Installed-app library with search, app details, launch, and Android App Info shortcuts.
- A clone builder that accepts an installed app's base APK or a separately selected `.apk` file.
- Editable cloned app name and package ID.
- Manifest orientation changes: landscape, reverse landscape, portrait, or unchanged.
- Optional removal of `android.permission.INTERNET`. This blocks ordinary network access; it does **not** add local game data or make a server-based game work offline.
- APK decode/rebuild through the Android-oriented `apktool-android` library, then APK signing using a key kept in this app's Android Keystore.
- A save picker for the rebuilt APK, followed by the Android installer when allowed.
- Saved profiles, recipe export, screen information, ADB display-size commands, and reset commands.

## Build it on your Android phone with AndroidIDE

The APK rebuild library is fetched from its public source repository during setup. The first setup needs an Internet connection and enough free storage/RAM for Gradle and Android SDK components.

1. Download and extract this project ZIP into a folder you can find again.
2. Open that folder in AndroidIDE. Before the first Gradle sync, open its Terminal in the project folder and run:

   ```sh
   sh scripts/setup-apktool.sh
   ```

   This downloads the APK rebuild engine and sets up Gradle 8.9. It does not require root. It does need an Internet connection. If the terminal says `git: not found`, install Git in that terminal environment and rerun the command.
3. Close and reopen the project, allow Gradle to download dependencies, then use **Run / Quick Run** to build and install the debug APK.
4. If Android asks, allow AndroidIDE to install apps. ModKit itself uses the Android file picker for selected APK files, so it does not need broad “all files” access.

If AndroidIDE has trouble building the project on your phone, use the cloud-build route below instead. AndroidIDE's original project is currently marked as unmaintained, so device/Gradle compatibility can vary.

## Cloud build with GitHub Actions

1. Create a repository on GitHub and upload the extracted **project files and folders** (not only this README).
2. Keep `.github/workflows/build-apk.yml` in the repository.
3. Open the repository's **Actions** tab, select **Build ModKit CloneKit APK**, and tap **Run workflow**. The workflow downloads the build dependency, compiles the Android project, and uploads an artifact named `ModKit-CloneKit-debug-apk`.
4. Open that completed run and download the artifact ZIP. Inside it is `app-debug.apk`. On your phone, open the APK and follow Android's install prompt.

The workflow may take a little while the first time because it downloads the Android build tools and dependency source.

## Using the app

1. Open **Apps** and search for the installed app you want to duplicate.
2. Open its details, tap **Build a separate APK clone**, enter a new package ID such as `com.example.game.clone`, and select the orientation.
3. Press **Build and sign clone APK**. When it finishes, choose **Save APK** and follow the Android installer.
4. If you choose **Remove INTERNET permission**, online features will stop working unless the app is designed to function without network access.

## Important limitations

- **Not every APK can be cloned successfully.** Apps installed from Play may use split APKs; this project currently rebuilds the base APK only, so some clones may be incomplete. Apps with anti-tamper checks, signature checks, dynamic delivery, unusual resources, or server-side verification may fail to rebuild or run.
- The new clone has a different package ID and the ModKit signing key, so it cannot update or replace the original app. It is a separate installation.
- Apps can rely on hardcoded package names or signing certificates. Those references are updated where they appear as readable text, but no generic tool can guarantee every app works after package-ID changes.
- An ordinary Android app cannot force an arbitrary installed app's orientation/resolution without modifying the APK or using privileged control. Orientation is patched into the rebuilt APK. Screen-size ADB commands change the **whole device display** until reset, not just one game.
- The file picker is used instead of requesting broad “all files” access. It gives the app access to the APK file you select.
- Rebuilding large games can need substantial free storage and memory. Do not force-stop the app during a rebuild.

## Technical notes

- App package: `com.modkit.studio`
- Minimum Android: API 26 (Android 8.0). Your Android 10 device is within that range.
- The APK build requires Android SDK Platform 35, Build Tools 35.0.0, Java 17, and Gradle 8.9.
- Third-party APK tooling is fetched at setup/build time from [`anirudhmalik/apktool-android`](https://github.com/anirudhmalik/apktool-android). The ModKit project does not contain prebuilt binaries from that repository.

**Build status:** the source project and GitHub Actions workflow are prepared, but the APK has not been compiled or device-tested in this environment because the Android SDK/Gradle toolchain is not installed here and direct package downloads are unavailable. Treat this as an unverified build until AndroidIDE or the workflow finishes successfully.
