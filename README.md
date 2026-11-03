# Skulk

<img src="docs/icon.png" alt="Skulk app icon: an orange sandworm arching out of a dune under twin moons" width="128" align="right" />

**Skulk** is an Android app for reading Reddit anonymously — no account, no official API, no ads — with iOS 26-style liquid glass.
Its function follows [Lurkur](https://apps.apple.com/us/app/lurkur-for-reddit/id6470203216) (an iOS reader for Reddit);
its UI is ported from [SwiftDdit / Lurker](https://github.com/SilverMarcs/SwiftDdit) (a SwiftUI client by Zabir Raihan), and uses the same glass
library as Uno Launcher ([Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)) so surfaces, toggles, buttons and menus bend and blur what is
behind them the way iOS's do.

`Skulk` is the project (repository); `Skulk` is the app.

## What it does

- **Feeds**: Home, Popular and All, with Best / Hot / New / Controversial / Top (hour to all time) sorting, endless scrolling, pull to refresh,
  and a switcher between the front pages and your starred subreddits. Optionally remembers the last feed and sort.
- **Posts**: text (Reddit markdown), images, galleries, GIFs, Reddit videos (HLS), YouTube, link cards and crossposts; NSFW media blurred until tapped;
  save on this device, share.
- **Comments**: the full thread with colour-coded depth bars (can be switched off), tap to collapse, the eight sort orders, inline images and GIFs,
  and a setting for how many comments to load (25–200, default 50).
- **Subreddits and users**: browse both, search inside a subreddit, star a subreddit to keep it in the feed switcher, view about info.
- **Search**: subreddits and posts.
- **Saved**: the bookmark keeps whole posts on your phone; the Saved tab reads them back with no network at all.
- **Appearance**: device-default, dark or light colour scheme; several launcher icons.
- **Links**: Reddit links (`reddit.com`, `redd.it`) open inside the app.

It is a reader: there is no voting, commenting or posting, because there is no account. What it shows is what any signed-out browser can see.

## Read this first: how it talks to Reddit

Skulk reads Reddit's public website JSON (`/r/<sub>/hot.json`, ...), the same data a signed-out browser gets, over plain HTTPS.
It is **not** Reddit's official OAuth API:

- Requests identify themselves as Skulk (name, version, read-only) and carry no cookies; Skulk does not pose as a browser or another app. Nothing is signed in, so nothing about you is sent.
- Skulk is a polite guest: one request at a time with a gap between them, short-term caching of what it just read, and it honours Reddit's
  Retry-After when told to slow down.
- **Reddit can refuse anonymous traffic (HTTP 403) or change these endpoints at any time.** When that happens Skulk shows it plainly and offers a
  retry; it does not disguise itself to get past a refusal. Reddit's terms govern use of its site; using this against them is your call.

## Status

Built and unit-tested against Reddit-shaped fixtures and a mock server (parsing, media extraction, comment flattening, URL building, error mapping,
pacing and caching). Not yet exercised against live Reddit on a phone. Expect rough edges when real data arrives.

## Install on your phone

Skulk is not on Google Play. Install the APK from the **[Releases page](https://github.com/everettpenne/Skulk/releases/latest)**.
It needs **Android 12 or newer** (API 31); Android 13 or newer for the full glass lens.

1. On your phone, open the latest release and download **`Skulk-<version>.apk`** (and, if you want to check it, `SHA256SUMS.txt`).
2. Open the downloaded file. Android will say it can't install apps from this source: tap **Settings** and allow your browser (or the Files app) to
   **install unknown apps**, then go back and tap **Install**. (On GrapheneOS this is the per-app "Install unknown apps" setting; turn it back off afterwards.)
3. Open **Skulk** and browse. There is nothing to sign into.

To update, install a newer APK over the old one; it is signed with the same key, so your saved posts and settings stay.

**Check the download (optional but sensible):** the release lists the APK's SHA-256. Compare it with `sha256sum Skulk-<version>.apk`, and the signing
certificate with `apksigner verify --print-certs Skulk-<version>.apk`; the certificate SHA-256 is
`c76163ce927f65df5869fe7b67f7e00ac81c8a254a5fc830590adef159724ff4`. Only install an APK from this repository's Releases page.

**From a computer instead:** enable *Developer options › USB debugging*, then `adb install Skulk-<version>.apk`.

**Build it yourself:** see below. A debug build (`./gradlew :app:installDebug`) is signed with your own debug key, so it will not install over the release one;
uninstall one before installing the other.

## Build

The liquid-glass UI kit (components, theme, type scale) is the separate `Glass` project, pulled in with a Gradle `includeBuild("../Glass")`, so it must be checked out next to this folder.

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:installDebug        # build and install on a connected device
```

Requirements: JDK 17+, Android SDK 36, Android 12 (API 31) or newer on the device (the glass effect needs Android 13 for the lens; on 12 it blurs without bending).

## Layout

```
app/src/main/java/com/everettpenne/skulk/
  data/        Reddit reader (api/), models, media extraction (media/), settings and on-device lists
  ui/glass/    comes from the Glass project: surfaces, controls, menus, sheets, tab bar
  ui/screens/  feed, post and comments, saved, search, settings
  ui/components/ post card, media views, rich text
  util/        formatting, markdown, link routing
```

## Licence

MIT. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Building with Glass

The shared UI kit lives in its own repository, [Glass](https://github.com/everettpenne/Glass), checked out next to this one (`../Glass`) and built
with it through `includeBuild`. `glass.version` names the Glass tag this app is built and released with; CI checks out exactly that tag. To
build locally, check Glass out beside this repository at that tag (`git -C ../Glass checkout $(cat glass.version)`).
