# Indus TV

A simple, remote-friendly IPTV player for Android TV with an India focus, built and tuned for basic 720p and 1080p TVs.

## Features
- Channel grid with logos
- Instant search
- Category filters and Favorites (long-press OK on a channel)
- Language filter: Hindi, English, Tamil, Telugu, Malayalam, Kannada, Marathi, Gujarati, Bengali, Punjabi, Odia, Rajasthani and more
  (detected from the playlist's language tag or the channel name, so a few channels may appear under "Other")
- **Global** option: all English-language channels from around the world (separate worldwide playlist)
- Channels that stream on YouTube open in the YouTube app
- Remote control navigation: ▲ / ▼ to change channel while watching
- Saved playlist for offline use, with automatic retry on stream errors
- Lightweight, with small buffers for low-RAM TVs

## Important
**This app is only a media player. It does not include, host or distribute any channels or streams.**
It loads an M3U playlist from a URL. By default it uses the public, community-maintained
[iptv-org](https://github.com/iptv-org/iptv) India playlist. You can change the playlist in the app
with the **Source** button.

You are responsible for the playlists you use and for making sure you have the right to watch their content.
Streams in public playlists are unofficial and may go offline or be geo-blocked at any time.
Indus TV is not affiliated with any TV channel or broadcaster.

## Install
1. Download the APK (`IndusTV-v1.5.apk`) from the [Releases](../../releases) page.
2. Copy it to your TV with a USB drive or `adb install`, and allow installs from unknown sources.

## Updating
Install the new APK over the old one. Your favorites and playlist are kept.
Only use APKs from the Releases page, because APKs built on a different machine are signed with a different key and
Android will refuse to update over them. If an update is refused, uninstall once and install again.

## Build it yourself
Open the project in Android Studio and choose **Build → Build APK(s)**.
Or push to GitHub: the workflow in `.github/workflows/build.yml` builds the APK automatically.
To publish a Release, push a tag, for example `git tag v1.1 && git push origin v1.1`.

### Signing (maintainers)
Releases are signed with one fixed key so every version can update the previous one. Create a keystore once:

    keytool -genkeypair -v -keystore indus.jks -alias indus -keyalg RSA -keysize 2048 -validity 10000

Then add these repository secrets (Settings → Secrets and variables → Actions):
`KEYSTORE_BASE64` (the keystore encoded as base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Never commit the keystore, and keep a backup. If you lose it, users must uninstall to update.

## Credits
- Playlist source: [iptv-org](https://github.com/iptv-org/iptv)
- Playback: [AndroidX Media3 / ExoPlayer](https://developer.android.com/media/media3)
- Images: [Coil](https://coil-kt.github.io/coil/)

## License
MIT. See [LICENSE](LICENSE).
