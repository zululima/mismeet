# App Store listing

The text App Store Connect holds for the iOS app, kept here so each version's listing is
reviewed and versioned like the code. Apple's guidelines keep it free of Apple trademarks such
as Find My and of other mobile platforms.

## App Information

- Name: Mismeet
- Subtitle: Private location sharing
- Primary category: Navigation
- Secondary category: Social Networking

## Version page

Promotional text:

```text
Share where you are with the people you choose. End-to-end encrypted over Nostr, with no account and no company server in between.
```

Description:

```text
Mismeet shows the people you choose where you are, and you where they are, without an account and without a company server in the middle.

Your phone publishes its location to Nostr relays, encrypted separately for each person you share with. Relays pass it along but can read neither the location nor who it is for, and every location expires after 24 hours.

- Add a contact by scanning the QR code of their invite, or by pasting it.
- Watch your contacts without sharing anything yourself: sharing is off until you turn it on, and until then the app needs no location permission.
- Choose for each contact whether you share your location with them.
- See everyone on the map at a glance, with where and how long ago each position was seen and how far each person is from you.
- Your location updates in the background as you move.
- No sign-up, no email, no phone number. Your identity is a key that stays on your phone.

Mismeet is free and open source under the GNU Affero General Public License. Its protocol and source code are at github.com/zululima/mismeet.
```

Keywords:

```text
location,sharing,friends,family,map,privacy,encrypted,nostr,find,where,meet
```

- Support URL: https://github.com/zululima/mismeet/issues
- Marketing URL: https://github.com/zululima/mismeet
- Copyright: 2026 Mismeet contributors
- Screenshots: `iphone-6.9/` for the large display, `iphone-6.3/` for the medium one, in file
  order.

## App Review notes

```text
Mismeet needs no account: each install creates its own key on the device.

Location: sharing is off until the user turns on Share my location on the Me tab; until then the app only shows contacts and needs no location permission. Turned on, it asks for location while in use, then Always. With Always it publishes the user's position, end-to-end encrypted, on significant location changes while the app is closed, so that the contacts the user chose see a recent position. It publishes only for contacts the user added and left sharing on, and nothing when there are none.

To try it, use two devices. On each, open the Me tab to show the invite QR code. On the other, tap + beside Contacts at the bottom of the map, then Scan QR code, and add it. Within a minute each phone shows the other on the map. With one device, the Me tab shows the invite and the map shows your own position.
```

## Answers to App Review's request for more information

App Review asked for a screen recording and for the answers below, both in a reply and in the
Notes field. The Notes field holds the App Review notes above followed by these answers.

```text
Purpose and audience
Mismeet lets people share their location with the family members and friends they choose, and see theirs on a map. It is for people who want this without creating an account and without a company server that can read their movements. Each phone publishes its position to public Nostr relays, end-to-end encrypted separately for each chosen contact. The relays pass the data along but cannot read it, and every position expires after 24 hours.

Setup and main features
No account, login or sample files are needed.
1. Open Mismeet, turn on Share my location on the Me tab and allow location access while using the app, then tap Allow location for Always, so that the position updates in the background.
2. To add a contact, open the Me tab on their device to show its invite QR code. On yours, tap + beside Contacts at the bottom of the map, tap Scan QR code, scan it, type a name and tap Add. Do the same the other way round to share in both directions.
3. Within about a minute the map shows the contact's position together with yours, and the contact list, pulled up from the bottom of the map, shows where and how long ago it was seen. Each contact has a switch to stop sharing your location with them, and swiping a contact removes it.
With a single device, the Me tab shows the invite and the map shows your own position. Seeing a contact needs a second device running Mismeet, as in the screen recording.

External services
- Public Nostr relays: relay.damus.io, nos.lol and relay.primal.net. They store the encrypted location events and the list of relays each user publishes to, and cannot read locations.
- Apple MapKit for the map.
- The open-source Nostr Dev Kit library (MIT licence) for the Nostr protocol and the encryption.
Mismeet uses no authentication, payment, analytics, advertising or AI service, and has no server of its own.

Regional differences
The app works the same in every region where it is offered. It is not offered in France.

Regulated industries and third-party material
Not applicable. The app does not operate in a regulated industry and contains no protected third-party material. Its source code is public under the GNU Affero General Public License with an app store exception: https://github.com/zululima/mismeet
```

## TestFlight

The external group "Public" hands out a public link, with no tester limit, and needs each new
version's first build to pass Beta App Review. Test Information holds the texts below, the
marketing URL above, the privacy policy
https://github.com/zululima/mismeet/blob/main/PRIVACY.md, as feedback email the address Play
shows publicly, and for Beta App Review the contact App Review has, no sign-in and the App
Review notes above.

Beta App Description:

```text
Mismeet shows the people you choose where you are, and you where they are, without an account and without a company server in the middle. Locations travel through Nostr relays, encrypted separately for each contact.
```

What to Test:

```text
Add a contact by scanning the invite QR code on their Me tab, turn on Share my location on yours, and check that each phone shows the other on the map within a minute. Report bugs and ideas at github.com/zululima/mismeet/issues.
```
