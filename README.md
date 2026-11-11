# share-it: transfer files between Android and Mac or Windows over Wi-Fi

**Open your Android phone in Finder or File Explorer over Wi-Fi. Browse it, then copy and paste files in both directions. No cable, no cloud, no app on the computer.**

share-it is a free, open-source Android app. It turns your phone storage into a network drive on your local Wi-Fi. Your Mac connects to it with Finder (**⌘K**), and Windows connects with File Explorer or WinSCP. The files go directly from the computer to the phone over your Wi-Fi, and they never go to the internet.

It is a wireless alternative to Android File Transfer, which Google no longer offers for Mac.

## Features

- **Browse the phone in Finder.** The phone storage shows as a drive. Copy, paste, rename and delete files with Finder or File Explorer.
- **Local network only.** Files go over your Wi-Fi from device to device. No account, no cloud and no internet are necessary.
- **Nothing to install on the computer.** The phone uses WebDAV, a standard protocol that macOS and Windows already support.
- **Fast.** The server streams files directly to and from storage. Your Wi-Fi sets the speed, not the app.
- **Password protection.** Each session has a new random password.
- **Copies continue when the phone locks.** A foreground service keeps the server and the Wi-Fi awake until you tap Stop.
- **New files show immediately in Gallery.** share-it adds each copied file to Android's media index, so Gallery and the Files app show it and make its thumbnail.
- **No `._` junk files.** Finder makes `._name` and `.DS_Store` metadata files on network drives. share-it keeps them in memory and does not write them to the phone.
- **Small and auditable.** The server is about 150 lines of Go, and the app is 2 Kotlin files. The app has no ads, no analytics and no trackers.

## How it works

```
 Mac (Finder ⌘K) ─┐                        ┌──────── Android phone ────────┐
                  ├── Wi-Fi (WebDAV/HTTP) ─►│ share-it → phone storage      │
 Windows (WinSCP) ┘                        └───────────────────────────────┘
```

The app runs a small WebDAV server on the phone, on port 8080. The computer mounts that server as a network drive.

## Requirements

- An Android phone with Android 11 or later.
- A Mac or a Windows PC on the **same Wi-Fi network** as the phone.

## Quick start

### 1. On the phone

1. Download `share-it.apk` from the [latest release](https://github.com/amjadaziz817/share-it/releases/latest) and open it on the phone. Android asks you to allow installs from your browser or Files app. You can also build the APK yourself (see [Build](#build)).
2. Open **share-it** and tap **Start sharing**.
3. The first time, Android asks for **All files access**. Allow it, go back, and tap **Start sharing** again.
4. The app shows a URL, a name and a password, for example:
   ```
   http://192.168.1.20:8080
   Name: phone
   Password: kndgny6n
   ```

### 2a. On a Mac (Finder)

1. In Finder, press **⌘K** (**Go → Connect to Server**).
2. Type the URL from the phone and click **Connect**. If macOS warns that the connection is not secure, click **Connect** again.
3. Type the name and the password from the phone.
4. The phone opens as a drive. Copy and paste files as on a USB drive.
5. When you finish, eject the drive in the Finder sidebar, and tap **Stop sharing** on the phone.

### 2b. On Windows

**Easiest way: WinSCP (free).** Create a new site with these values:

| Field | Value |
|---|---|
| File protocol | WebDAV |
| Encryption | No encryption |
| Host name | The phone IP, for example `192.168.1.20` |
| Port | `8080` |
| User name / Password | `phone` and the password from the phone |

**File Explorer.** By default, Windows does not send a password over plain HTTP, and it stops downloads larger than 50 MB. Run this 1 time in PowerShell as Administrator:

```powershell
$p = "HKLM:\SYSTEM\CurrentControlSet\Services\WebClient\Parameters"
Set-ItemProperty $p BasicAuthLevel 2                 # allow a password over HTTP
Set-ItemProperty $p FileSizeLimitInBytes 4294967295  # 4 GB, the maximum
Restart-Service WebClient
```

Then right-click **This PC**, select **Map network drive**, and type the URL from the phone. File Explorer cannot copy a file larger than 4 GB. For larger files, use WinSCP.

## Comparison with other ways to move files

| | share-it | Android File Transfer | LocalSend | USB cable (MTP) |
|---|---|---|---|---|
| Works over Wi-Fi | Yes | No | Yes | No |
| Browse the phone from the computer | Yes, in Finder or File Explorer | Yes | No, send and receive only | Yes |
| App on the computer | No | Yes | Yes | Depends on the OS |
| Available for Mac now | Yes | No, Google discontinued it | Yes | Needs a 3rd-party app |

## Security and privacy

- share-it works only on your local network. It does not send any data to the internet.
- Each session has a new random password. Requests without the password get `401 Unauthorized`.
- The connection uses **plain HTTP, not HTTPS**. Other devices on the same Wi-Fi cannot log in without the password, but they can read the data that passes over the network. **Use share-it only on Wi-Fi networks that you trust**, not on public or hotel Wi-Fi.
- The app needs **All files access** to share the whole phone storage. Tap **Stop sharing** when you finish.

## Troubleshooting

| Problem | Fix |
|---|---|
| The computer cannot connect | Make sure that both devices use the same Wi-Fi. Guest networks and many office or hotel networks block devices from connecting to each other. |
| The phone shows an address like `10.x.x.x` and the computer cannot connect | A VPN is on. Turn it off, or turn on "Allow LAN traffic" in the VPN app. |
| Copied files do not show in Gallery | Update to the latest version. For older copies, restart the phone so that Android scans the storage again. |
| The copy stops when the phone locks | Open **Settings → Apps → share-it → Battery** and select **Unrestricted**. Some Samsung and Xiaomi phones stop background apps. |
| Finder shows the drive as unavailable | Eject it, tap **Start sharing** on the phone again, and reconnect with ⌘K and the new password. |

## Build

You need Go, Android Studio (Android SDK, NDK and its bundled JDK) and `gomobile`.

```sh
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init

export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<version>
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$HOME/go/bin:$PATH"

go test ./server
gomobile bind -target=android/arm64,android/amd64 -androidapi 30 -javapkg com.shareit \
  -o android/app/libs/server.aar ./server
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/share-it-debug.apk
```

The APK is `android/app/build/outputs/apk/debug/share-it-debug.apk`. The app shows its build number at the bottom of the screen.

## Project layout

| Path | Contents |
|---|---|
| `server/` | The WebDAV server in Go (`golang.org/x/net/webdav`), with the password check, the media index updates and the filter for Finder metadata files. |
| `android/` | The Android app (Kotlin, no AndroidX): 1 screen and 1 foreground service. |
| `scripts/speedtest.sh` | Measures the copy speed to and from the phone without Finder. |
| `scripts/android-ready.sh` | Optional: prepares video episodes for Android (see below). |

### Optional: video previews that show the first frame

Android makes a video thumbnail from a frame that it selects itself, often not the first frame. `scripts/android-ready.sh` uses `ffmpeg` to add the first frame as the cover picture of each MP4. It does not re-encode the video. The script also moves an episode number to the start of the name (`Show - Episode 01.mp4` → `EP01 - Show.mp4`). It writes new files to `<folder> (android)` and does not change the original files.

```sh
scripts/android-ready.sh "/path/to/folder"
```

## FAQ

**How do I transfer files from Android to Mac without a cable?**
Install share-it on the phone and tap **Start sharing**. On the Mac, press ⌘K in Finder and type the URL that the phone shows.

**Is there an Android File Transfer alternative for Mac?**
Yes. share-it shows the phone in Finder over Wi-Fi, so you do not need Android File Transfer or a USB cable.

**Does share-it upload my files to the cloud?**
No. The files go directly between the phone and the computer on your local network.

**Does it work with an iPhone?**
Not yet. iOS lets an app share only its own folder, not the whole phone.

**Does it need root, ADB or developer mode?**
No. share-it is a normal app. It needs only the All files access permission.

## Status

share-it is an early version. The developer uses it with macOS Finder and Android phones. The Windows steps follow the standard WebDAV setup for Windows, but nobody has tested them with share-it yet. Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

share-it is free and open-source software under the [MIT License](LICENSE).
