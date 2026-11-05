# Mismeet

Nostr-based location sharing for iOS and Android, in the style of Find My, with no server of
its own. The protocol lives in [spec/SPEC.md](spec/SPEC.md) and is the source of truth: app code
follows it, and a protocol change edits the spec first, in the same change.

## Layout

- `spec/`: the protocol specification.
- `docs/`: what people do by hand, written for them: `development.md` (requirements, the
  one-time setup, building and testing, the first test between two phones) and `releasing.md`
  (store accounts, keys and the steps of each release). The README stays one screen at
  1440x730 and links to both.
- `ios/`: Swift and SwiftUI app. XcodeGen generates `ios/Mismeet.xcodeproj` from
  `ios/project.yml`. Never edit the `.pbxproj`; change `project.yml` and regenerate. Deployment
  target iOS 18. Location comes from significant location change monitoring with the "Always"
  permission, and the app publishes on each wakeup. Under `ios/Mismeet/`: `Protocol/` (the
  wire format, mirrored in Kotlin), `App/` (the model and the app delegate that starts
  monitoring on a background launch), `Identity/` (Keychain), `Store/` (JSON state in
  Application Support), `Nostr/` (one short-lived client per publish or fetch), `Location/`,
  `UI/`.
- `android/`: Kotlin, built with the Gradle wrapper checked into `android/`. Two modules:
  `app` (the Android app, Compose) and `protocol` (a plain JVM library holding the protocol core
  and its tests). `compileSdk` and `targetSdk` are 36, `minSdk` is 29 so that Android 10
  phones run it. No Google Play Services dependency: location comes from the platform
  `LocationManager` inside a foreground service that publishes on a fixed timer, never from the
  fused provider. Under `android/app/src/main/kotlin/app/mismeet/android/`: `AppModel.kt`,
  `identity/` (a Keystore wrapped secret key), `store/` (JSON state in the files directory),
  `nostr/`, `location/` (the provider, the foreground service, the boot receiver and the road
  cells for estimates), `ui/`.
  The map tab opens first, framed around everyone with a known position above the contact
  sheet, and draws OpenStreetMap tiles through osmdroid, which needs the package name as user
  agent, has to be clipped inside Compose and scales its tiles to the screen density, or their
  labels are too small to read; a contact row also opens the position in whatever map app
  handles `geo:` URIs.

## Shared rules

- Both apps use the Nostr Dev Kit bindings (the project formerly called rust-nostr; its GitHub
  organisation is now `nostrdevkit`). Keep both platforms on the same version so protocol
  behaviour matches. Current version: 0.45.1.
  - iOS: Swift package product `NostrSDK` from `https://github.com/nostrdevkit/nostr-sdk-swift`,
    pinned to an exact version in `project.yml`.
  - Android: `org.nostrdevkit:nostr-sdk:0.45.1` from Maven Central, Kotlin package
    `org.nostrdevkit.sdk`. The `protocol` module compiles and tests against the JVM flavour
    `org.nostrdevkit:nostr-sdk-jvm`, which carries macOS native libraries, so its tests run on
    this Mac; the `app` module excludes that jar and uses the Android AAR, which has the same
    API.
- Protocol constants (kind, `d` tag, expiration, buckets, timeouts) live in
  `ios/Mismeet/Protocol/ProtocolConstants.swift` and
  `android/protocol/src/main/kotlin/app/mismeet/protocol/ProtocolConstants.kt` and match the
  table in the spec, section 10. The conformance checklist in section 12 is a unit test on each
  platform, under `ios/MismeetTests` and `android/protocol/src/test`.
- Nothing but kinds `31122` and `10002`, the kind `1059` add-back requests of spec section
  4.5 and the kind `1059` problem reports of section 8.8 is ever published. No profile, no
  contact list, no DMs but those reports.
- Problem reports are off by default on both platforms: keep every automatic report behind
  `reportsEnabled`. "Send report to the developer" on Check setup is the one path without it,
  and it shows the whole text first. A report's text goes through `ProblemReport.redact`, so a
  new line in it, or a new log line at info level, never needs to hold a key, a name or a
  position to be useful. The reports key in `ProtocolConstants` is the user's; its secret is
  in `~/keys/mismeet-reports.nsec`, which the git-ignored `android/.reports-key` names.
- Publishing is opt-in and off by default on both platforms (spec section 8.1): the phone then
  only watches its contacts, publishes nothing, not even its relay list, and needs no location
  permission. The "Share my location" switch on the Me screen turns it on. Keep every
  publishing path behind `sharingEnabled`.
- Controls go at the bottom of the screen, where a thumb reaches them, or at least never in the
  top bar: the map's framing button and the buttons of sheets and dialogs, such as Add contact
  and the photo crop, sit at the bottom, and only titles stay at the top. The contact list is a
  sheet over the map, as Find My's people are, with Refresh and Add in its header beside the
  title; tapping a contact turns the sheet into that contact's card. Both apps have only the
  Map and Me tabs. New controls follow the same rule.
- Never log a secret key, a decrypted payload or a contact's public key above debug level.
- Bundle identifier `app.mismeet.ios`, Android `applicationId` `app.mismeet.android`. Changing
  one means changing it here and in the project files in the same commit.
- Pipe every `xcodebuild` through `xcbeautify` with `pipefail` set, as in the commands below.
- Invites are links, `https://zululima.github.io/mismeet/i#<nprofile>` (spec section 4.1),
  opened as an iOS universal link (Associated Domains in `project.yml`) and an Android verified
  app link (the intent filter on `MainActivity`). The site repository
  `zululima/zululima.github.io`, the user's own, pushed to `main`, serves what they need:
  `.well-known/apple-app-site-association` for `VXSNWDR5MY.app.mismeet.ios`,
  `.well-known/assetlinks.json` with the SHA-256 of the Play App Signing certificate and of the
  upload key, and `mismeet/i.html`, the page shown without the app. A new team, bundle id,
  package or signing key changes those files in the same change. A GitHub Pages site for the
  `mismeet` repository would take over `/mismeet/` and break every link.
- Earlier builds shared `https://lambdakilo.github.io/mismeet/i#<nprofile>`. The apps still
  accept it and list `lambdakilo.github.io` for app links, served by
  `lambdakilo/lambdakilo.github.io` from the user's second account `lambdakilo`, which forwards
  every path to `zululima.github.io` with the fragment kept and carries copies of the two
  `.well-known` files. A change to those files goes to both sites.
- The app icon is generated from `design/icon-sketch.png` by `design/make-icon.py` into the
  iOS asset catalog, the Android adaptive icon foreground, `design/icon-512.png`, the store
  icon for Google Play and Zapstore, and `design/play-store/feature-graphic.png`; change the
  sketch or the script and rerun it, never the PNGs by hand. It needs Pillow in a virtual environment:

  ```bash
  python3 -m venv design/.venv && design/.venv/bin/pip install pillow && design/.venv/bin/python design/make-icon.py
  ```
- The country borders that frame the opening map, `ios/Mismeet/Location/countries.json` and
  `android/app/src/main/assets/countries.json`, are written by `scripts/make-countries.py` from
  Natural Earth (public domain); change the script and rerun it, never the JSON by hand:

  ```bash
  python3 -I scripts/make-countries.py
  ```

## Releases

- Android goes to Google Play as an app bundle from `bundleRelease`, signed with the upload
  key and re-signed by Play App Signing with a key Google generated; `zapstore.yaml` also
  describes it for Zapstore (not F-Droid). iOS goes to TestFlight and the App Store through
  `scripts/release.sh upload-ios`, which archives from the tag and uploads with the build
  numbers kept; Xcode signs the upload with a cloud-managed distribution certificate, so none
  is created on this Mac. It authenticates with the App Store Connect API key that the
  git-ignored `ios/.asc-key` names, or without that file with the Apple Account in Xcode. The App Store screenshots in `design/app-store/` are full size and RGB, at
  sizes App Store Connect accepts (1320 x 2868 and 1206 x 2622), and the Play screenshots in
  `design/play-store/phone/` are 1080 x 1920 RGB from the emulator under `wm size 1080x1920`,
  since Play refuses a long side more than twice the short one; redo both after a visible
  change. The Play listing and declarations live in `design/play-store/listing.md`, and the
  disclosure dialog on the Me screen has to come right before each location permission
  request, as Play's policy demands.
- Every push to `main` that changes what an app is built from (`ios/` for iOS, `android/` for
  Android) ships a test build of that app, by the user's standing go: raise
  `CURRENT_PROJECT_VERSION` or `versionCode` in the pushed commit, then run
  `scripts/release.sh tag <platform>` and `upload-ios` for TestFlight or `upload-android` for
  Play's closed testing track, which goes through the Play Developer API with the service
  account key that the git-ignored `android/.play-key` names. A push that changes neither app
  ships nothing. A store release happens only when the user says release or new release, and
  the session picks the version from the changes since the last release: patch for fixes
  alone, minor when users get something new, major when the protocol changes in a way older
  apps cannot read (a new `d` tag, spec section 11). It raises `MARKETING_VERSION` and
  `versionName` to that version, adds the `CHANGELOG.md` section and ships the iOS test build,
  which it then submits for App Store review. For Android it raises `versionCode` as for a
  test build but runs `upload-android production`, which puts the store build in a production
  draft, and rolls that out in Play Console with the user's declarations; testers get it too,
  since Play serves the highest version code of the tracks they are in. Never run `zsp
  publish`, which signs with the user's own Nostr key.
- The app shows no log in any build; it writes to the system log only (Console.app and
  logcat), and its last 50 lines go only into problem reports. For people, "Check setup" on the Me screen tests what sharing needs, in the style
  of Slack's connection test: permissions, battery and data restrictions, the last share and
  its failure, and each relay, by asking it for this phone's own location event. A new reason
  sharing can fail gets a check there on both platforms.
- The App Store listing text, from the subtitle and categories to the notes for App Review,
  lives in `design/app-store/listing.md`: change it there and commit, then enter it in App
  Store Connect, never only in the browser.
- Every store build comes from an annotated tag `<platform>-<version>-<build>` made by
  `scripts/release.sh tag`. `assembleRelease`, `bundleRelease` and `scripts/release.sh archive-ios` refuse to
  run unless the tree is clean and `HEAD` is that tag; `-PuntaggedRelease` skips the Android
  check only for testing the signing setup. Versions live in `android/app/build.gradle.kts`
  (`versionName`, `versionCode`) and `ios/project.yml` (`MARKETING_VERSION`,
  `CURRENT_PROJECT_VERSION`); each release adds a `CHANGELOG.md` section, which Zapstore shows and Play's release notes
  repeat.
- The release key never enters the repository: `android/keystore.properties` names it and is
  git-ignored, as are `*.jks`, `*.keystore` and `*.p12`. Test signing with a throwaway key in
  the scratchpad and delete the properties file afterwards.
- Code is AGPL-3.0-or-later with the app store exception in `LICENSE-APP-STORE-EXCEPTION`,
  prose CC BY-SA 4.0. Keep dependencies under licences that allow App Store distribution
  (MIT, Apache-2.0, BSD, MPL-2.0). A GPL, LGPL or AGPL dependency without an equivalent
  exception would block it.
- Every commit carries a Developer Certificate of Origin sign-off, as `CONTRIBUTING.md`
  requires: commit with `git commit -s`, which signs with the configured identity. The DCO
  workflow runs `scripts/check-dco.sh` over the new commits of every pull request and push to
  `main`; commits before 5b23249 predate the rule and are never checked. The repository
  ruleset "main requires DCO sign-off" makes its `sign-off` job a required check on `main`;
  the admin role bypasses it, which keeps the owner's direct pushes working, so CI checks
  those only after the push.

## Building and running

The prerequisites and the one-time setup (Apple ID and team id, device identifier, SDK path,
`adb`, developer options on the phones) are in `docs/development.md`, written for people;
they do not belong here. The commands below assume that setup is done, so that
`ios/.team-id`, `ios/.device-id` and `android/local.properties` exist. `docs/development.md`
lists the same commands for people: change both together.

### iOS

Generate the project after cloning and after every edit to `ios/project.yml`:

```bash
xcodegen generate --spec ios/project.yml
```

Build for the simulator:

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath ios/build build | xcbeautify
```

Run on the simulator after that build:

```bash
xcrun simctl bootstatus "iPhone 17" -b && open -a Simulator
```

```bash
xcrun simctl install booted ios/build/Build/Products/Debug-iphonesimulator/Mismeet.app && xcrun simctl launch booted app.mismeet.ios
```

Unit tests (iPhone 17 is one of the installed simulators; `xcrun simctl list devices available`
lists them):

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath ios/build test | xcbeautify
```

Build for the connected iPhone. The destination names the phone on purpose: a free team has
no registered devices, and only a build for a specific device registers it and creates the
provisioning profile; a `generic/platform=iOS` build fails with "no devices".

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination "platform=iOS,id=$(cat ios/.device-id)" -derivedDataPath ios/build -allowProvisioningUpdates DEVELOPMENT_TEAM="$(cat ios/.team-id)" build | xcbeautify
```

Install and launch on the device. The first launch on a phone is refused until the user has
trusted the personal team under Settings > General > VPN & Device Management; that cannot be
done from the Mac, so report it and stop.

```bash
xcrun devicectl device install app --device "$(cat ios/.device-id)" ios/build/Build/Products/Debug-iphoneos/Mismeet.app && xcrun devicectl device process launch --device "$(cat ios/.device-id)" --terminate-existing app.mismeet.ios
```

Add `--console` to the launch command to stream the app's stdout until Ctrl-C. Leave it out
when testing background wakeups, so the app runs detached as it would for a user.

Device logs: Console.app, select the iPhone, filter on process `Mismeet`. Simulator logs:

```bash
xcrun simctl spawn booted log stream --level debug --predicate 'subsystem == "app.mismeet.ios"'
```

Simulated movement, one position or a built-in scenario (`xcrun simctl location booted list`
names them):

```bash
xcrun simctl location booted set 60.1699,24.9384
```

```bash
xcrun simctl location booted run "Freeway Drive"
```

- Significant location change delivery on the simulator is unreliable; the real test is a walk
  or drive with the iPhone.
- Background location needs `UIBackgroundModes` with `location` and the two location usage
  strings in `Info.plist`, defined in `project.yml`. It is not an entitlement, so it works with
  the free team.
- `ITSAppUsesNonExemptEncryption` is `false` in `project.yml`: it records the user's export
  compliance answers (standard encryption algorithms, not distributed in France), with which
  App Store Connect asks no compliance question per build. A change to the app's encryption,
  or distribution in France, goes back to the user before the next upload.
- Always pass `-derivedDataPath ios/build`, so the app path in the install command stays valid.
- The app is iPhone only: `TARGETED_DEVICE_FAMILY` is `"1"` on the app target, because
  XcodeGen's application preset sets `"1,2"` there and overrides the project setting. A build
  that declares iPad makes App Store Connect demand 13-inch iPad screenshots.
- The free team's profile lasts seven days and at most ten app ids can be registered per seven
  days, so never churn the bundle identifier.

### Android

Build:

```bash
android/gradlew -p android assembleDebug
```

Lint and unit tests, the verify command; it runs lint on the app and the unit tests of both
modules:

```bash
android/gradlew -p android lint test
```

Install and launch on the phone:

```bash
android/gradlew -p android installDebug && adb shell am start -n app.mismeet.android/.MainActivity
```

Logs:

```bash
adb logcat --pid="$(adb shell pidof -s app.mismeet.android)"
```

Relay integration test: publishes a location event and a relay list under throwaway keys to
the default public relays and reads them back. Skipped unless the variable is set, so the
verify command stays offline.

```bash
MISMEET_RELAY_TESTS=1 android/gradlew -p android :protocol:test --tests 'app.mismeet.protocol.RelayIntegrationTest'
```

Emulator (`mismeet`, API 36 without Google APIs, created as `docs/development.md` describes).
Start it headless, wait for it, and it is the device for the install and launch commands:

```bash
nohup /opt/homebrew/share/android-commandlinetools/emulator/emulator -avd mismeet -no-window -no-audio -no-boot-anim -no-snapshot > /dev/null 2>&1 &
```

```bash
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'
```

Position, screenshot, and shutting it down:

```bash
adb emu geo fix 24.9384 60.1699
```

```bash
adb exec-out screencap -p > emulator.png
```

```bash
adb emu kill
```

Problem reports: read the ones on the report relays, print them and save each new one under
`reports/` at the repository root, since the relays drop them after 30 days:

```bash
android/gradlew -p android -q :protocol:reports
```

Fake publisher and launch hooks for debug builds, the same commands as "Testing without a
second phone" in `docs/development.md`. The watching app logs its own invite at launch on a
line starting `Invite:`, which these print:

```bash
adb logcat -d -s Mismeet:I
```

```bash
xcrun simctl spawn booted log show --info --last 5m --predicate 'subsystem == "app.mismeet.ios"'
```

Publish a location for that invite. The publisher prints its own invite, writes it as
`android/protocol/build/<name>-invite.png` for a phone to scan, and keeps its key under
`android/protocol/build/`, so a rerun moves the same contact. The first run for a reader also
sends it an add-back request named after the key. `MISMEET_FAKE_NAME=other` in front
makes a second publisher, several reader invites go comma-separated, `-` as the invite only
creates the key, and relays may follow the coordinates:

```bash
android/gradlew -p android -q :protocol:fakePublish -PfakeArgs="<invite> 60.1699 24.9384"
```

`MISMEET_FAKE_SPEED` in metres per second, `MISMEET_FAKE_HEADING` in degrees and
`MISMEET_FAKE_AGE` in seconds before now make the contact move:

```bash
MISMEET_FAKE_SPEED=12 MISMEET_FAKE_HEADING=316 MISMEET_FAKE_AGE=120 \
  android/gradlew -p android -q :protocol:fakePublish -PfakeArgs="<invite> 60.17106 24.93688"
```

Add contacts at launch, semicolon separated, and `Name|<invite>` names one. On the simulator,
`SIMCTL_CHILD_MISMEET_TEST_TAB=me` in front of the launch opens the Me tab, and `contacts` the
map with the contact list pulled up:

```bash
SIMCTL_CHILD_MISMEET_TEST_INVITES="<invite>;<invite>" xcrun simctl launch booted app.mismeet.ios
```

```bash
adb shell "am start -n app.mismeet.android/.MainActivity --es invites '<invite>;<invite>'"
```

The README's screenshots under `design/screenshots/` come from the simulator and the emulator
with the two fake contacts, scaled to 600 px high and shown in one row by `width`
attributes; redo them after a visible change:

```bash
sips -Z 600 design/screenshots/<name>.png
```

- Build with the checked-in wrapper only, never the Homebrew `gradle` (it is Gradle 9.8 on JDK
  27, which the Android Gradle Plugin does not support); the wrapper pins the version the plugin
  supports.
- GrapheneOS has no Google location services. The network location provider exists only when the
  user turns on GrapheneOS's own network location in Settings, so rely on `GPS_PROVIDER` and
  treat `NETWORK_PROVIDER` as optional: `LocationProvider.currentFix` asks both at once and takes
  the network fix only when GPS finds none in time, as indoors.
- On Android, the location, notification and battery prompts appear and the foreground service
  runs only once "Share my location" is on; the QR scanner asks for the camera when used.
- `minSdk` is 29, Android 10: `POST_NOTIFICATIONS` exists from API 33, so permission checks for
  it are gated on the API level, `LocationManager.getCurrentLocation` from API 30, so fixes go
  through `LocationManagerCompat`, and stock Android 12 needs the battery optimisation exemption
  the Me screen offers or it stops the foreground service.
- Background location is a foreground service with `foregroundServiceType="location"`. It needs
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `ACCESS_FINE_LOCATION`,
  `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` and `POST_NOTIFICATIONS`. From
  Android 11, Android grants "while in use" first and "all the time" only from the app's
  settings page, so the app has to send the user there.
- The Nostr Dev Kit AAR ships native libraries for every ABI; the build keeps arm64-v8a and,
  for older 32-bit phones, armeabi-v7a. A native
  crash at startup on GrapheneOS is most likely its hardened memory allocator: check the
  per-app exploit protection compatibility mode before debugging anything else, and report it.
- AndroidX is pinned to the last versions that compile against API 36; the newer ones demand
  compileSdk 37 and lint flags the pins as outdated. Compose material icons are a separate
  dependency, `material-icons-core`, not pulled in by material3.

## Verify after edits

Run before saying a change is done, not only before committing:

- iOS: the simulator build and the unit tests above.
- Android: the lint and unit test command above.

Stop what was started: `android/gradlew -p android --stop` for the Gradle daemon,
`xcrun simctl shutdown all` for simulators and `adb emu kill` for the emulator, so nothing
keeps running when the turn ends.

## Git-ignored paths

`.gitignore` covers the generated Xcode project, build output, the Gradle caches and the
local-only files named above (`ios/.team-id`, `ios/.device-id`, `android/local.properties`,
`android/.reports-key`, `reports/`).
Anything else that is generated or machine-specific goes there too, never into a commit.
