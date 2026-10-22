# Live TV

A very simple IPTV player for Android TV / Google TV. Give it your M3U playlist URL, and it opens on a grid of your favorite channels.

## What it does

- **Load a playlist:** Settings → paste your M3U URL → *Save & load*. The playlist is saved on the device, so the app opens offline, and it re-downloads in the background when the saved copy is over 24 hours old.
- **Browse:** categories on the left (Favorites, All channels, then each group from your playlist) and a channel grid on the right. Search filters across all channels.
- **Favorites:** long-press OK on a channel to add or remove it (a ★ appears). The app launches on Favorites; with no favorites yet it opens on All channels.
- **Watch:** OK plays the channel. While playing, Up/Down (or Channel +/-) changes channel within the list you came from, OK shows the channel name, long-press OK toggles the favorite, and Back returns to the grid.
- **Back button:** from a category or search, Back returns to Favorites; from Favorites it exits.
- **Menu / Settings key:** opens Settings from anywhere on the grid.

## Build the APK

I couldn't build this on the machine I wrote it on, so the first build is the real test. If it fails, send me the error text.

**Option A: GitHub Actions (no Android Studio needed)**
1. Create a **private** GitHub repository.
2. From this folder: `git init -b main`, `git add .`, `git commit -m "Live TV"`, `git remote add origin https://github.com/<your-username>/<repo>.git`, `git push -u origin main`.
3. Open the repository's *Actions* tab, wait for *Build APK* to finish (a few minutes), and download the `live-tv-debug-apk` artifact. Unzip it to get `app-debug.apk`.

**Option B: Android Studio**
1. Install Android Studio, choose *Open*, and select this folder. Let Gradle sync.
2. *Build → Build APK(s)*. The file is `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the projector

```
adb connect <ip>:<port>
adb install -r app-debug.apk
```
If adb says "more than one device", add `-s <ip>:<port>` after `adb`. The app appears as **Live TV** in your launcher.

## Notes

- **Playlist URL privacy:** it is stored only in the app's private storage, and app backups are turned off so it never goes to cloud backup. It is not in the source code, so keep it that way if you share the repo.
- **User-Agent:** defaults to `VLC/3.0.20 LibVLC/3.0.20`, which most providers accept. Change it in Settings if a provider rejects the playlist or streams (HTTP 401/403).
- **Favorites are matched by `tvg-id` plus channel name.** If your provider renames a channel, its favorite may drop out of sync; just favorite it again. Stream URLs and logos in your favorites update automatically on each playlist refresh.
- **Streams:** plays HLS (.m3u8) and MPEG-TS. If a URL has no file extension and fails as a transport stream, the player retries it once as HLS. Failed streams retry twice before showing an error.
- **Not included (by design):** no program guide (EPG), no reordering of favorites, no recording.

## Project layout

- `M3uParser.kt`: playlist parser (pure Kotlin).
- `Repo.kt`: download, cache and in-memory channel list.
- `Favorites.kt`, `Prefs.kt`: local storage.
- `MainActivity.kt`: categories, grid, search, Settings dialog.
- `PlayerActivity.kt`: full-screen player (Media3 / ExoPlayer).
