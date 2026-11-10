# PodcastsPlus

A podcast player for the Light Phone III. Search for shows or add any RSS feed, then stream or download episodes and listen away. It appears as "Podcasts+" in your tools list.

## Features

- **Find podcasts**: search the podcasts directory, or add any show by its RSS feed URL.
- **Episode feed**: Home shows recent episodes from all your subscriptions, or your list of subscribed podcasts. Choose which in Settings.
- **Podcast pages**: browse a show's episodes, search within them, refresh the feed, and sort newest or oldest first.
- **Playback**: plays in the background with the screen off. Skip back or forward 15 seconds, and set the speed to 0.5x, 1x, 1.25x, 1.5x, or 2x. Your position is saved as you listen.
- **Auto-Play**: when an episode ends, play the next unlistened episode from the same show or from any show in your recent episodes, or turn it off.
- **Downloads**: save episodes for offline listening and manage them from the Downloads screen.
- **Listened tracking**: episodes are marked listened when they finish. Long-press an episode, or use the check icon in the player, to change it.
- **Background sync**: feeds refresh automatically every 6 hours.
- **Remove podcasts**: unsubscribe only, or unsubscribe and delete that show's downloads. Listened history is kept either way.
- **Settings**: invert colors, Auto-Play, Home screen view, add podcast, and an optional Bluetooth icon on the home bar. Bluetooth itself can only be turned on or off in LightOS settings for now.

## Installation

### From Releases

Download the latest APK from [Releases](../../releases) and install it over ADB:

```
adb install <downloaded-file>.apk
```

### From Source

Requires Android Studio (or the command line) with JDK 17 and the Android SDK. No GitHub token is needed.

```
./gradlew :tool:installDebug
```

On Windows, use `.\gradlew :tool:installDebug`.

`tool/lighttool.toml` targets a real Light Phone III (`serverPackage = "com.lightos"`). For the LightOS emulator, switch to `serverPackage = "com.thelightphone.sdk.emulator"`.

This project includes small changes to the Light SDK in `sdk/client`: the volume buttons work inside the tool, streaming continues with the screen off, and the launch screen is plain black.

## Credits

Built on the [Light SDK](https://github.com/lightphone/light-sdk) by The Light Phone (MIT). The original copyright notice is kept in [LICENSE](LICENSE), and the SDK's own README is kept in [README.light-sdk.md](README.light-sdk.md).

Podcast search uses the [iTunes Search API](https://performance-partners.apple.com/search-api). Episodes are loaded directly from each podcast's own RSS feed.

## License

MIT. See [LICENSE](LICENSE).

## Disclaimer

Unofficial, independent open-source project — not affiliated with or endorsed by The Light Phone, Inc. Light Phone and Light OS are trademarks of The Light Phone, Inc.
