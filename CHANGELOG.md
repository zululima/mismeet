# Changelog

Store releases, newest first, in the [Keep a Changelog](https://keepachangelog.com/) format.
Zapstore reads the section whose version matches the published APK as its release notes, and
Google Play's release notes repeat it.

## [0.3.0] - 2026-11-09

- Adding a contact asks whether to share your location with them, and for how long: one hour,
  until the end of the day, or until you stop. The switch is in the contact's card.
- The map follows the layout of Find My: the contact list is a panel over the full map, and the
  buttons at the bottom right show everyone, someone abroad or you.
- The app asks for your name once at the start, which you can skip. A request without a name
  shows Unknown.
- An iPhone that stays in one place keeps sharing, so its contacts no longer lose it after a
  day.
- The Me tab fits one screen. Share now, the last share and problem reports are under Check
  setup.
- Finnish, when the phone's language is Finnish.
- Relays: relay.nostr.net replaces nos.lol, which stopped answering, and problem reports go to
  offchain.pub and relay.primal.net.

## [0.2.0] - 2026-11-06

- Sharing is off until you turn on Share my location on the Me tab. Until then you only watch
  your contacts, and the app needs no location permission.
- The contact list is a panel over the map. Tap a contact, or their picture on the map, to see
  the district or town they were in, how long ago, how far away, and directions there.
- A moving contact's estimated position follows the roads for a few minutes.
- Give each contact a photo, cropped to a circle.
- Invites are links that open the app, and adding someone sends them a request to add you back
  with one tap.
- Check setup on the Me tab tests everything sharing needs.
- Problem reports, off until you turn them on, tell the developer about a failed share or a
  crash, without your key, names or positions.
- Dark mode.

## [0.1.0] - 2026-11-05

First release: share your location from an iPhone with contacts you approve, and watch
contacts from Android without any permission. Locations are end-to-end encrypted Nostr events
that expire after 24 hours; there is no server of its own.
