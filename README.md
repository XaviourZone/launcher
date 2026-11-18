# Focus Launcher

A minimal Android home screen for staying focused. Only the apps you pin are in reach; everything else stays quiet.

**Download:** https://github.com/XaviourZone/launcher/releases/latest/download/FocusLauncher.apk

## Features
- **Home screen launcher**: clock, your pinned apps, widgets, and your own wallpaper. Set it as the default Home app from Settings.
- **Side tab over every app**: a thin translucent tab on the screen edge, like the Play Games tab. Swipe or tap it to slide out your pinned apps, then tap an icon or drag it out to open the app. Long-press and drag the tab to move it. You can pick the left or right edge, its size and how visible it is.
- **Focus mode**: removes notifications from apps you haven't pinned. Calls, alarms, ongoing notifications and an "also allow" list (bank OTPs, for example) always get through.
- **Closes background apps**: when you open a pinned app, Focus Launcher asks Android to close idle background apps that aren't pinned. There's also a "Clean now" button.
- **Widgets**: add any app's widgets (ChatGPT, Calendar…) with **+ Widget**, then use **Edit** to resize or remove them.
- **Wallpaper**: pick any image for the home screen, the lock screen or both.

## First-time setup (Vivo / OriginOS)
1. Install the APK. If asked, allow installing from your browser or file manager.
2. Open **Focus Launcher → Settings** and tap **Allow** on each item in *Setup*.
3. On Vivo, also turn on **Autostart** and **High background power use** for Focus Launcher (the **Open** button goes there). Otherwise the system may kill the side tab.
4. Choose your pinned apps.

## Android limits worth knowing
- Android lets a launcher close only *idle* background apps. Apps playing music, on a call or running their own foreground service can't be force-stopped by any normal app.
- Focus mode removes a notification right after it arrives, so you may occasionally feel a vibration first. For complete silence, combine it with Vivo's Do Not Disturb.

## Build
GitHub Actions builds `FocusLauncher.apk` on every push to `main` and publishes it as a release. To build locally, run `./gradlew assembleRelease`.
