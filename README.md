# Photo Dream

An Aerial Dream-style screensaver and slideshow for **your own photos and videos**.
Works on Android phones, tablets and Android TV / Google TV (Android 8.0 or newer).

**What it does**
- Cross-fading photo slideshow with a slow zoom ("Ken Burns") effect
- Plays videos (MP4, MKV, WebM, MOV, 3GP), muted by default
- Clock and date overlay, like Aerial Dream
- Shows where each photo or video was taken (e.g. "📍 Leeds, United Kingdom") when the file
  has GPS data; nothing is shown when it doesn't
- Reads a folder and all its sub-folders, or searches the whole media library
- Runs as a proper Android screensaver, or on demand with "Start slideshow now"
- Remote control: Right = next, Left = previous, OK = pause, Back = exit

Photos: JPG, PNG, WebP, GIF (first frame), BMP, HEIC (Android 9+).

---

## 1. Build the app (one-off, about 30 minutes)

1. On your PC, install **Android Studio** (free): https://developer.android.com/studio
   Accept the default options, including the Android SDK.
2. Unzip this project somewhere simple, e.g. `C:\Projects\PhotoDream`.
3. In Android Studio: **File > Open** and pick the `PhotoDream` folder.
4. Wait while it syncs (bottom status bar). The first sync downloads Gradle and the
   Android SDK pieces, which can take 5–15 minutes.
   - If it offers to upgrade the Android Gradle Plugin, it's safe to accept.
5. **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
6. When it finishes, click **locate** in the pop-up. The file is
   `app\build\outputs\apk\debug\app-debug.apk`.

## 2. Install it

### On an Android phone or tablet
- **Easiest:** turn on Developer options (Settings > About phone > tap *Build number* 7 times),
  then turn on **USB debugging**. Plug the phone into the PC and press the green ▶ **Run**
  button in Android Studio.
- **Or:** copy `app-debug.apk` to the phone (USB, Google Drive, email), tap it, and allow
  "Install unknown apps" when asked.

### On Android TV / Google TV (e.g. Chromecast with Google TV, Sony/TCL/Hisense TVs, Nvidia Shield)
1. On the TV: **Settings > System > About**, scroll to **Android TV OS build**, press OK 7 times
   to enable Developer options.
2. **Settings > System > Developer options**: turn on **USB debugging** (some models call it
   *Network debugging* or *ADB debugging*).
3. Note the TV's IP address: **Settings > Network & Internet > (your Wi-Fi)**.
4. On the PC, open a terminal. ADB comes with Android Studio at
   `%LOCALAPPDATA%\Android\Sdk\platform-tools` (Windows). Run:
   ```
   adb connect 192.168.1.50        (use your TV's IP)
   adb install app-debug.apk
   ```
   Accept the "Allow USB debugging?" prompt on the TV the first time.
   (Or in Android Studio the TV appears in the device list after `adb connect`; press ▶ Run.)

## 3. Put your photos somewhere

- **Phone/tablet:** make a folder such as `Pictures/Slideshow` and copy photos/videos into it.
- **TV:** a USB stick plugged into the TV works well. Copy a folder of photos onto it first.
  Videos should be ones the TV can play normally (H.264/H.265 MP4 is safest).

## 4. Set it up

Open **Photo Dream** from your app list (on TV it's in the Apps row).

1. Under *Where are your photos?* choose **Pick a folder**, press **Choose folder…**,
   browse to your folder and press **Use this folder > Allow**.
   - If your TV says it has no folder picker, choose **Search the media library** instead and
     type part of the folder name (e.g. `Slideshow`), or leave it blank to use everything.
     Allow the photos/videos permission when asked.
2. Set seconds per photo, video length, shuffle, mute, zoom, crop and clock as you like.
   Settings save automatically.
3. Press **▶ Start slideshow now** to test it.

## 5. Make it the screensaver

### Phones/tablets
Press **Set as screensaver…** in the app (or Settings > Display > Screen saver), choose
**Photo Dream**, and pick *When to start* (e.g. while charging). Not every phone brand shows
this menu; if yours doesn't, just use *Start slideshow now*.

### Google TV / Android TV
Google TV hides third-party screensavers, so set it once from the PC (same as Aerial Dream):
```
adb connect 192.168.1.50
adb shell settings put secure screensaver_components com.aminu.photodream/.SlideshowDreamService
```
Then set the delay on the TV: **Settings > System > Energy > When to turn on screensaver**
(wording varies by model, sometimes under *Ambient mode* or *Display*).

To go back to Google's screensaver:
```
adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
```

To test it straight away without waiting:
```
adb shell am start -n "com.android.systemui/.Somnambulator"
```
(This test command doesn't exist on every device; if it fails, just wait for the timeout.)

**Amazon Fire TV:** newer Fire OS versions block replacing the screensaver. Use the
*Start slideshow now* button instead.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| "No photos or videos found" | Check the folder actually has supported files; try *Search the media library* mode. |
| Screensaver shows the error after a TV update | Open Photo Dream and choose the folder again (re-grants access). |
| USB stick not visible in the picker | Unplug/replug it, then wait a minute; or use media library mode. |
| A video is skipped | That file's format isn't supported by the device's player; convert it to H.264 MP4. |
| Photos look sideways (Android 8 only) | Android 8 doesn't apply photo rotation info; rotate the files on your PC. |
| Settings changes don't appear in the screensaver | They apply the next time the screensaver starts. |
| No location on a photo | The photo has no GPS data (location was off on the camera, or it was stripped when shared via WhatsApp/Facebook etc.). |
| Coordinates shown instead of a place name | The device was offline or the lookup failed; it will try again next time. Place names are remembered once found. |
| No locations at all in media library mode | Re-open Photo Dream, press Start, and allow the "access locations from your media" prompt. |

## Updating to a new version

Build the APK again as in step 1, then install it over the top. Your settings are kept:
```
adb install -r app-debug.apk
```
(Or press ▶ Run in Android Studio with the device connected.)

## Project layout (for tinkering)

- `MainActivity.kt` – settings screen (`res/layout/activity_main.xml`)
- `SlideshowView.kt` – the slideshow engine (fades, zoom, video, clock)
- `SlideshowActivity.kt` – full-screen slideshow + remote/touch controls
- `SlideshowDreamService.kt` – the screensaver
- `MediaScanner.kt` – finds the photos/videos
- `Prefs.kt` – saved settings

Ideas for later: photo date/location caption, network shares (SMB/NAS), Google Photos albums.
