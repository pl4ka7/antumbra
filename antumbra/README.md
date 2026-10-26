<p align="center"><img src="play/icon-512.png" width="96" alt=""></p>

<h1 align="center">Antumbra</h1>
<p align="center">A private Android browser that routes everything through Tor.</p>

---

Antumbra embeds tor and a hardened GeckoView engine in a single Android app. Every request,
including DNS, goes through Tor; each website gets its own circuit; nothing is written to
disk. It adds the things a privacy browser needs on a phone: censorship circumvention,
a live circuit view, an app lock, a panic wipe, and an AI assistant that talks to models
through Tor.

## Features

**What sets it apart**
- Identity spaces: GeckoView contexts plus per-tab SOCKS credentials keep cookies, storage
  and circuits separate between identities, even on the same site.
- Per-site shield: live tracker counter, circuit, per-tab JavaScript and desktop mode.
- Reader view extracted by a content script, with one-tap AI summary over Tor.
- Encrypted vault (Keystore AES-GCM) and a bundled directory of verified onion services.
- Downloads through Tor to a location the user picks (Storage Access Framework).
- Default-browser and share-target support, find in page, auto-wipe after inactivity.

**Network**
- tor 0.4.9 in-process via tor-android, controlled over its control socket.
- Per-site circuit isolation: a built-in WebExtension assigns SOCKS credentials derived from
  the top-level site (`IsolateSOCKSAuth`), the same model Tor Browser uses.
- Fail-closed proxy: no direct fallback, local DNS disabled, DoH and QUIC off.
- Pluggable transports through IPtProxy: Snowflake, obfs4, meek and custom bridges
  (webtunnel included). Built-in bridges ship with the app and refresh over Tor via Moat.
- Automatic mode tries a direct connection, detects a stalled bootstrap and moves through
  Snowflake → obfs4 → meek, remembering what worked.
- Exit country selection and upstream SOCKS/HTTPS proxy.
- Circuit view per site, "New circuit for this site", "New identity".
- Onion-Location support with optional automatic redirect.

**Browser**
- Private sessions only, Gecko preferences hardened (resist fingerprinting, letterboxing,
  first-party isolation, HTTPS-only, ETP strict, WebRTC/sensors/geolocation off).
- Tor Browser–style security levels.
- External schemes, downloads and Android permissions blocked; payment and WalletConnect
  links require confirmation.
- Onion search engines (DuckDuckGo, Brave Search, Ahmia) and clearnet alternatives.

**Device**
- `FLAG_SECURE`, incognito keyboard, no backups.
- Biometric / device-credential app lock.
- Quick Settings tile that wipes all data and kills the process.
- Discreet launcher icon.
- Optional foreground service exposing the SOCKS proxy to other apps (wallets).

**AI assistant**
- OpenRouter (bring your own key) or any OpenAI-compatible endpoint, including `.onion`.
- Requests go through Gecko's network stack, so they inherit Tor and isolation.
- Streaming, model picker with pricing, zero-data-retention routing, in-app reporting.
- API key encrypted with an Android Keystore AES-GCM key; chats stay in memory.

## Threat model

Antumbra protects your IP address from the sites you visit, your browsing from your local
network, and your sessions from being linked across sites. It does not make you
indistinguishable from Tor Browser users: its fingerprint is its own. It cannot protect
against logging in to personal accounts or a global passive adversary. For high-risk use,
Tor Browser or Tails remain the right tools.

## Architecture

```
app/src/main/java/app/antumbra/
├── AntumbraApp.kt          process-wide singletons (lazy: Gecko child processes skip them)
├── MainActivity.kt         navigation, lock, restart
├── core/                   settings, Keystore secrets, registrable-domain helper
├── tor/                    tor lifecycle, bridges, transports, GeoIP, circuits
├── web/                    GeckoView runtime, isolation bridge, browser view model, Net
├── ai/                     OpenAI-compatible client, chat view model, reports
├── system/                 keep-alive service, panic tile, app lock, launcher icon
└── ui/                     Compose screens and theme
app/src/main/assets/extensions/isolation/   proxy.onRequest + Onion-Location extension
```

## Build

Requirements: Android Studio (latest stable), JDK 17+.

```
gradle wrapper --gradle-version 9.6.0   # once, if the wrapper jar is missing
./gradlew assembleDebug
```

The GeoIP database is downloaded during the first build; without it the exit-country option
is hidden.

### Release

1. Create an upload key once and keep it safe:
   ```
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 36500
   ```
2. Copy `keystore.properties.example` to `keystore.properties` and fill it in.
3. Set `reportEndpoint` and `privacyPolicyUrl` in `gradle.properties`.
4. `./gradlew bundleRelease` and upload `app/build/outputs/bundle/release/app-release.aab`.

Dependencies live in `gradle/libs.versions.toml` and are kept current by Dependabot.
GeckoView and tor must be updated with every release.

## Licences

GeckoView (MPL-2.0), tor and tor-android (BSD-3-Clause), jtorctl (BSD), IPtProxy (MIT),
built-in bridge list from Orbot (BSD-3-Clause), Inter and JetBrains Mono (OFL-1.1), AndroidX (Apache-2.0).

Antumbra is not affiliated with or endorsed by The Tor Project.
