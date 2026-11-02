# Development

Run the commands here from the root of your checkout. Releasing to the stores is in
[releasing.md](releasing.md).

## Files

- [`spec/SPEC.md`](../spec/SPEC.md): the protocol: event kind and tags, encryption and padding,
  expiration, relay handling, the contact model, client behaviour and the threat model.
- [`CLAUDE.md`](../CLAUDE.md): the rules and the commands Claude Code follows in this repository.
- `ios/`: the Swift and SwiftUI app, generated from `ios/project.yml` with XcodeGen.
- `android/`: the Kotlin app and the `protocol` module it builds on.
- `design/`: the icon sketch, the script that turns it into both apps' icons, the store icon
  and Google Play's feature graphic, the README's screenshots, taken as under Testing without a
  second phone, and the store listings with their screenshots under `app-store/` and
  `play-store/`.
- [`scripts/release.sh`](../scripts/release.sh): tags store builds and checks that a release
  build comes from its tag, see [releasing.md](releasing.md).
- [`scripts/make-countries.py`](../scripts/make-countries.py): writes the country borders both
  apps bundle, from Natural Earth's public domain 1:50m countries.
- [`scripts/check-dco.sh`](../scripts/check-dco.sh) and
  [`.github/workflows/dco.yml`](../.github/workflows/dco.yml): the sign-off check CI runs on
  pull requests and on pushes to `main`, see [`CONTRIBUTING.md`](../CONTRIBUTING.md).
- [`zapstore.yaml`](../zapstore.yaml) and [`CHANGELOG.md`](../CHANGELOG.md): the Zapstore
  listing and the release notes it reads, which Google Play's release notes repeat.
- [`CONTRIBUTING.md`](../CONTRIBUTING.md): the terms contributions come under and the sign-off
  every commit needs.
- [`PRIVACY.md`](../PRIVACY.md): the privacy policy the store listings link to.
- [`PROMPTS.md`](../PROMPTS.md): every prompt behind the commits, with its commit, generated
  from the commit messages.
- [`LICENSE`](../LICENSE), [`LICENSE-APP-STORE-EXCEPTION`](../LICENSE-APP-STORE-EXCEPTION) and
  [`LICENSE-CC-BY-SA-4.0`](../LICENSE-CC-BY-SA-4.0): the licence texts.

## Requirements

- iOS: a Mac with Xcode 27 (the iOS 27 SDK; the deployment target is iOS 18), `xcodegen` and
  `xcbeautify` from Homebrew, an Apple ID (a free one is enough: the app uses no push and no
  special entitlement) and an iPhone on iOS 18 or later. The test device is an iPhone XR.
- Android: a JDK 17 or later (Temurin 21 here), the Android command line tools with
  `platforms;android-36`, `build-tools;36.0.0` and `platform-tools`, and a phone on Android 10
  or later. The test devices are a phone on GrapheneOS and a Motorola Moto G200 5G on Android
  12. Gradle comes from the checked-in wrapper; no Android Studio is needed.

## Set up once: iOS

1. Sign in to Xcode with the Apple ID under Xcode > Settings > Apple Accounts. Signing in
   alone creates nothing: a free account gets its personal team, certificate and profile only
   once a project asks for them, so `security find-identity` shows no identity yet.

2. Generate the project and open it in Xcode:

   ```bash
   xcodegen generate --spec ios/project.yml && open ios/Mismeet.xcodeproj
   ```

   Select the Mismeet target, then Signing & Capabilities, and pick the team named after you
   with "(Personal Team)". Xcode registers the app id and creates the development certificate
   and profile. Do the same for the MismeetTests target. Xcode writes the team id into the
   generated project; copy it to `ios/.team-id` (git-ignored, one line), which the command
   line builds read, since the next `xcodegen generate` discards the project's copy:

   ```bash
   grep -m1 -oE 'DEVELOPMENT_TEAM = [A-Z0-9]{10}' ios/Mismeet.xcodeproj/project.pbxproj | cut -d' ' -f3 | tee ios/.team-id
   ```

3. On the iPhone, enable Settings > Privacy & Security > Developer Mode, connect it by USB and
   trust the Mac. List it, then write its identifier (the first column) to `ios/.device-id`
   (git-ignored, one line):

   ```bash
   xcrun devicectl list devices
   ```

## Set up once: Android

1. Point Gradle at the SDK (`android/local.properties` is git-ignored); the path below is the
   Homebrew one:

   ```bash
   printf 'sdk.dir=/opt/homebrew/share/android-commandlinetools\n' > android/local.properties
   ```

2. Put `adb` on the PATH:

   ```bash
   ln -s /opt/homebrew/share/android-commandlinetools/platform-tools/adb /opt/homebrew/bin/adb
   ```

3. On the phone: Settings > About phone, tap Build number seven times, then Settings > System >
   Developer options > USB debugging. Connect by USB with a data cable (a charge-only cable
   leaves the phone invisible), choose "File transfer" when the phone asks what the USB
   connection is for, and accept the "Allow USB debugging?" prompt that shows the Mac's
   fingerprint, ticking "Always allow from this computer". Check that the phone shows as
   `device`; `unauthorized` means the prompt is still waiting on the phone, and an empty
   list means the cable, the port or USB debugging:

   ```bash
   adb devices
   ```

## Android emulator

An emulator image without Google APIs, in line with the app's independence from Play Services.
Install the emulator and the image once, and create a virtual device:

```bash
sdkmanager "emulator" "system-images;android-36;default;arm64-v8a"
```

```bash
avdmanager create avd --name mismeet --package "system-images;android-36;default;arm64-v8a" --device pixel_7
```

Start it; it keeps running in that terminal, and `-no-window` runs it without a window:

```bash
/opt/homebrew/share/android-commandlinetools/emulator/emulator -avd mismeet
```

It then counts as the connected phone for the install and launch commands below. Set its
position (longitude first) and take a screenshot:

```bash
adb emu geo fix 24.9384 60.1699
```

```bash
adb exec-out screencap -p > emulator.png
```

## Testing without a second phone

A fake publisher on the Mac stands in for the iPhone. The watching app prints its own invite
at launch, `Invite: https://zululima.github.io/mismeet/i#nprofile1...`, to logcat on Android and to the simulator's log on iOS,
and shows it on the Me screen:

```bash
adb logcat -d -s Mismeet:I
```

```bash
xcrun simctl spawn booted log show --info --last 5m --predicate 'subsystem == "app.mismeet.ios"'
```

Publish a location for that invite; the tool prints the fake publisher's own invite and a QR
code to add on the watching app, and keeps its key under `android/protocol/build/` so that
later runs move the same contact. `MISMEET_FAKE_NAME=other` in the environment makes a second
publisher, several readers go comma-separated, and `-` as the reader publishes nothing but
creates the publisher's key and QR code, which it also writes as
`android/protocol/build/<name>-invite.png` for a phone to scan off the screen:

```bash
android/gradlew -p android -q :protocol:fakePublish -PfakeArgs="<invite> 60.1699 24.9384"
```

`MISMEET_FAKE_SPEED` in metres per second, `MISMEET_FAKE_HEADING` in degrees and
`MISMEET_FAKE_AGE` in seconds before now make a contact on the move, which the watching app
then estimates along the roads, here a car going north-west on Mannerheimintie:

```bash
MISMEET_FAKE_SPEED=12 MISMEET_FAKE_HEADING=316 MISMEET_FAKE_AGE=120 android/gradlew -p android -q :protocol:fakePublish -PfakeArgs="<invite> 60.17106 24.93688"
```

Debug builds also take contacts at launch, so the simulator and the emulator need no typing;
the invites are separated by semicolons:

```bash
SIMCTL_CHILD_MISMEET_TEST_INVITES="<invite>;<invite>" xcrun simctl launch booted app.mismeet.ios
```

An entry may carry a name, `Aino|<invite>`; without one the contact is named Test 1, Test 2 and
so on. On the simulator, `SIMCTL_CHILD_MISMEET_TEST_TAB=me` in the same way opens the Me tab
instead of the map, and `contacts` pulls the contact list up over the map, which is how the
README's screenshots were taken.

```bash
adb shell "am start -n app.mismeet.android/.MainActivity --es invites '<invite>;<invite>'"
```

## Build, test and run

iOS, after cloning and after every edit to `ios/project.yml`:

```bash
xcodegen generate --spec ios/project.yml
```

Build for the simulator (`xcrun simctl list devices available` lists the simulators; the
generic destination builds without booting one):

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath ios/build build | xcbeautify
```

Unit tests:

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath ios/build test | xcbeautify
```

Run on the simulator: boot one, show it, build for it as above, then install and launch:

```bash
xcrun simctl bootstatus "iPhone 17" -b && open -a Simulator
```

```bash
xcrun simctl install booted ios/build/Build/Products/Debug-iphonesimulator/Mismeet.app && xcrun simctl launch booted app.mismeet.ios
```

Build for the connected iPhone. The destination names the phone on purpose: a free team has
no registered devices, and only a build for a specific device registers it and creates the
provisioning profile; a `generic/platform=iOS` build fails with "no devices".

```bash
set -o pipefail && xcodebuild -project ios/Mismeet.xcodeproj -scheme Mismeet -destination "platform=iOS,id=$(cat ios/.device-id)" -derivedDataPath ios/build -allowProvisioningUpdates DEVELOPMENT_TEAM="$(cat ios/.team-id)" build | xcbeautify
```

Install and launch on the device (add `--console` to the launch to stream its output). The
first launch is refused until the phone trusts the personal team: Settings > General > VPN &
Device Management, tap the developer app entry, Trust. After that, launching works.

```bash
xcrun devicectl device install app --device "$(cat ios/.device-id)" ios/build/Build/Products/Debug-iphoneos/Mismeet.app && xcrun devicectl device process launch --device "$(cat ios/.device-id)" --terminate-existing app.mismeet.ios
```

Android, build:

```bash
android/gradlew -p android assembleDebug
```

Lint and unit tests:

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

## Notes

- Free Apple ID: the provisioning profile expires after seven days, so rebuild and reinstall
  weekly, and at most ten app ids can be registered per seven days.
- GrapheneOS has no Google location services: the app uses GPS, and the network provider only
  when GrapheneOS's own network location is turned on in Settings.
- Stock Android, and Android 12 in particular, stops background services it considers
  wasteful. The Me screen offers the battery optimisation exemption; grant it, or the sharing
  service will not survive the screen being off for long.
- From Android 11, Android asks for location "while in use" first and "all the time" only from
  the app's settings page; the Me screen walks through both, but only once "Share my location"
  is on. Off, which is the default on both platforms, the phone only watches its contacts and
  needs no permission beyond the camera for scanning a QR code.

## First test between two phones

The iPhone publishes and the Android phone watches. Each phone scans the other's invite once:
the iPhone's scan tells it whom to encrypt for, the Android phone's scan tells it whose events
to fetch.

1. On the iPhone, open Me, turn on "Share my location" and grant location while using the app,
   then tap "Allow location" for "Always". "Check setup" on the same screen lists each relay
   as answering.
2. On the Android phone, open Me: its invite QR code is on screen. On the iPhone, tap + beside
   Contacts at the bottom of the map, then "Scan QR code", scan it, give the contact a name and
   tap Add. Within a few seconds "Check setup" on the iPhone shows "Last share" a moment ago
   and each relay holding that location.
3. On the iPhone, open Me to show its QR code. On the Android phone, tap Add beside Contacts at
   the bottom of the map, then "Scan QR code", allow the camera, scan it, name the contact, Add.
   Pulled up, the contact list shows the district, "seen ..." and the distance after the next
   refresh, within a minute, or at once with Refresh. Both apps open on the map, framed around
   everyone with a known position in the phone's own country. After panning, the button at the
   bottom frames that view again; with someone abroad, "Everyone" in that view widens it to
   them too.
4. Walk a few hundred metres with the iPhone, or drive. Significant location changes wake the
   app and the Android row's "Seen" time moves. Pulling the contact list down on the iPhone, once
   it is pulled up over the map, refreshes its own view; the "Share now" button on its Me
   screen forces a share.
5. To check revocation, switch "Share my location" off for the contact on the iPhone: the next
   refresh on the Android phone shows "no longer sharing", with the last position kept.
