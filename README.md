# Photo Dream

An Aerial Dream-style screensaver and slideshow for **your own photos and videos**.
Works on Android phones, tablets and Android TV / Google TV / Nvidia Shield (Android 8.0 or newer).

**What it does**

* Cross-fading photo slideshow with a slow zoom ("Ken Burns") effect
* Plays videos (MP4, MKV, WebM, MOV, 3GP), muted by default
* Clock and date overlay, like Aerial Dream
* Shows the date each photo or video was taken (e.g. "14 March 2024") and where
(e.g. "📍 Leeds, United Kingdom"), when the file records it; each can be switched off
* Plays from a folder on the device, the media library, a **network folder on your PC or NAS**,
or a **YouTube playlist or channel**
* Runs as a proper Android screensaver, or on demand with "Start slideshow now"
* Remote control: Right = next, Left = previous, OK = pause, Back = exit

Photos: JPG, PNG, WebP, GIF (first frame), BMP, HEIC (Android 9+).

\---

## 1\. Download

[**⬇ Download the latest Photo Dream APK**](../../releases/latest): open the release and
tap **app-release.apk** under *Assets*.

Direct link (handy for typing into a TV):

```
https://github.com/meanmeen/PhotoDream/releases/latest/download/app-release.apk
```

You don't need Android Studio or any developer tools.

## 2\. Install

### Android phone or tablet

1. Open the download link above on the phone and download the APK.
2. Tap the downloaded file. If asked, allow your browser or file manager to
**Install unknown apps**, then tap **Install**.

### Android TV / Google TV / Nvidia Shield

Pick whichever is easiest:

* **Downloader app (no computer needed):** install **Downloader by AFTVnews** from the TV's
Play Store. Open it, type the direct link above, and install when it finishes downloading.
Allow Downloader to *install unknown apps* when the TV asks.
* **USB stick:** copy the APK onto a USB stick, plug it into the TV, and open it with a file
manager app (e.g. *File Commander* or *X-plore*). Allow unknown apps when asked.
* **From a computer with ADB:** enable Developer options on the TV
(**Settings > System/Device Preferences > About**, press OK on *Build* 7 times), turn on
**USB/Network debugging**, then run:

```
  adb connect <TV-IP-address>
  adb install app-release.apk
  ```

If Photo Dream doesn't appear on the TV home screen, find it in **Settings > Apps > See all apps**
or the **Apps > See all** row, and add it to your favourites.

## 3\. Put your photos somewhere

* **Phone/tablet:** make a folder such as `Pictures/Slideshow` and copy photos/videos into it.
* **TV / Shield:** a USB stick or microSD card works well. Choose **removable storage** if the
TV asks how to use it. On a Shield you can also copy files over Wi-Fi: turn on
**Settings > Device Preferences > Storage > Transfer files over local network**, then open
`\\\\SHIELD` in Windows File Explorer and copy into `internal\\Pictures\\Slideshow`.
* **On your PC or NAS:** leave them where they are and use *Network folder* (see below).
* **On YouTube:** use *YouTube playlist or channel* (see below).
* Videos should be ones the device can play normally (H.264/H.265 MP4 is safest).

### Network folder (Windows PC, Mac or NAS)

1. On a Windows PC, right-click the folder > **Properties > Sharing > Share…**, add yourself
(Read is enough) and click **Share**. Note the path it shows, e.g. `\\\\MYPC\\Photos`.
2. In Photo Dream choose **Network folder**, enter that path (you can add a sub-folder,
e.g. `\\\\MYPC\\Photos\\Slideshow`), plus your Windows username and password. If you sign in to
Windows with a Microsoft account, use that email address and its password.
3. Press **Test connection**. It should report how many photos and videos it found.
If the PC name isn't found, use its IP address instead (e.g. `\\\\192.168.1.20\\Photos`).

**Dropbox, OneDrive or Google Drive:** install the provider's desktop app on the PC, let it sync
the folder you want (set it to *always keep on this device* / *available offline*), then share
that synced folder as above. Photo Dream then shows your cloud photos with nothing extra to set up
on the TV. The PC must be switched on.

### YouTube

Choose **YouTube playlist or channel** and paste one of:

* a playlist link (`https://www.youtube.com/playlist?list=…`),
* a channel link or `@handle`, which plays the channel's public uploads.

Unlisted videos play too: add them to a playlist (the playlist can itself be unlisted) and paste
that playlist's link. **Private videos can't be shown**, because YouTube only plays them inside
YouTube for invited accounts. YouTube mode needs internet, uses YouTube's own player (ads may
appear), and plays video only, with no photo captions.

## 4\. Set it up

Open **Photo Dream**.

1. Under *Where are your photos?* choose **Pick a folder**, press **Choose folder…**,
browse to your folder and press **Use this folder > Allow**.

   * If your device says it has no folder picker, choose **Search the media library** instead and
type part of the folder name (e.g. `Slideshow`), or leave it blank to use everything.
Allow the photos/videos permissions when asked.
2. Set seconds per photo, video length, shuffle, mute, zoom, crop, clock, and the date and
location captions.
Settings save automatically.
3. Press **▶ Start slideshow now** to test it.

## 5\. Make it the screensaver

### Phones/tablets and Nvidia Shield

Press **Set as screensaver…** in the app, or go to the device's screensaver settings
(phones: *Settings > Display > Screen saver*; Shield: *Settings > Device Preferences > Screen saver*)
and choose **Photo Dream**.

### Google TV (e.g. Chromecast with Google TV)

Google TV hides third-party screensavers, so set it once from a computer with ADB
(the same method Aerial Dream uses):

```
adb connect <TV-IP-address>
adb shell settings put secure screensaver\_components com.aminu.photodream/.SlideshowDreamService
```

Then set the delay in the TV's settings (*System > Energy*, *Ambient mode* or *Display*,
depending on the model).

To go back to Google's screensaver:

```
adb shell settings put secure screensaver\_components com.google.android.apps.tv.dreamx/.service.Backdrop
```

**Amazon Fire TV:** newer Fire OS versions block replacing the screensaver. Use
*Start slideshow now* instead.

\---

## Updating

Download the newer APK and install it the same way. It installs over the old one and keeps your
settings. If you get *"App not installed"* or a *signature/conflict* error, uninstall the old
version first (*Settings > Apps > Photo Dream > Uninstall*), then install again and re-pick your folder.

## Troubleshooting

|Problem|Fix|
|-|-|
|"No photos or videos found"|Check the folder actually has supported files; try *Search the media library* mode.|
|Screensaver shows an error after a system update|Open Photo Dream and choose the folder again (re-grants access).|
|USB stick not visible in the picker|Unplug/replug it and wait a minute, or use media library mode.|
|A video is skipped|The device can't play that format; convert it to H.264 MP4.|
|Photos look sideways (Android 8 only)|Android 8 ignores photo rotation info; rotate the files on your PC.|
|No location on a photo|The photo has no GPS data (location was off on the camera, or it was removed when shared via WhatsApp, Facebook etc.).|
|Coordinates shown instead of a place name|The device was offline or the lookup failed; it tries again next time. Place names are remembered once found.|
|No locations at all in media library mode|Open Photo Dream, press Start, and allow access to locations in your media.|
|No date on a photo|The file doesn't record when it was taken (common for screenshots, scans and photos saved from messaging apps).|
|Network: "wrong username or password"|Use the account you sign in to the PC with. For a Microsoft-account PC, that's the email and its password (not your PIN).|
|Network: "couldn't find that PC"|Use the PC's IP address instead of its name.|
|Network: "the PC didn't answer"|The PC is asleep or off, or Windows *Network discovery / File sharing* is turned off for your network.|
|Network videos stutter|Use a wired connection for the TV or the PC, or put the largest videos on a USB stick instead.|
|YouTube: "couldn't find that playlist or channel"|Paste a playlist link instead of the @handle.|
|YouTube: some videos are skipped|They're private, or their owner has turned off embedding.|

## Privacy

Photo Dream doesn't collect or upload anything. Your photos stay on your device or your own
network. To show place names, the photo's coordinates are passed to Android's built-in location
lookup service. A network-folder password is stored only in the app's private storage on this
device and is excluded from backups. YouTube mode loads YouTube's own player, which follows
YouTube's privacy policy.

\---

## For developers: building from source

1. Install [Android Studio](https://developer.android.com/studio).
2. Clone this repository and open the `PhotoDream` folder in Android Studio; let Gradle sync.
3. **Build > Build App Bundle(s) / APK(s) > Build APK(s)** for a debug build, or
**Build > Generate Signed App Bundle or APK** for a release build.

Project layout:

* `MainActivity.kt`: settings screen (`res/layout/activity\_main.xml`)
* `SlideshowView.kt`: the slideshow engine (fades, zoom, video, clock, location caption)
* `SlideshowActivity.kt`: full-screen slideshow plus remote and touch controls
* `SlideshowDreamService.kt`: the screensaver
* `MediaScanner.kt`: finds the photos and videos
* `MediaDetails.kt`: reads the date taken and GPS location, and looks up place names
* `NetworkSource.kt`: network folders (SMB via [smbj](https://github.com/hierynomus/smbj)),
plus a local-only streamer so network videos can seek
* `YouTube.kt`: resolves playlist/channel links and builds the embedded YouTube player
* `Prefs.kt`: saved settings

