# Mismeet privacy policy

Mismeet has no server and no account. Its developer receives nothing from the app unless you
turn on problem reports or send one yourself, and then only what the section on problem
reports below lists: never your location or contacts, and no analytics or advertising
identifiers. There is no tracking.

## What stays on your phone

The app keeps its own Nostr key pair, the contacts you add (their public keys, the names you
give them and their relays), their last known positions, and your relay list. Removing the app
deletes all of it.

## What leaves your phone

Only when you share your location, which is off until you turn it on:

- Your location goes to the Nostr relays in your relay list as one event, encrypted end to end
  separately for each contact you share with (NIP-44). It expires after 24 hours, and each new
  publish replaces the previous one. Relays cannot read the location or tell who it is for.
- Your relay list goes to the same relays, so that contacts know where to look.
- When you add a contact, an add-back request goes to their relays: your invite and the name
  you gave yourself on the Me screen, encrypted for that contact alone (NIP-59) and sent from a
  throwaway key, so that they can add you back. It expires after 30 days.

Sharing works in the background: once it is on, the app collects location data to share your
position with the contacts you choose, even when the app is closed or not in use. On Android a
notification shows while it runs.

When you look at your contacts, the app asks those relays for their latest events.

The relays are run by third parties under their own terms. The app uses relay.damus.io,
relay.nostr.net and relay.primal.net. A relay can see your public key, when you publish, your
IP address, roughly how many contacts you share with (rounded up to 4, 8, 16, 32 or 64) and,
when you fetch, which public keys you ask about. Section 9 of the
[protocol specification](spec/SPEC.md) describes this in detail.

## Problem reports

Problem reports are off until you turn on "Send problem reports" on the Me screen. With it on,
the app sends a report when a share fails or after the app crashed, at most once a day. You can
also send one from Check setup, which shows the whole report before it goes.

A report holds why it was sent, the app version, the iOS or Android version, the phone model,
the results of the setup checks, how many contacts you have and how many share with you, the
app's last 50 messages, and for a crash where in the app it stopped. Before sending, the app
removes keys, invite links, coordinates, relays you added yourself and the names you gave
contacts. A report never holds your location, a place name, a contact's name or key, your own
key or a photo.

The report is encrypted for the developer's reports key alone (NIP-17) and sent from a key made
for that one report, to the relays offchain.pub and relay.primal.net, which delete it after 30 days.
Neither the relays nor the developer can tell from the report whose phone sent it; the relays
see your IP address. The developer keeps reports to fix bugs and shares them with nobody.

## Your position, maps, roads and pictures

Your own position comes from the phone. On iOS that is Apple's location service. On Android,
Mismeet asks GPS and, at the same time, the phone's network location when one is turned on,
and uses the network's answer only when GPS finds nothing in time, as indoors. Network location
is the phone's own service, such as Google's Location Accuracy or GrapheneOS's network location,
which works the position out from nearby Wi-Fi networks or cell towers under that service's
policy. Turn it off in the phone's location settings to keep Mismeet on GPS alone.

On iOS the map comes from Apple Maps, under Apple's privacy policy. On Android it comes from
OpenStreetMap's tile servers, which see your IP address and the map area you view.

When a contact is moving, the app guesses where the road has taken them since their last
position. For that it downloads the OpenStreetMap roads around them from the Overpass API at
overpass-api.de, in fixed squares of about a kilometre that are never centred on anyone, and
keeps them on the phone for 30 days. That server sees your IP address and those squares, much
as map tiles of the same area would show, but no position.

To name the district, town or village a contact is in, the app downloads OpenStreetMap place
names from the same Overpass API, in fixed squares of about 10 km, and keeps them on the phone
for 30 days. The nearest name is picked on the phone, so that server again sees your IP
address and those squares, but no position.

The map opens on the people in your own country. Which country a position lies in is worked out
on the phone from borders that come with the app, so no position is sent anywhere for it.

An invite link opens a page on zululima.github.io, hosted by GitHub Pages, when Mismeet is
not installed. An earlier link on lambdakilo.github.io first opens a page there, also on GitHub
Pages, that forwards to it. The invite sits after the `#` in the link, which browsers do not
send, so GitHub sees your IP address and that the page was opened, but not the invite.

The camera is used only to scan invite QR codes; no image is stored or sent. A photo you choose
for a contact is stored on your phone only and is never sent.

## Stopping and removing

Switching sharing off for a contact, or removing the contact, publishes a replacement event
without them at once. A share for one hour or until the end of the day ends at that time: no
later event includes the contact, and the app publishes the replacement the next time it runs. Contacts keep what they already saw. Events left on relays expire after
24 hours.

## Contact

Questions go to the project's issue tracker: <https://github.com/zululima/mismeet/issues>.
