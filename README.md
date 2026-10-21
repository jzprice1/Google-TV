# Recents Switcher (prototype)

Press one remote button anywhere on Google TV and a vertical list of recently
used apps slides in over the current screen. Up/Down moves, OK launches, Back
closes. The overlay never takes window focus, so whatever is playing underneath
keeps playing.

## Build

Push this folder to a GitHub repo (the workflow is at `.github/workflows/build-apk.yml`
and expects the project at the repo root). The "Build debug APK" action runs on
push, or run it by hand from the Actions tab. Download the
`recents-switcher-debug-apk` artifact and unzip it to get `app-debug.apk`.

Local alternative: open the folder in Android Studio and run `assembleDebug`.

## Install and set up (adb)

```
adb connect <tv-ip>:<port>
adb install -r app-debug.apk

# 1. Let the app read recent-app history
adb shell appops set dev.jzprice.recents GET_USAGE_STATS allow

# 2. Turn on the accessibility service (it sees the trigger button and draws the overlay)
adb shell settings get secure enabled_accessibility_services
adb shell settings put secure enabled_accessibility_services dev.jzprice.recents/dev.jzprice.recents.SwitcherService
adb shell settings put secure accessibility_enabled 1
```

If the `get` command prints existing services, join them with colons instead of
overwriting, e.g. `existing.pkg/Svc:dev.jzprice.recents/dev.jzprice.recents.SwitcherService`.

## Test without picking a button

```
adb shell am start -n dev.jzprice.recents/.TriggerActivity
```

Open a couple of apps first so there is history to show.

## Pick the trigger button

Open "Recents Switcher" from your launcher and press the remote button you want.
It shows the keycode it captured. Buttons the system handles itself (often Home
and Assistant) never reach the app. To see what a button sends:

```
adb logcat -s RecentsSwitcher
```

If no free button exists, use a button-remapper app that can launch an activity
and point it at `dev.jzprice.recents/.TriggerActivity`.

## Known limits

- "Recent apps" comes from usage-stats events (last 3 days), not the true list of
  running tasks. Selecting an app brings its existing task forward if the system
  still has it.
- Prototype: untested on the MoGo 3 Pro. Expect to iterate.
