# Google Play listing

The text Play Console holds for the Android app, kept here so each version's listing is
reviewed and versioned like the code. Google's policies want the background location
disclosure in the description too, so the description repeats what the app's own dialog says.

## Main store listing

- App name: Mismeet
- Category: Maps & Navigation
- Website: https://github.com/zululima/mismeet
- Privacy policy: https://github.com/zululima/mismeet/blob/main/PRIVACY.md
- App icon: `design/icon-512.png`
- Feature graphic: `design/play-store/feature-graphic.png`
- Phone screenshots: `design/play-store/phone/`, in file order

Short description:

```text
Share your location with people you choose, end-to-end encrypted over Nostr.
```

Full description:

```text
Mismeet shows the people you choose where you are, and you where they are, without an account and without a company server in the middle.

Your phone publishes its location to Nostr relays, encrypted separately for each person you share with. Relays pass it along but can read neither the location nor who it is for, and every location expires after 24 hours.

- Add a contact by scanning the QR code of their invite, or by pasting it.
- See everyone on the map at a glance, with where and how long ago each position was seen and how far each person is from you.
- When someone is on the move, see where the road has probably taken them since, guessed from OpenStreetMap roads.
- Give your contacts photos; they stay on your phone.
- Watch your contacts without sharing anything yourself: sharing is off until you turn it on, and until then the app asks for no permission.
- Choose for each contact whether you share your location with them.
- No sign-up, no email, no phone number. Your identity is a key that stays on your phone.
- No Google Play Services, no ads and no tracking.

When you turn sharing on, Mismeet collects location data to share your position with the contacts you choose, even when the app is closed or not in use. While sharing is on, a notification shows that it is running, and the switch on the Me screen stops it.

Mismeet is free and open source under the GNU Affero General Public License. Its protocol and source code are at github.com/zululima/mismeet.
```

## Release notes

Play takes up to 500 characters per language, from the version's section of `CHANGELOG.md`.

0.1.0:

```text
First release. Add the people you know by their invite QR code and see them on a map. Sharing your own location is off until you turn it on, and you choose who gets it. Locations are end-to-end encrypted Nostr events that expire after 24 hours, with no account and no server of its own.
```

## App content

As submitted in Play Console on 2026-10-24, the sign in instructions as updated on 2026-10-27.

- Privacy policy: the URL above.
- Ads: no ads. Advertising ID: not used.
- Sign in details: restricted, since Play counts QR codes and actions on another device. One
  set named "Two devices, no account", with no username or password and these instructions:

  ```text
  No account or login. Seeing a contact needs a second device. On both devices turn on Share my location on the Me tab and allow location, then all the time. On device A open the Me tab to show the invite QR code. On device B tap Add beside Contacts at the bottom of the map, then Scan QR code (or Paste after Copy link on A), type a name, tap Add. Within a minute, or after Refresh, A's contact list shows "Someone added you": tap Add back, type a name, tap Add. Within a minute, or after Share now, each device shows the other on the map and in its contact list. With one device, the Me tab shows the invite and the sharing switch.
  ```

- Content rating: category All Other App Types; the app shares the user's precise location
  with other users, and nothing else in the questionnaire applies. IARC rated it PEGI 3, ESRB
  Everyone and 3+ elsewhere, with the interactive element Shares Location.
- Government app, financial features, health features: none.
- Target audience: 18 and over. A location sharing app aimed at children would fall under the
  Families policy, and the app has no age checks.
- Data safety: App info and performance, Crash logs and Diagnostics, collected and not
  shared, not processed ephemerally, optional (users choose), for Analytics; no other data
  collected or shared. These are the opt-in problem reports of `PRIVACY.md`, from version code
  16. No deletion request: a report carries nothing that ties it to its sender, and the relays
  drop it after 30 days. The developer runs no server; the location leaves the phone only
  end-to-end encrypted for the chosen contacts, which Play exempts from disclosure, and the
  app has no analytics service or ads. Data is encrypted in transit. The Nostr relays still see a public key, an IP address and publish times,
  and the Overpass API sees an IP address and the road and place name squares it serves, as
  `PRIVACY.md` says.

## Sensitive permissions

Location permissions, as submitted on 2026-10-24. App purpose:

```text
Mismeet lets people share their location with the family members and friends they choose and see theirs on a map, end-to-end encrypted over Nostr, with no account and no server of its own.
```

The one feature that needs location in the background:

```text
Sharing the user's location with the contacts they chose. Once the user turns on Share my location, a foreground service with a visible notification publishes the position every 10 minutes, end-to-end encrypted for those contacts only, so they see a recent position while the phone is in a pocket. The service must start without the app on screen, after the phone or the service restarts, which needs background location. Location is never used for ads or analytics.
```

Video, 24 seconds from the emulator: the Me screen, the disclosure, the system prompt, "Allow
all the time", then the app closed with the sharing notification showing.

```text
https://www.youtube.com/watch?v=3O42YLkma38
```

Foreground service permissions: the task "User-initiated location sharing", with the same video.

Battery optimisation: the Me screen asks for the exemption with
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, because stock Android 12 otherwise stops the
service. Play accepts that request from safety apps that keep users and their families safe;
if review refuses it, the fallback is the settings list, `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`,
without the permission.

## Closed testing

The track "Closed testing - Alpha" covers every country and region Play lists but France, 176
of them, and the rest of the world, as on the App Store. Testers join the Google Group mismeet-testers@googlegroups.com,
which anyone can find and join, and only its owner sees members or posts; they then opt in
through the track's link. Feedback goes to the GitHub issues. Release 1 (0.1.0) is the bundle
built from the tag `android-0.1.0-1`.
