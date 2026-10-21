# Recents Switcher (prototype)

Launching the app shows a vertical list of recently used apps over whatever is
on screen. Up/Down moves, OK launches the selected app, Back closes. Launching
the app again while the list is open closes it, so one mapped button toggles it.
The overlay never takes window focus, so whatever is playing underneath keeps
playing.

There is no settings screen. Map any remote button to launch
**Recents Switcher** (package `dev.jzprice.recents`) with your button-remapper
of choice.

## Build

The "Build debug APK" GitHub Action runs on every push to `main` (or run it by
hand from the Actions tab). Download the `recents-switcher-debug-apk` artifact
and unzip it to get `app-debug.apk`.

## Install and set up (adb)

```
adb connect <tv-ip>:<port>
adb install -r app-debug.apk

# 1. Let the app read recent-app history
adb shell appops set dev.jzprice.recents GET_USAGE_STATS allow

# 2. Turn on the accessibility service (it draws the overlay and handles D-pad/OK/Back while open)
adb shell settings get secure enabled_accessibility_services
adb shell settings put secure enabled_accessibility_services dev.jzprice.recents/dev.jzprice.recents.SwitcherService
adb shell settings put secure accessibility_enabled 1
```

If the `get` command prints existing services, join them with colons instead of
overwriting, e.g. `existing.pkg/Svc:dev.jzprice.recents/dev.jzprice.recents.SwitcherService`.

## Test from adb

```
adb shell monkey -p dev.jzprice.recents -c android.intent.category.LAUNCHER 1
```

(`adb shell am start -n dev.jzprice.recents/.TriggerActivity` also works.)
Open a couple of apps first so there is history to show.

## Known limits

- "Recent apps" comes from usage-stats events (last 3 days), not the true list of
  running tasks. Selecting an app brings its existing task forward if the system
  still has it.
- If the service isn't enabled, launching the app only shows a short toast.
- Prototype: untested on the MoGo 3 Pro. Expect to iterate.
