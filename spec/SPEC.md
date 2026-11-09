# Mismeet protocol specification

Version 1, draft for review, 2026-10-07.

Mismeet is a location sharing app in the style of Find My, built on Nostr, for iOS and
Android. Each phone publishes its own location to Nostr relays as one addressable event,
encrypted so that only approved contacts can read it. Contacts fetch the latest event when they
want to see where someone is. There is no server of our own, no push service and no bridge.

This document defines the wire protocol and the behaviour both apps must share. It does not
define user interface. The key words MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are to be read
as in RFC 2119.

## 1. Terminology

- Publisher: a device publishing its location.
- Reader: a device fetching a contact's location. Every device is both a publisher and a reader.
- Contact: a public key the user has added on their device, with a local display name.
- Location event: the kind `31122` event defined in section 5.
- Entry: one NIP-44 ciphertext inside the content of a location event.
- Real entry: an entry a contact can decrypt. Dummy entry: padding that nobody can decrypt.
- Fix: one position report from the platform location API.
- Add-back request: the message of section 4.5, which tells a contact that the user added them
  and carries the user's own invite.
- Problem report: the message of section 8.8, which tells the app's developer what went wrong on
  a phone without telling them whose phone it was.

## 2. Design summary

| Decision | Choice |
| --- | --- |
| Event | Addressable kind `31122`, `d` tag `location`, one per publisher |
| Content | JSON with a list of NIP-44 v2 ciphertexts, one per approved contact, padded with dummies |
| Tags | `d` and `expiration` only, never a `p` tag |
| Expiration | NIP-40, 24 hours after `created_at` |
| Contacts | Local only, exchanged as `nostr:nprofile` invites, adding is approving |
| Add-back request | NIP-59 gift wrap to a newly added contact, carrying the adder's invite |
| Problem report | Opt-in NIP-17 message to the reports key, sealed by a throwaway key |
| Relays | Per-user list, published as NIP-65 kind `10002`; readers query each contact's relays |
| Keys | One secp256k1 key pair per app install, kept in the platform key store |

## 3. Identity and keys

Each app install generates its own secp256k1 key pair on first launch. The secret key is stored in
the iOS Keychain with an access class that allows use after the first unlock, and in
Android Keystore backed storage, so that background publishing works after a reboot once the
phone has been unlocked.

The app MUST NOT publish a kind `0` profile, a kind `3` contact list or any NIP-51 list for this
key. The key has no public profile: contacts name each other locally.

Importing an existing key is out of scope for version 1, as are key backup and key rotation.
A person with two phones appears to their contacts as two contacts.

## 4. Contacts

### 4.1 Invite

A user shares their identity as an invite link, an `https` URL whose fragment is a NIP-19
`nprofile`:

```
https://zululima.github.io/mismeet/i#nprofile1...
```

The `nprofile` carries the user's public key (TLV type 0) and up to four of the user's relays
(TLV type 1), as absolute `wss://` URLs. The app shows the link as a QR code and as copyable
text. Chat apps make an `https` link tappable, which they do not for a custom scheme. On a phone
with the app, the link opens the app (an iOS universal link, an Android verified app link) on
adding a contact, with the invite filled in. Without the app, the page at that address links to
the app stores and shows the invite to paste after installing. A browser never sends the
fragment to the server, so the web host sees no key.

The app MUST also accept the NIP-21 URI `nostr:nprofile1...`, which earlier versions shared, the
link `https://lambdakilo.github.io/mismeet/i#nprofile1...`, which later ones shared, a bare
`nprofile1...` and a bare or `nostr:` prefixed `npub1...`. The page at the earlier link's
address forwards to the current one with the fragment kept.
An invite without relays gets the app's default relay list until a kind `10002` is seen
(section 7.1).

An invite contains no secret. It should still be exchanged in person or over a channel the user
trusts, because whoever substitutes their own key in an invite receives the location meant for
the invitee.

### 4.2 Adding is approving

Adding an invite, opened as a link, scanned or pasted, creates a contact record on the device
that added it, with a display name typed by that user. Opening a link only fills in the invite;
the contact exists once the user confirms. Adding a contact has two effects on the adder's device:

- Sharing: the adder's location events include a real entry for the contact.
- Watching: the adder's reader fetches and decrypts the contact's location events.

Sharing can be switched on and off per contact (`share` flag), in the contact's card. A new
contact starts with `share` off: right after adding, the app asks whether to share the user's
location with them for one hour, until the end of the day or indefinitely, and only one of
those answers turns `share` on, together with sharing itself (section 8.1) when that was off.
Switching `share` on in the card asks the same. The end of the day is the next midnight in the
phone's time zone at the time of the answer. A share for a limited time ends as section 8.3
describes. A stored record without the flag, written before this question existed, reads it as
on, without an end. Watching is always on;
when the contact has not added the adder, the reader finds no entry it can decrypt and shows the
contact as not sharing.

Mutual sharing therefore needs each person to add the other. One-way sharing needs only one
side to add. Once the adder shares with a new contact, the adder's device sends them one add-back
request (section 4.5), so that they can add the adder back without a second invite. The request approves
nothing and has no answer: whether an entry decrypts stays the only signal of sharing. Nothing is
sent when a contact is removed.

### 4.3 Contact record

Each contact record holds, locally only:

- public key (32 bytes),
- display name,
- relay URLs, from the invite and later from kind `10002` (section 7.1),
- `share` flag, and the time the share ends when it was given for a limited time,
- whether its add-back request was sent or is not needed (section 4.5),
- the latest decrypted payload with its event `created_at` and event id (section 8.4).

The contact list never leaves the device. Backup of the contact list is out of scope for
version 1.

### 4.4 Removal

Removing a contact, switching `share` off or reaching the end of a limited share triggers a
publish without that contact (section 8.3). Data the removed contact already fetched stays on their device.

### 4.5 Add-back request

An add-back request tells a contact that the user added them and carries the user's own invite,
so that the contact adds the user back with one confirmation instead of asking for a link.

The request is a NIP-59 gift wrap: a kind `1059` event signed by a throwaway key, with one `p`
tag naming the contact and a NIP-40 `expiration` tag, whose content seals a rumor for the contact
with NIP-44. The seal, kind `13`, is signed by the user's key. The rumor is unsigned, kind `1122`,
with the user's public key, the time of sending as `created_at`, no tags, and this content:

```json
{"v":1,"invite":"nprofile1...","name":"Akira"}
```

- `v` is `1`.
- `invite` is the user's `nprofile` with up to four of the user's relays, as in section 4.1.
- `name` is the name the user gave themselves on the device, at most 64 characters, and is
  omitted when they gave none. The app asks for it once, at its first start, and the user may
  skip the question; a request without a name shows its sender as unknown.

The wrap's `created_at` is randomised up to two days into the past, as NIP-59 says, and its
`expiration` is that `created_at` plus 30 days.

A device sends a request:

- only while sharing is on (section 8.1), so contacts added while it was off get theirs when it
  is turned on;
- to a contact whose `share` flag is on, once per contact;
- not to a contact added back from that contact's own request, nor to one already seen sharing
  with the user, since both have added the user;
- to the contact's relay set (section 7.1), succeeding when one relay answers `OK` with `true`.

A device reads requests from its own relays whenever it fetches (section 8.2), with one filter of
`kinds` `[1059]`, `#p` its own key and `limit` 100, on connections apart from the location fetch,
so that no relay sees the device's key in the same subscription as the authors it asks about
(section 7.3). It discards a wrap that has expired, that it
cannot unwrap, whose rumor is not kind `1122` or not by the seal's key, whose `v` is not `1`,
whose `invite` names another key, or that came from itself. It keeps the newest request per
sender and shows those whose sender is not a contact and has not been dismissed by the user.
Adding back creates a contact as in section 4.2, with the invite filled in and the name offered
for the user to confirm or change; a request never adds a contact by itself.

## 5. Location event

```json
{
  "kind": 31122,
  "pubkey": "<publisher public key, 32 bytes lowercase hex>",
  "created_at": 1759840123,
  "tags": [
    ["d", "location"],
    ["expiration", "1759926523"]
  ],
  "content": "{\"v\":1,\"c\":[\"AtK3...\",\"AnQ9...\",\"Ag7c...\",\"AuPx...\"]}",
  "id": "<sha256 of the serialised event>",
  "sig": "<schnorr signature>"
}
```

- `kind` is `31122`, an addressable kind (NIP-01: 30000 to 39999). It is not registered in the
  NIPs repository; section 13 explains the choice.
- `tags` MUST be exactly one `d` tag with value `location` and one `expiration` tag, in that
  order. No other tag is allowed. In particular there is no `p`, `g`, `client` or `alt` tag,
  so the event reveals nothing about recipients, place or software.
- `created_at` is the publish time in Unix seconds. The publisher MUST use a value greater than
  the `created_at` of its previous location event, so that relays replace the old event
  (NIP-01 keeps the newest event per kind, pubkey and `d`, and the lowest id on a tie). If the
  clock has gone backwards, the publisher uses the previous value plus one.
- `expiration` is `created_at + 86400` as a decimal string (section 6).
- `content` is compact JSON (no whitespace) with the object below.

### 5.1 Content

```json
{"v":1,"c":["<entry>","<entry>","<entry>","<entry>"]}
```

- `v`: integer content version, `1`.
- `c`: array of entries. Each entry is a NIP-44 v2 payload as a base64 string, exactly as the
  NIP-44 `encrypt` function returns it.

The array holds one real entry per contact with `share` on, plus dummy entries up to the bucket
size, in random order (section 5.4). The array length is one of 4, 8, 16, 32 or 64. A payload
of section 5.2 pads to 128 bytes under NIP-44, so an entry is 260 base64 characters and an
event with 64 entries is about 17 KB; relays that cap event size below 32 KB are unsuitable
(section 7.4).

### 5.2 Payload

Every real entry in one event encrypts the same plaintext: the payload JSON below, compact,
with the keys in exactly this order.

```json
{"v":1,"ts":1759840101,"lat":60.169856,"lon":24.938379,"acc":12,"alt":21,"spd":1.4,"hdg":270,"bat":0.73}
```

| Key | Type | Required | Meaning |
| --- | --- | --- | --- |
| `v` | integer | yes | Payload version, `1` |
| `ts` | integer | yes | Time of the fix, Unix seconds |
| `lat` | number | yes | WGS84 latitude in degrees, at most 6 decimals |
| `lon` | number | yes | WGS84 longitude in degrees, at most 6 decimals |
| `acc` | integer | yes | Horizontal accuracy radius in metres, as the platform reports it |
| `alt` | integer | no | Altitude in metres above the WGS84 ellipsoid |
| `spd` | number | no | Ground speed in metres per second, at most 1 decimal |
| `hdg` | integer | no | Course over ground in degrees clockwise from true north, 0 to 359 |
| `bat` | number | no | Battery level from 0 to 1, at most 2 decimals |

Rules:

- Numbers are written without exponent notation and without a leading plus sign.
- An optional key is omitted when the platform reports the value as unknown: on iOS a negative
  `verticalAccuracy`, `speed` or `course`; on Android `hasAltitude()`, `hasSpeed()` or
  `hasBearing()` returning false. Altitude on iOS comes from `ellipsoidalAltitude`, not
  `altitude`, so that both platforms report the same datum.
- A fix with unknown horizontal accuracy (negative on iOS) MUST NOT be published.
- `hdg` is the direction of travel, not the direction the phone is facing.
- Readers MUST ignore keys they do not know and MUST reject a payload whose `v` is not `1` or
  whose required keys are missing or of the wrong type.

### 5.3 Encryption

Each real entry is `nip44_encrypt(publisher_secret_key, contact_public_key, payload, v2)`.
NIP-44 version 2 only. The conversation key is symmetric, so the contact decrypts with
`nip44_decrypt(contact_secret_key, publisher_public_key, entry)`.

Each dummy entry is `nip44_encrypt(publisher_secret_key, throwaway_public_key, filler, v2)`,
where `throwaway_public_key` belongs to a key pair generated for that entry with a
cryptographically secure random generator and discarded, and `filler` is a random string of
ASCII letters and digits with the same byte length as the payload. NIP-44 pads by plaintext
length, so a dummy has exactly the same length as a real entry and the same structure, and
cannot be told apart without a key.

### 5.4 Padding and order

Let `n` be the number of contacts with `share` on. The bucket size is the smallest of 4, 8, 16,
32 and 64 that is at least `n`. The publisher adds `bucket - n` dummy entries, then shuffles the
whole array with a cryptographically secure random generator. More than 64 sharing contacts is
not supported in version 1: the app refuses to switch sharing on for a 65th contact.

Readers MUST NOT rely on the order of entries.

### 5.5 Reading an event

For one event from one contact, a reader:

1. Verifies the signature, that `pubkey` is the contact's key, that `kind` is `31122` and
   that the `d` tag is `location`.
2. Rejects the event if the `expiration` tag is missing, is not a decimal integer, or is not
   later than the current time (section 6).
3. Parses `content`; rejects the event if it is not an object with `v` equal to `1` and `c` an
   array of strings.
4. Tries `nip44_decrypt` on each entry in turn with its own secret key and the contact's
   public key. A failure is expected for every entry that is not meant for this reader and
   is not an error. The first entry that decrypts to a valid payload (section 5.2) is the
   result.
5. If no entry decrypts, the contact is not sharing with this reader.

With at most 64 entries and one ECDH per attempt this is a few milliseconds per contact.

## 6. Expiration

Every location event carries a NIP-40 `expiration` tag of `created_at + 86400` (24 hours).

Relays that support NIP-40 drop the event after that time and stop serving it. Many relays do
not, so readers enforce the tag themselves: an event whose expiration has passed is treated as
absent, whatever the relay returns.

The tag bounds how long a location stays readable on a relay after the phone stops publishing,
and how long a removed contact can still fetch the last event that included them when the
publisher could not republish. It does not delete anything a reader already fetched; readers
keep the last payload locally and show its age (section 8.4).

NIP-40 says clients SHOULD NOT send expiring events to relays that do not advertise the NIP.
Mismeet sends the same signed event to all of the publisher's relays anyway: an addressable
event is replaced by the next publish on every relay, and readers enforce the expiration.
Section 13 lists this deviation.

## 7. Relays

### 7.1 Relay lists

Each user has a relay list in the app, two to four `wss://` URLs, seeded from the app's default
list. The app offers no way to view or edit it. The list is used for both writing and reading.

The publisher advertises its list as a NIP-65 kind `10002` event with one `r` tag per relay and
no marker (read and write), empty content, published to every relay on the list. It is
published on first run and whenever the list changes.

A reader keeps a relay set per contact: the relays from the contact's invite, merged with the
`r` tags without marker or with marker `write` from the newest kind `10002` of that contact.
Readers refresh a contact's kind `10002` at most once per 24 hours, and only from relays
already in that contact's set. A reader uses at most eight relays per contact; if more are
known it takes the first eight from the newest source.

### 7.2 Publishing

1. Build the payload from the latest fix (section 8.1).
2. Build the entries (sections 5.3 and 5.4).
3. Choose `created_at` as described in section 5 and sign the event.
4. Send the event to every relay in the publisher's list, in parallel, and wait for each
   relay's `OK`. The publish succeeds when at least one relay answers `OK` with `true`.
   Record `created_at` as the previous value for the next publish.
5. An `OK` with `false` whose message starts with `rate-limited:` makes the publisher wait at
   least five minutes before publishing to that relay again. Other failures are logged.

Timeouts: five seconds to connect, ten seconds to receive `OK`, and at most 25 seconds for the
whole publish, so that an iOS background wakeup finishes inside its budget.

### 7.3 Fetching

The reader groups its contacts by relay. To each relay it sends one filter with `kinds`
`[31122]`, `authors` set to the contacts whose relay set includes that relay, and `#d`
`["location"]`. A reader MUST NOT send a relay the keys of contacts not associated with it:
the author list is the reader's contact list, and each relay should see only its own part.

The reader closes the subscription after `EOSE`, or MAY keep it open while the map is on screen
to receive new events as they arrive.

For each author the reader first discards expired events, then keeps the event with the
greatest `created_at`, and on a tie the lowest id in lexical order, across all relays. It then
reads that event as in section 5.5.

Readers do not authenticate to relays (NIP-42) in version 1, because authenticating would tell
the relay which key is asking about which authors. A relay that demands `AUTH` to serve the
event is unusable for reading; section 13 records this. Add-back requests are fetched in the
same way, unauthenticated, with the filter of section 4.5.

### 7.4 Relay requirements

A relay is suitable when it:

- accepts kind `31122` and kind `10002` from any public key, without `AUTH`, payment or
  proof of work,
- replaces addressable events as NIP-01 describes,
- allows events of at least 32 KB (NIP-11 `limitation.max_message_length` and
  `max_content_length`, when published),
- preferably lists NIP-40 in its NIP-11 `supported_nips`,
- serves events to unauthenticated readers,
- accepts kind `1059` from any public key and serves it by `#p` to unauthenticated readers, for
  add-back requests (section 4.5).

The app's default relays are chosen against this list: `wss://relay.damus.io`,
`wss://relay.nostr.net`, `wss://relay.primal.net`. The relay probe of the Android `protocol`
module tests a relay against it. On 2026-11-09 all three accepted kinds `31122`, `10002` and
`1059` from a new key, replaced the location event and served the newest back, but only
relay.primal.net served a kind `1059` to a reader asking by `#p` without `AUTH`; relay.damus.io,
relay.nostr.net and relay.snort.social served it to nobody, while offchain.pub, nostr.oxtr.dev
and nostr-pub.wellorder.net did. Requests to a default user therefore reach them through
relay.primal.net. nos.lol, a default until then, answered no event within the `OK` timeout
from 2026-11-05 on; an app that stored a relay list holding it replaces it with
relay.nostr.net and publishes the new kind `10002` once sharing is on.

## 8. Client behaviour

### 8.1 When to publish

Sharing is off until the user turns it on. Until then the device only watches its contacts: it
publishes nothing, kind `10002` and add-back requests included, and needs no location
permission. Problem reports (section 8.8) have a setting of their own and carry no key of the
user's. Turning sharing off counts as switching `share` off for every contact (section 8.3).

A publisher publishes when:

- iOS: a significant location change or leaving the region around the last fix wakes the app,
  the background refresh runs, the app comes to the foreground, or the user asks for a refresh;
- Android: the foreground service's location timer fires (default every 10 minutes, whether or
  not the position changed), the app comes to the foreground, or the user asks for a refresh;
- either platform: a contact is removed, its `share` flag changes or its share ends, the relay
  list changes, or the user turns sharing on or off; turning it on also publishes the relay list.

Publishing is throttled to at most once per 60 seconds, except for the contact and relay list
changes above. The fix used MUST be at most one hour old; if no such fix exists the publisher
requests one, and skips the publish if none arrives in time. The previous event then stays on
the relays until it expires.

Android publishes on a fixed timer so that publish times tell an observer nothing about
movement. iOS cannot run background work on a timer without a push service, so it publishes
on movement and asks for refreshes:

- After each fix it uses, it monitors a circular region of 150 m around that fix; leaving it
  wakes the app, which asks for a fix and publishes. A significant location change still wakes
  it after about 500 m when the region was not left first.
- After each publish it asks the system for a background app refresh no earlier than four hours
  later, or at the end of the soonest limited share when that comes first. A refresh asks for a
  fix and publishes, so that a phone that stays in one place replaces its event before it
  expires (section 6). The system picks the time, later than asked when the app is seldom
  used, and never runs it while Background App Refresh is off for the app.

iOS publish times therefore follow the phone's movement; section 9 lists this leak.

### 8.2 When to fetch

A reader fetches when the app comes to the foreground, when the user asks for a refresh, and
every 60 seconds while the contact list or map is on screen. Each fetch also asks for add-back
requests (section 4.5), also while the user has no contacts yet. There is no background fetching
on either platform in version 1.

### 8.3 Revocation and the empty recipient set

When a contact is removed or its `share` flag is switched off, the publisher publishes
immediately with the new recipient set, using the cached fix if it is at most one hour old. If
no fix that recent exists, it publishes an event whose entries are all dummies, with a
`filler` length of 100 bytes, so that the old event is replaced at once. If the device has
never published there is nothing to revoke.

When no contact has `share` on, the publisher stops publishing after that one revocation
event.

A share for a limited time ends at its end time: no event published after it has an entry for
that contact. The first time the app runs after the end, for a publish, a wakeup, a refresh or
the foreground, it switches `share` off for that contact and publishes as above. Until then the
contact can read the last event that included them, with a fix from before the end.

### 8.4 Reader cache

For each contact the reader stores the latest valid payload with the event's `created_at` and
id. An event with a `created_at` lower than the stored one is ignored, which blocks a relay
from replaying an older unexpired event. When a fetch finds no valid event, the reader keeps
showing the cached payload, marked stale, with the age of `ts`. Removing a contact deletes the
cached payload.

### 8.5 Clocks

Both roles use the device clock. Relays reject events whose `created_at` is far in the future;
the publisher logs the relay's `OK` message in that case. Expiration checks compare the tag
against the device clock without tolerance.

### 8.6 Estimated position

Between fixes a reader shows where a moving contact probably is now, guessed along the roads
from the last payload. The estimate is for display only: it is never published or cached as a
payload, and it is drawn apart from the real position, which stays where the fix put it.

A contact gets an estimate when its payload has `hdg` and an `spd` of at least 1 m/s, and its
`ts` is at most 900 seconds old. The estimate covers `min(now - ts, 300)` seconds, so its
length is `spd` times that span. The mode follows from `spd`: on foot below 2.5 m/s, by bike
from 2.5 m/s, by car from 8.5 m/s.

Road data are OpenStreetMap ways from the Overpass API, one grid cell at a time. A position's
cell is `(floor(lat * 100), floor(lon * 50))`, so a cell spans 0.01 degrees of latitude and
0.02 of longitude, and its bounding box is `(row / 100, col / 50, (row + 1) / 100, (col + 1) /
50)` written with two decimals. The reader requests whole cells only, never an area centred
on a contact, so the server learns the areas a map of them would show, not positions. The
query for a cell is:

```text
[out:json][timeout:25];
way["highway"~"^(motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|road|cycleway|path|track|footway|pedestrian)$"](south,west,north,east);
out tags geom;
```

Cells are cached on the device for 30 days. Ways that share a coordinate, compared at 7
decimals, are joined there.

The ways a mode may use, by `highway` value:

- car: `motorway`, `trunk`, `primary`, `secondary`, `tertiary`, their `_link` variants,
  `unclassified`, `residential`, `living_street` and `road`. A way with `oneway` `yes`, `true`
  or `1`, or with `junction` `roundabout` or `circular`, is used only in its drawn direction,
  and one with `oneway` `-1` only against it.
- bike: `cycleway`, `path`, `track`, `living_street`, `residential`, `unclassified`, `road`,
  `tertiary`, `secondary`, `primary` and their `_link` variants, and `footway` or `pedestrian`
  with `bicycle` `yes` or `designated`; never a way with `bicycle` `no`.
- foot: `footway`, `pedestrian`, `path`, `track`, `cycleway`, `living_street`, `residential`,
  `unclassified`, `road`, `tertiary`, `secondary`, `primary` and their `_link` variants;
  never a way with `foot` `no`.

Bike and foot use every way in both directions. Distances and bearings are taken on a plane
around the fix: `x = (lon - lon_fix) * cos(lat_fix) * 111320` and `y = (lat - lat_fix) ×
110574` metres, bearings in degrees clockwise from north. The reader then:

1. Loads the cells of the fix and of the four points 50 m north, south, east and west of it.
2. Snaps the fix to the nearest segment of a usable way, in a usable direction whose bearing
   is within 60 degrees of `hdg`, at most 50 m away. Ties go to the smaller bearing
   difference, then the lower way id. With no such segment there is no estimate.
3. Travels from the snapped point along the segment. At each node it needs that node's cell,
   loading it first, and takes the usable segment leaving the node that turns least from the
   current bearing, never the one leading straight back. Ties go to the same way, then the
   lower way id. With no segment left it stops at the node.
4. Stops when it has travelled the estimate's length, or at a node whose cell it cannot load,
   or after loading 12 cells for this estimate.

The estimate is the point where it stopped and the path travelled to it. The reader shows the
path as a dashed line and the point as a faded marker labelled as estimated.

### 8.7 Place name

A reader names the district, town or village around a contact's last payload, such as
Kumpula for a position in Helsinki, rather than the city. The name is for display only and is
never published.

Names are OpenStreetMap place nodes from the Overpass API, one cell at a time, so that, as
with the road cells of section 8.6, the server learns areas and not positions. A position's
place cell is `(floor(lat * 10), floor(lon * 5))`, so a cell spans 0.1 degrees of latitude
and 0.2 of longitude, and its bounding box is `(row / 10, col / 5, (row + 1) / 10, (col + 1) /
5)` written with one decimal. The query for a cell is:

```text
[out:json][timeout:25];
node["place"~"^(suburb|town|village|city)$"]["name"](south,west,north,east);
out;
```

Cells are cached on the device for 30 days. With distances on the plane of section 8.6 around
the position, the reader:

1. Loads every cell that the box from 5 km south-west to 5 km north-east of the position
   touches. With any of them unavailable there is no name.
2. Takes the nearest node whose `place` is `suburb`, `town` or `village` and that lies at most
   5 km away, ties going to the lower node id.
3. With no such node, takes the nearest `city` node at most 5 km away in the same way. With
   none of those either there is no name.

The name is that node's `name` tag.

### 8.8 Problem reports

A problem report tells the app's developer what went wrong on a phone. Sending reports is a
setting of its own, off until the user turns it on, and apart from sharing.

A report is a NIP-17 direct message to the reports key: a NIP-59 gift wrap as in section 4.5,
whose rumor is kind `14` with one `p` tag naming the reports key and plain text as content.
Unlike an add-back request, the seal is signed by a key made for that one report and then
forgotten, never by the user's key, so neither a relay nor the holder of the reports key can tie
a report to the user's key. The wrap's `expiration` is its `created_at` plus 30 days.

The text is at most 8000 characters, cut at the end. It holds:

- why it was sent: a failed share, a crash on an earlier run, or the user asking;
- the app's version and build, the platform and its version, and the phone model;
- the result of each setup check, with contacts counted rather than listed;
- for a crash, its type and stack;
- the app's last 50 log lines at info level or above.

Before sending, a client removes from the text every invite link, NIP-19 entity, string of 64
hex digits, relay URL outside the app's defaults and coordinate pair, and every name the user
gave a contact. A report never holds a position, a place name, a contact's name or key, the
user's key or invite, or a photo.

A client sends a report:

- when the user asks for one, after showing them its text, also with the setting off;
- with the setting on, when a share fails or after the app crashed, at most once per 24 hours;
  a crash waits until that allows, and is forgotten when the setting is off.

Reports go to the report relays of section 10, not to the user's relays, so that they reach the
reader of the reports key. The reports key and the report relays are those of whoever builds
and ships the app; a fork uses its own.

## 9. Privacy and threat model

What a relay operator, or anyone reading the relay, learns from the protocol:

- that a public key uses Mismeet (the kind), and which relays it uses;
- when it publishes, which on iOS correlates with movement and with the times the system ran a
  background refresh (section 8.1);
- a bucketed count of recipients: 4, 8, 16, 32 or 64;
- the reader's IP address and the authors it asks about on that relay, at fetch time, without
  the reader's key (section 7.3);
- that some key sent a kind `1059` to a public key, within two days of when, and that this
  public key asks for its own: the wrap names the recipient in its `p` tag and is signed by a
  throwaway key (section 4.5). Kind `1059` also carries NIP-17 direct messages, so the wrap alone
  does not mark the recipient as a Mismeet user;
- that some key sent a kind `1059` to the reports key, and the sender's IP address. A relay that
  also sees that address publish a location event can guess which key sent a problem report
  (section 8.8), but not what it says.

What they do not learn:

- any location, past or present;
- who the recipients are, or whether two keys are contacts, from the events alone;
- who sent an add-back request, or what it holds;
- what a problem report holds;
- the exact number of recipients.

Other properties:

- Events are signed, so a relay cannot forge or alter a location. It can withhold events or
  serve an older unexpired one; the reader cache (section 8.4) limits replay to the newest
  event the reader has seen, and expiration bounds it to 24 hours.
- A removed contact keeps what they already fetched, and can fetch the last event that
  included them until it is replaced or expires (section 8.3).
- A relay that sees both a publisher's event and a reader's query for that author links the
  two by IP address and time. Querying through Tor or a VPN is out of scope for version 1.
- A reader that shows estimates (section 8.6) asks the Overpass API for the road cells around
  contacts that move, which tells that server the reader's IP address and those cells, much
  as map tiles of the same area would.
- A reader that shows place names (section 8.7) asks the Overpass API for the place cells,
  about 10 km across, around every contact with a known position, which tells that server the
  reader's IP address and those cells.
- The holder of the reports key reads each problem report, but learns from it no key, contact,
  position or relay outside the defaults; the phone model, the app version and the times in it
  remain.
- Device compromise, and keys extracted from an unlocked phone, are out of scope.

## 10. Constants and limits

| Name | Value |
| --- | --- |
| Event kind | `31122` |
| `d` tag | `location` |
| Gift wrap kind | `1059` |
| Add-back rumor kind | `1122` |
| Add-back content version | `1` |
| Add-back expiration | wrap `created_at + 2592000` seconds |
| Add-back requests fetched per relay | at most 100 |
| Name given in an add-back request | at most 64 characters |
| Problem report rumor kind | `14`, NIP-17 chat message |
| Problem report expiration | wrap `created_at + 2592000` seconds |
| Problem report text | at most 8000 characters |
| Automatic problem reports | at most one per 86400 seconds |
| Log lines in a problem report | at most 50 |
| Reports key | `npub1zvqr2zn45kdr0epvfc600z92sc7fdmhf8p46dz3e6tmwug8fk7fquge3l4` |
| Report relays | `wss://relay.nostr.net`, `wss://relay.primal.net` |
| Content version | `1` |
| Payload version | `1` |
| Expiration | `created_at + 86400` seconds |
| Entry buckets | 4, 8, 16, 32, 64 |
| Maximum sharing contacts | 64 |
| Relays per user list | 2 to 4 |
| Relays queried per contact | at most 8 |
| Kind `10002` refresh | at most once per 24 hours per contact |
| Publish throttle | 60 seconds |
| Maximum fix age at publish | 3600 seconds |
| Android publish interval | 600 seconds |
| iOS region radius | 150 m |
| iOS background refresh, earliest | 14400 seconds after the last publish |
| Limited share durations | 3600 seconds, or until the next local midnight |
| Foreground fetch interval | 60 seconds |
| Connect timeout | 5 seconds |
| `OK` timeout | 10 seconds |
| Whole publish timeout | 25 seconds |
| Rate-limit backoff | 300 seconds |
| Invite link | `https://zululima.github.io/mismeet/i#` followed by the `nprofile` |
| Earlier invite link, accepted only | `https://lambdakilo.github.io/mismeet/i#` followed by the `nprofile` |
| Coordinate precision | 6 decimals |
| Dummy filler length when no fix exists | 100 bytes |
| Estimate minimum speed | 1 m/s |
| Bike speed from | 2.5 m/s |
| Car speed from | 8.5 m/s |
| Estimate span | 300 seconds |
| Estimate maximum fix age | 900 seconds |
| Road snap distance | 50 m |
| Road snap bearing tolerance | 60 degrees |
| Road cells per estimate | 12 |
| Road cell cache | 30 days |
| Place cell cache | 30 days |
| Place name distance | 5 km |

## 11. Versioning

The content `v` and the payload `v` are independent. A reader that meets an unknown content
version ignores the event. A reader that decrypts a payload with an unknown version ignores the
entry and keeps trying the others. A future version that changes the event shape in a way an
old reader would misread uses a new `d` tag value.

## 12. Conformance checklist

Both apps MUST behave identically on every point below. Each point is a unit test on each
platform, and the protocol constants live in a single constants file per platform.

1. Payload JSON is compact, keys in the order of section 5.2, numbers without exponents,
   coordinates rounded to 6 decimals, optional keys omitted when unknown.
2. Entry count is the bucket of section 5.4, dummies are built as in section 5.3, and the
   array is shuffled with a secure random generator.
3. The event has exactly the two tags of section 5, in that order, and `expiration` equals
   `created_at + 86400`.
4. `created_at` is strictly greater than the previous published value.
5. A reader rejects events with a missing, malformed or past `expiration`.
6. A reader picks the newest event across relays, lowest id on a tie, and ignores events older
   than its cache.
7. A reader accepts a payload with unknown extra keys and rejects one with a missing required
   key or the wrong `v`.
8. A reader sends each relay only the authors associated with that relay.
9. Invites round-trip through the invite link and `nostr:nprofile` with up to four relays, and
   the earlier invite link and `npub` input are accepted.
10. Nothing is ever published except kinds `31122` and `10002`, kind `1059` add-back requests
    and kind `1059` problem reports.
11. An estimate follows section 8.6: the mode from `spd`, the ways each mode may use, the
    snap, the least turn at each node, and the speed, span and age limits.
12. A place name follows section 8.7: the cells loaded, the place values and their order of
    preference, the 5 km limit and the tie rule.
13. An add-back request follows section 4.5: the content, the wrap's kinds, tags and expiration,
    each reason to discard one, and the newest request per sender.
14. A problem report follows section 8.8: the wrap's kinds, tags and expiration, a seal by a key
    other than the user's, the length limit and each removal from the text.

## 13. Deviations from NIPs and open questions for review

1. Kind `31122` is not registered. It was chosen from an unused part of the addressable range
   after checking the NIPs repository kind table on 2026-10-07. Kind `30078` (NIP-78
   application data) was rejected because NIP-78 asks relays to serve those events only to
   their authenticated author, which would stop contacts from reading them. If a collision
   with another project turns up before release, change the number; a NIP could be proposed
   later.
2. NIP-40: the expiration tag is sent to every relay, including ones that do not advertise
   NIP-40 (section 6).
3. NIP-42 is not used. Relays that demand `AUTH` for writing or reading are unsupported in
   version 1. Allowing `AUTH` for publishing only would be a small change; allowing it for
   reading would expose the reader's contact list to the relay per key.
4. Expiration of 24 hours: shorter means less exposure after a phone goes quiet, longer means
   contacts see a location after a longer offline period. Readers show cached data either way.
5. Padding to powers of two hides the exact recipient count at the cost of up to twice the
   bandwidth. Padding to multiples of four would be cheaper and leak more.
6. The Android publish interval of 10 minutes trades battery for freshness and for hiding
   movement in publish timing. iOS cannot offer the same.
7. Default relays need verification against section 7.4 before release.
8. Out of scope for version 1, possibly later: per-contact precision (coarse location for
   some contacts), key import, backup and rotation, multiple devices under one key, publishing
   through Tor.
9. Kind `1122`, the add-back rumor, is not registered either. It never reaches a relay on its own,
   only sealed inside a gift wrap that one key can open, so a collision would matter only to a
   client opening Mismeet's wraps with a Mismeet key, which is never imported elsewhere
   (section 3).
10. The add-back request uses NIP-59 without NIP-17: it is no direct message, and it goes to the
    contact's relay set rather than to kind `10050` inbox relays, which Mismeet users do not
    publish.
11. Problem reports are NIP-17 messages, so that a NIP-17 client holding the reports key can
    read them, but they go to the fixed report relays rather than to the kind `10050` inbox
    relays NIP-17 asks for, which the app does not look up.

## 14. References

- NIP-01, basic protocol flow and addressable events:
  https://github.com/nostr-protocol/nips/blob/master/01.md
- NIP-11, relay information document: https://github.com/nostr-protocol/nips/blob/master/11.md
- NIP-17, private direct messages: https://github.com/nostr-protocol/nips/blob/master/17.md
- NIP-19, bech32 entities and `nprofile`: https://github.com/nostr-protocol/nips/blob/master/19.md
- NIP-21, `nostr:` URI scheme: https://github.com/nostr-protocol/nips/blob/master/21.md
- NIP-40, expiration timestamp: https://github.com/nostr-protocol/nips/blob/master/40.md
- NIP-42, client authentication: https://github.com/nostr-protocol/nips/blob/master/42.md
- NIP-44, versioned encryption: https://github.com/nostr-protocol/nips/blob/master/44.md
- NIP-59, gift wrap: https://github.com/nostr-protocol/nips/blob/master/59.md
- NIP-65, relay list metadata: https://github.com/nostr-protocol/nips/blob/master/65.md
- NIP-78, application-specific data (rejected, see section 13):
  https://github.com/nostr-protocol/nips/blob/master/78.md
- Nostr Dev Kit, the Rust implementation and its Swift and Kotlin bindings, formerly
  rust-nostr: https://github.com/nostrdevkit/nostr and https://github.com/nostrdevkit/nostr-sdk-ffi
