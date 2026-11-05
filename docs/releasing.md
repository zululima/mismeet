# Releasing

Android goes to Google Play, and [`zapstore.yaml`](../zapstore.yaml) also describes it for
[Zapstore](https://zapstore.dev), the Nostr app store; it is not on F-Droid. iOS goes to the
App Store. Anyone who would rather avoid the App Store builds and
installs the iOS app from source, as [development.md](development.md) describes; a free Apple
ID is enough, and such a build runs for seven days before it needs reinstalling. Run the
commands here from the root of your checkout.

## Store builds are tagged

Every build that goes to a store is made from an annotated git tag named
`<platform>-<version>-<build>`, such as `android-0.1.0-1` or `ios-0.1.0-1`, so the source of
any published binary is exactly the tagged commit. The version and build number come from
`versionName` and `versionCode` in `android/app/build.gradle.kts` and from
`MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in `ios/project.yml`. `scripts/release.sh`
creates and pushes the tag, and the release builds refuse to run unless the tree is clean and
`HEAD` is that tag. Each release also gets a section in [`CHANGELOG.md`](../CHANGELOG.md).

Every push to `main` that changes an app also ships a test build of it: the push raises the
build number, the commit is tagged, and the build goes to TestFlight or to Play's closed
testing track. Only a release goes to the App Store and to Play production, with its version
raised as semantic versioning says: patch for fixes, minor for new features, major for a
protocol change older apps cannot read.

## Android release key

Android store builds are signed with a release key kept outside the repository. On Google Play
it is the upload key: Play App Signing re-signs every bundle with an app signing key Google
keeps, and Play Console can reset a lost upload key. Zapstore instead links the key's
certificate to the publisher's Nostr identity and needs the same signature on every update, so
a lost key there means a new listing. Because Play re-signs, an install from Play and an
install from Zapstore cannot update each other.

Once, create the key and keep a backup:

```bash
keytool -genkeypair -keystore ~/keys/mismeet-release.jks -storetype PKCS12 -alias mismeet -keyalg RSA -keysize 4096 -validity 10000
```

Point the build at it in `android/keystore.properties`, which git ignores, with the passwords
`keytool` asked for:

```bash
cat > android/keystore.properties <<'PROPS'
storeFile=/Users/you/keys/mismeet-release.jks
storePassword=...
keyAlias=mismeet
keyPassword=...
PROPS
```

`-PuntaggedRelease` on `assembleRelease` or `bundleRelease` skips the tag check to try out the
signing setup; never publish what it builds.

## Problem reports key

Problem reports (spec section 8.8) are encrypted for one Nostr key, whose public half sits in
both apps' `ProtocolConstants` as `REPORTS_PUBLIC_KEY` and `reportsPublicKey`. Its secret
stays on the maintainer's Mac, in a file that the git-ignored `android/.reports-key` names.
Losing it makes every report sent to it unreadable, and a new key reaches users only with new
builds, so keep a backup in a password manager.

A fork makes its own key, then puts the hex it prints into both constants files and the spec's
section 10:

```bash
printf '%s\n' '~/keys/mismeet-reports.nsec' > android/.reports-key
```

```bash
android/gradlew -p android -q :protocol:reports -PreportsArgs=new
```

Read the reports, which saves each new one under `reports/`:

```bash
android/gradlew -p android -q :protocol:reports
```

## Android on Google Play

Once:

1. Create a personal developer account at <https://play.google.com/console/signup> with the
   Google account that will own the app. It costs 25 USD once and asks for an identity
   document. The Play Console Home page then asks to verify a real Android phone with the Play
   Console app: any phone that is not rooted and runs Android 10 or newer, not an emulator.
2. Create the app in Play Console: Mismeet, an app, free. Fill in the store listing and App
   content from [`design/play-store/listing.md`](../design/play-store/listing.md), with an email
   address that Play shows publicly. Upload the background location video to YouTube as
   unlisted or to Google Drive, and give its link in the location and foreground service
   declarations.
3. Run a closed test. Under Test and release > Testing > Closed testing, set the track's
   countries and its testers, here the Google Group `mismeet-testers`, which testers join
   before they opt in through the track's link. Upload the first bundle by hand under Create
   new release; Play asks how to sign the app, and the key Google generates is kept. Play
   opens production only once 12 testers have stayed opted in for 14 days in a row; then
   click Apply for production on the Dashboard, which Google reviews, usually within seven
   days.
4. Give `scripts/release.sh` access to Play through the Play Developer API. In the Google
   Cloud console, create a project, enable the Google Play Android Developer API, create a
   service account and a JSON key for it, and keep the key outside the repository. In Play
   Console, open Users and permissions, enter the service account's email address, add the app
   under App permissions with Release apps to testing tracks, and click Invite user. Then
   point the script at the key, with your key's path:

   ```bash
   echo ~/keys/mismeet-play.json > android/.play-key
   ```

For each test build:

1. Raise `versionCode` in `android/app/build.gradle.kts`, and for a new version also
   `versionName` and its section in `CHANGELOG.md`; commit and push to `main`.
2. Tag the commit and push the tag:

   ```bash
   scripts/release.sh tag android
   ```

3. Build the signed bundle from the tag and release it to the closed testing track, which
   Google reviews before testers get it:

   ```bash
   scripts/release.sh upload-android
   ```

For a release, raise `versionName`, `versionCode` and `CHANGELOG.md`, commit, push and tag as
above, then build the bundle and put it in a production draft, which you roll out under
Production in Play Console. The service account then also needs Play's permission to release to
production, granted under Users and permissions.

```bash
scripts/release.sh upload-android production
```

The Play screenshots come from the emulator with the contacts Aino and Eero published by the
fake publisher, and with its display set to 1080 x 1920, since Play refuses screenshots whose
long side is more than twice the short one:

```bash
adb shell wm size 1080x1920
```

Set it back afterwards:

```bash
adb shell wm size reset
```

## Android on Zapstore

It uses the release key above. Install `zsp`, Zapstore's publishing tool, with Go, which puts it in `~/go/bin`, or take a
binary from its [releases](https://github.com/zapstore/zsp/releases):

```bash
brew install go && go install github.com/zapstore/zsp@latest
```

Put the publishing npub into the `pubkey` line of [`zapstore.yaml`](../zapstore.yaml), then
commit and push it: the Zapstore relay reads that file from the repository to whitelist the
publisher on the first release.

For each release:

1. Raise `versionCode` and `versionName` in `android/app/build.gradle.kts`, add the version's
   section to `CHANGELOG.md`, commit and push to `main`.
2. Tag the commit and push the tag:

   ```bash
   scripts/release.sh tag android
   ```

3. Build the signed APK from the tag:

   ```bash
   android/gradlew -p android assembleRelease
   ```

4. Publish with your own key. `SIGN_WITH=browser` signs through a NIP-07 browser extension
   such as nos2x-fox: `zsp` serves a signing page on `http://localhost:17007/` and opens it in
   the default browser. When the extension lives in another browser or Firefox profile, open
   that address there and approve each event the extension asks about. A NIP-46 `bunker://`
   URL works as well. On the first publish `zsp` asks for the keystore to link its
   certificate to your npub.

   ```bash
   SIGN_WITH=browser ~/go/bin/zsp publish --commit "$(git rev-parse HEAD)" zapstore.yaml
   ```

## iOS on TestFlight and the App Store

Uploading needs a paid Apple Developer Program membership; the free personal team used for
development cannot distribute. Every upload goes to App Store Connect, where it is a TestFlight
build first. A release to the App Store submits one of those tested builds for review, so the
binary users get is the one that was tested, and it is already tagged.

Once, after the membership is active:

1. Find the team id under Membership details at <https://developer.apple.com/account> and
   write it to `ios/.team-id`, with your id in place of `ABCDE12345`. An individual
   membership can keep the id of the free personal team, and Xcode may go on calling it
   Personal Team; activation can take up to two days after payment.

   ```bash
   echo ABCDE12345 > ios/.team-id
   ```

2. Build for the iPhone with the device build command in [development.md](development.md). That registers the
   bundle id `app.mismeet.ios` under the team, and once the membership is active the
   development profile it fetches lasts a year instead of seven days. If Xcode answers that
   the id "is not available", another team holds it, and the bundle id has to change.
3. In App Store Connect (<https://appstoreconnect.apple.com>), open Apps, click "+" and then
   New App. Choose iOS, the name Mismeet (names are unique on the store, so another may be
   needed), the bundle id `app.mismeet.ios`, and the SKU `mismeet`.
4. Give `scripts/release.sh` an App Store Connect API key, so that uploads stop depending on
   the Apple Account in Xcode, which signs out now and then. In App Store Connect, open Users
   and Access > Integrations > App Store Connect API. The first time, the Account Holder clicks
   Request Access, ticks the box in the dialog and clicks Submit. Then generate a key under Team
   Keys with Admin access, which cloud signing needs, and download it, which is possible only
   once. Keep it outside the repository and write its path and the Issuer ID shown above the
   keys to `ios/.asc-key`, with your file and issuer id:

   ```bash
   printf '%s\n' ~/keys/AuthKey_ABCDE12345.p8 \
     00000000-0000-0000-0000-000000000000 > ios/.asc-key
   ```

   Without the file, the script uses the Apple Account signed in under Xcode > Settings >
   Apple Accounts.

For each build:

1. Raise `CURRENT_PROJECT_VERSION` in `ios/project.yml`, since every upload needs a new build
   number, and `MARKETING_VERSION` too when it starts a new version. Add the version to
   `CHANGELOG.md`, commit and push to `main`.
2. Tag the commit and push the tag:

   ```bash
   scripts/release.sh tag ios
   ```

3. Archive from the tag and upload it. Xcode creates the distribution certificate and the App
   Store profile on the first run:

   ```bash
   scripts/release.sh upload-ios
   ```

4. Wait until App Store Connect has processed the build, usually minutes, then open the app's
   TestFlight tab. The first build asks the export compliance question there: the app
   encrypts locations end to end with NIP-44 (ChaCha20 and secp256k1), beyond the operating
   system's encryption, and the Info.plist leaves the answer to you.
5. Under Internal Testing, add yourself as a tester. Install TestFlight from the App Store on
   the iPhone and install the build from it. TestFlight builds expire after 90 days.

To release a tested build on the App Store:

1. In App Store Connect, open the app's version page under iOS App, creating the version if it
   is not there, and set its version to the build's `MARKETING_VERSION`. The listing text is in
   [`design/app-store/listing.md`](../design/app-store/listing.md). Upload the screenshots from
   `design/app-store/iphone-6.3`, which App Store Connect uses for every iPhone size (the
   `iphone-6.9` set is there for a large-display override), and fill in the description,
   keywords, the support URL (this repository works), the privacy policy URL
   <https://github.com/zululima/mismeet/blob/main/PRIVACY.md>, the App Privacy answers, the
   age rating and the category.
2. Under Build, choose the TestFlight build you tested.
3. In App Review Information, explain the "Always" location: the app publishes on significant
   location changes while closed, to the contacts the user chose. Then click Add for Review
   and submit. Once approved, the version goes live when you release it.

The screenshots come from the iPhone 18 Pro Max and iPhone 17 simulators with a fixed status
bar and the contacts Aino and Eero published by the fake publisher.
