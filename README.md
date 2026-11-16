# Indus TV

A simple, remote-friendly IPTV player for Android TV, built and tuned for basic 720p Mi TVs.

## Features
- Channel grid with logos
- Instant search
- Category filters and Favorites (long-press OK on a channel)
- Language filter: Hindi, English, Tamil, Telugu, Malayalam, Kannada, Marathi, Gujarati, Bengali, Punjabi, Odia, Rajasthani and more
  (detected from the playlist's language tag or the channel name, so a few channels may appear under "Other")
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

## Install
1. Download the APK from the [Releases](../../releases) page, or from the latest **Actions** run (artifact `IndusTV-apk`).
2. Copy it to your TV with a USB drive or `adb install`, and allow installs from unknown sources.

## Build it yourself
Open the project in Android Studio and choose **Build → Build APK(s)**.
Or push to GitHub: the workflow in `.github/workflows/build.yml` builds the APK automatically.
To publish a Release, push a tag, for example `git tag v1.0 && git push origin v1.0`.

## Credits
- Playlist source: [iptv-org](https://github.com/iptv-org/iptv)
- Idea inspired by [Indian-IPTV-App](https://github.com/kananinirav/Indian-IPTV-App)
- Playback: [AndroidX Media3 / ExoPlayer](https://developer.android.com/media/media3)
- Images: [Coil](https://coil-kt.github.io/coil/)

## License
MIT. See [LICENSE](LICENSE).
