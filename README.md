# Mismeet

Location sharing in the style of Find My, on Nostr, for iOS and Android. Each phone publishes
its location to relays encrypted for each contact it shares with: no server, no Play Services.

<img src="design/screenshots/ios-map.png" alt="iOS map" width="94"> <img src="design/screenshots/ios-contacts.png" alt="iOS contacts" width="94"> <img src="design/screenshots/ios-me.png" alt="iOS Me screen" width="94"> <img src="design/screenshots/android-map.png" alt="Android map" width="92"> <img src="design/screenshots/android-contacts.png" alt="Android contacts" width="92"> <img src="design/screenshots/android-me.png" alt="Android Me screen" width="92">

| [`spec/`](spec/SPEC.md) | [`ios/`](ios) | [`android/`](android) | [`docs/`](docs) |
| --- | --- | --- | --- |
| the protocol both apps follow | Swift and SwiftUI | Kotlin, no Play Services | [development](docs/development.md), [releasing](docs/releasing.md) |

Android testers join [mismeet-testers](https://groups.google.com/g/mismeet-testers) (members are
hidden), then [opt in](https://play.google.com/apps/testing/app.mismeet.android) with the same
Google account. Play needs 12 of them for 14 days in a row: 5 are in, 7 to go, and the 14 days
start once all 12 are.

New here? Paste this into Claude Code:

```text
clone github.com/zululima/mismeet into ~/Projects and help me build and run it
```

Code AGPL-3.0-or-later with an [app store exception](LICENSE-APP-STORE-EXCEPTION), prose
CC BY-SA 4.0, see [CONTRIBUTING.md](CONTRIBUTING.md).
