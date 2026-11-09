# Location sharing apps compared

Notes for Mismeet, researched 2026-11-09, so that the app works the way people already expect
from the location sharing apps they know. Each claim links to its source. Where no public source
was found, the text says so. "Mismeet today" means the iOS code in `ios/Mismeet/UI/` on
2026-11-09, judged by its intent while it moves toward Find My; the Android app was not checked.

## Apps covered

| App | What it is | Status |
| --- | --- | --- |
| Apple Find My | System app on iPhone: people, devices and items | Current |
| Google Maps location sharing | Sharing inside Google Maps | Current |
| Google Find Hub | Google's find app, also shows people who share with you | Current |
| Life360 | Family "Circles" | Current |
| WhatsApp live location | Time-limited sharing inside a chat | Current |
| Telegram live location | Time-limited or indefinite sharing inside a chat | Current |
| Snapchat Snap Map | Friends map inside Snapchat | Current |
| Zenly | Social friends map | Shut down 3 Feb 2023 |
| Bump | Friends map by Amo, the company of Zenly's founder | Current |
| whoo | Friends map from Japan by LinQ Inc. | Current |
| Glympse | Auto-expiring location link | Current |
| OwnTracks | Open source, self-hosted MQTT or HTTP | Current |
| Grid | Open source, end-to-end encrypted over Matrix | Current |
| Locus | Open source, end-to-end encrypted over Nostr | Discontinued 2024 |

Status sources: Zenly's shutdown date in
[https://techcrunch.com/2022/12/05/zenly-was-the-best-social-app-and-it-will-sadly-shut-down-on-february-3rd/](https://techcrunch.com/2022/12/05/zenly-was-the-best-social-app-and-it-will-sadly-shut-down-on-february-3rd/);
Amo and Bump in
[https://techcrunch.com/2023/11/19/with-amo-the-founder-of-zenly-wants-to-make-social-apps-social-again/](https://techcrunch.com/2023/11/19/with-amo-the-founder-of-zenly-wants-to-make-social-apps-social-again/)
and
[https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217](https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217);
whoo's operator, launch in December 2022 and over 30 million downloads by September 2025 in
LinQ's press release
[https://kyodonewsprwire.jp/release/202602194299](https://kyodonewsprwire.jp/release/202602194299);
Locus archived as discontinued in
[https://github.com/Myzel394/locus](https://github.com/Myzel394/locus).
whoo's App Store listing could not be found through Apple's search API from the US, Japan, Spain,
the UK or Finland, so whoo facts below come from the press release and one Japanese review.

## 1. Consent and sharing flow

### Apple Find My

- You share first, then ask to follow. People > Add > Share My Location, pick the friend, Done,
  then pick how long: an hour, until the end of the day, indefinitely, or a custom time.
  [https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios](https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios),
  [https://support.apple.com/en-us/105104](https://support.apple.com/en-us/105104)
- To see a friend who does not share with you, tap their name and Ask To Follow Location. A
  friend who sees you but does not share back shows "Can see your location".
  [https://support.apple.com/guide/iphone/add-or-remove-a-friend-ipha5f04100d/ios](https://support.apple.com/guide/iphone/add-or-remove-a-friend-ipha5f04100d/ios),
  [https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios](https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios)
- A request shows a Share button below the sender's name; tapping it asks for the duration.
  Cancel declines. Me > Allow Friend Requests off stops new requests.
  [https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios](https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios)
- Removing a friend removes each of you from the other's list.
  [https://support.apple.com/guide/iphone/add-or-remove-a-friend-ipha5f04100d/ios](https://support.apple.com/guide/iphone/add-or-remove-a-friend-ipha5f04100d/ios)
- Stopping: "The person that you stop sharing with won't be notified" (Apple 105104), while
  Apple's personal safety guide says people may notice, and that earlier systems sent a
  Messages notification. Family members appear in People automatically.
  [https://support.apple.com/en-us/105104](https://support.apple.com/en-us/105104),
  [https://support.apple.com/guide/personal-safety/find-my-and-location-sharing-ips05ede4573/web](https://support.apple.com/guide/personal-safety/find-my-and-location-sharing-ips05ede4573/web)
- Location alerts: a friend can be notified when you arrive, leave or are not somewhere. You get
  an alert when someone sets one, and a recurring one needs your approval. Me shows
  "Notifications About You".
  [https://support.apple.com/guide/iphone/notified-friends-change-location-iph843dd79b6/ios](https://support.apple.com/guide/iphone/notified-friends-change-location-iph843dd79b6/ios),
  [https://support.apple.com/guide/personal-safety/find-my-and-location-sharing-ips05ede4573/web](https://support.apple.com/guide/personal-safety/find-my-and-location-sharing-ips05ede4573/web)
- Not found: an Apple source on whether the recipient is notified when you start sharing.

### Google Maps and Find Hub

- Maps: your profile > Location sharing > New share, choose how long, pick the person, Share.
  The recipient needs a Gmail account and must be in your Google Contacts; others can get a link
  that works up to 24 hours. "Until you turn this off" is one of the durations. Through iMessage
  a share lasts 1 hour and can be extended to 3 days. Accounts under 18 are limited to 24 hours.
  [https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS)
- Find Hub durations: one hour, until the end of the day (11:59 PM), until you stop, or custom.
  Before sharing it previews what the other person sees: location, name, avatar, address and
  battery. The recipient gets a notification, or an email if they lack the app.
  [https://support.google.com/android/answer/15992026?hl=en](https://support.google.com/android/answer/15992026?hl=en)
- Request: tap Request on a person. They get your email address and a notification, and can
  share, say No or Block. Find Hub sends a notification and an email.
  [https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS),
  [https://support.google.com/android/answer/15992026?hl=en](https://support.google.com/android/answer/15992026?hl=en)
- Stop: tap the person, then Stop (Maps), or "Stop sharing" with a confirmation (Find Hub).
  [https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS),
  [https://support.google.com/android/answer/15992026?hl=en](https://support.google.com/android/answer/15992026?hl=en)

### Life360

- Sharing is per Circle, joined with an invite code that expires after 72 hours; the joiner sees
  a preview and accepts or declines.
  [https://support.life360.com/hc/en-us/articles/23053441527319-Join-a-Circle](https://support.life360.com/hc/en-us/articles/23053441527319-Join-a-Circle)
- Location Sharing is a switch per Circle; when off, the others see "Location Sharing Paused".
  SOS, Check In and crash detection still share.
  [https://support.life360.com/hc/en-us/articles/23053695148823-Share-My-Location](https://support.life360.com/hc/en-us/articles/23053695148823-Share-My-Location)
- Temporary sharing by link: 1, 4 or 8 hours, or until the end of the day, then Start Sharing;
  Stop Sharing ends it. The viewer sees live location, battery and activity.
  [https://support.life360.com/hc/en-us/articles/39407118334103-Temporary-Location-Sharing](https://support.life360.com/hc/en-us/articles/39407118334103-Temporary-Location-Sharing)

### WhatsApp

- Per chat: attach > Location > Share live location, pick a length, and it stops by itself.
  Stop sharing ends it in one chat; You > Privacy > Live location stops it in all. End-to-end
  encrypted. The chat keeps a static thumbnail and the last updated location.
  [https://faq.whatsapp.com/480865177351335/?cms_platform=iphone&locale=en_US](https://faq.whatsapp.com/480865177351335/?cms_platform=iphone&locale=en_US),
  [https://faq.whatsapp.com/6780014865351544/?locale=en_US](https://faq.whatsapp.com/6780014865351544/?locale=en_US)
- WhatsApp's help page does not list the lengths. Launch coverage in 2017 gives 15 minutes,
  1 hour or 8 hours.
  [https://fortune.com/2017/10/20/facebook-whatsapp-live-location-feature/](https://fortune.com/2017/10/20/facebook-whatsapp-live-location-feature/)

### Telegram

- Since 2017: 15 minutes, 1 hour or 8 hours; a bar at the top shows who you share with, and two
  taps stop sharing.
  [https://telegram.org/blog/live-locations](https://telegram.org/blog/live-locations)
- Since 25 Apr 2024: add more time to a running share, or share "until you turn it off".
  [https://telegram.org/blog/my-profile-and-15-more](https://telegram.org/blog/my-profile-and-15-more)
- A share carries its period, the heading and a proximity alert radius.
  [https://core.telegram.org/api/live-location](https://core.telegram.org/api/live-location)

### Snapchat Snap Map

- Off by default. Shared only with mutual friends. Audience: My Friends, "My Friends, Except..."
  or "Only These Friends". Friends you pick are not notified.
  [https://help.snapchat.com/hc/en-us/articles/24547077410580-Snap-Map-Privacy-Safety-Reminder](https://help.snapchat.com/hc/en-us/articles/24547077410580-Snap-Map-Privacy-Safety-Reminder),
  [https://help.snapchat.com/hc/en-us/articles/7012277077140-How-do-I-share-my-location-with-only-select-friends-on-Snapchat](https://help.snapchat.com/hc/en-us/articles/7012277077140-How-do-I-share-my-location-with-only-select-friends-on-Snapchat)
- Live Location with a close friend works while the app is closed, is limited in time, and can
  be paused without notifying them. Not found: its durations.
  [https://values.snap.com/news/looking-out-for-friends-on-the-snap-map](https://values.snap.com/news/looking-out-for-friends-on-the-snap-map)
- Snapchat reminds you from time to time that you are sharing.
  [https://help.snapchat.com/hc/en-us/articles/24547077410580-Snap-Map-Privacy-Safety-Reminder](https://help.snapchat.com/hc/en-us/articles/24547077410580-Snap-Map-Privacy-Safety-Reminder)

### Zenly, Bump, whoo

- Zenly (2016 review): hide your location from all friends or some, or show some only your city.
  [https://techcrunch.com/2016/05/19/zenly-solomoyolo/](https://techcrunch.com/2016/05/19/zenly-solomoyolo/)
- whoo: add friends by ID or QR code from a people-plus icon at the top right of the map; the
  other side must accept.
  [https://www.sungrove.co.jp/whoo/](https://www.sungrove.co.jp/whoo/)
- Not found: Zenly's own help pages (offline since the shutdown) and a source on Bump's flow.

### Glympse, OwnTracks, Grid

- Glympse: send a link; the viewer needs no app; the share expires by itself.
  [https://apps.apple.com/fi/app/glympse-share-your-location/id330316698](https://apps.apple.com/fi/app/glympse-share-your-location/id330316698)
- OwnTracks: the user runs the server; not found: a consent flow in the app.
  [https://owntracks.org/booklet/features/ios/](https://owntracks.org/booklet/features/ios/)
- Grid: one-to-one sharing with contacts, and long-term or temporary groups.
  [https://apps.apple.com/us/app/grid-private-location-sharing/id6736839927](https://apps.apple.com/us/app/grid-private-location-sharing/id6736839927)

### Summary

| App | Who starts | Durations | Ask to see someone |
| --- | --- | --- | --- |
| Find My | Sharer, per person | 1 h, end of day, indefinitely, custom | Ask To Follow Location |
| Google Maps, Find Hub | Sharer, per person | 1 h, end of day, until you stop, custom | Request |
| Life360 | Circle invite code | Ongoing per Circle; links 1, 4, 8 h, end of day | Not needed, the Circle shares |
| WhatsApp | Sharer, per chat | 15 min, 1 h, 8 h (2017 press) | None |
| Telegram | Sharer, per chat | 15 min, 1 h, 8 h, until turned off | None |
| Snap Map | Mutual friends, then audience | Ongoing; Ghost Mode timers | None found |
| whoo | Mutual friend request | Ongoing | Friend request |
| Glympse | Sharer, by link | Auto-expiring | None |
| Mismeet today | Adder; "Share your location with X?" after adding | Until turned off | Add-back request on adding |

Apps disagree on whether sharing is one-way per person (Find My, Google, Mismeet) or mutual once
both are friends (Snap Map, whoo, Life360 Circles).

## 2. Freshness

### How the last update is shown

- Find My: "Live" under the name means you see the friend move in real time; "Now" shows the
  last known location (iPhone Life, 22 Oct 2024). Live needs iOS 15 or later on both iPhones,
  and shows direction and speed (Apple). Not found: "Now" in Apple's own docs. A friend who
  cannot be located shows "No location found". On a Mac the location updates as the friend
  moves, and a blue circle means it is approximate.
  [https://www.iphonelife.com/content/what-does-live-mean-find-my](https://www.iphonelife.com/content/what-does-live-mean-find-my),
  [https://support.apple.com/en-us/105104](https://support.apple.com/en-us/105104),
  [https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios](https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios),
  [https://support.apple.com/guide/findmy-mac/locate-a-friend-fmmb20f3d01a/mac](https://support.apple.com/guide/findmy-mac/locate-a-friend-fmmb20f3d01a/mac)
- Find Hub: a person's details show address, last-updated timestamp and distance. After
  Refresh the timestamp changes to "now".
  [https://support.google.com/android/answer/15992026?hl=en](https://support.google.com/android/answer/15992026?hl=en)
- Life360: "Location permissions off", "Logged out of Life360", and a greyed-out icon for a
  member whose app lost connection. While you view a member the app gets a signal every two
  seconds, for up to 5 minutes; a refresh button shows a spinning circle.
  [https://support.life360.com/hc/en-us/articles/23053527071255-See-a-Circle-Member-s-Location](https://support.life360.com/hc/en-us/articles/23053527071255-See-a-Circle-Member-s-Location),
  [https://support.life360.com/hc/en-us/articles/23053466420887-Movement-Accuracy](https://support.life360.com/hc/en-us/articles/23053466420887-Movement-Accuracy)
- whoo shows how long someone has been at a place, whether they are online and their battery;
  Bump shows "how long they've been somewhere".
  [https://kyodonewsprwire.jp/release/202602194299](https://kyodonewsprwire.jp/release/202602194299),
  [https://www.sungrove.co.jp/whoo/](https://www.sungrove.co.jp/whoo/),
  [https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217](https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217)
- Snap Map with "Only while using" drops your location after 24 hours; with Always it stays
  until you turn it off or it expires.
  [https://help.snapchat.com/hc/en-us/articles/7012280385684-How-long-does-my-location-stay-on-Snap-Map](https://help.snapchat.com/hc/en-us/articles/7012280385684-How-long-does-my-location-stay-on-Snap-Map)
- Battery level is shown by Google (with charging state), Life360, Zenly, whoo and Bump.
  [https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS),
  [https://support.life360.com/hc/en-us/articles/39407118334103-Temporary-Location-Sharing](https://support.life360.com/hc/en-us/articles/39407118334103-Temporary-Location-Sharing),
  [https://techcrunch.com/2016/05/19/zenly-solomoyolo/](https://techcrunch.com/2016/05/19/zenly-solomoyolo/),
  [https://www.sungrove.co.jp/whoo/](https://www.sungrove.co.jp/whoo/),
  [https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217](https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217)
- Mismeet today: "Kumpula, seen 2 minutes ago" from the fix time. The payload carries battery
  (`bat` in `Protocol/LocationPayload.swift`), but the iOS UI does not show it.

### How the apps keep a still iPhone fresh

- Find My is a system service; not found: how it refreshes a still phone. For other apps Apple
  offers the Location Push Service Extension: a server sends a location push, the extension gets
  the location and sends it back. It needs a server, APNs, the entitlement
  `com.apple.developer.location.push` and Always permission.
  [https://developer.apple.com/documentation/corelocation/cllocationpushserviceextension](https://developer.apple.com/documentation/corelocation/cllocationpushserviceextension)
- Google Maps on iPhone "periodically refreshes your position" and sometimes shows a message when
  it does. With those notifications off, "your shared location doesn't refresh and can become
  inaccurate". Swiping the app away stops updates. The notification points to a server push
  that wakes the app; that is our inference, Google does not say how.
  [https://support.google.com/maps/answer/7667679?hl=en&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/7667679?hl=en&co=GENIE.Platform%3DiOS)
- Life360 asks for Always plus Precise, Motion & Fitness, Bluetooth and notifications including
  Time Sensitive. Not found: how it wakes a still phone.
  [https://support.life360.com/hc/en-us/articles/23053398402839-Life360-Permission-Settings](https://support.life360.com/hc/en-us/articles/23053398402839-Life360-Permission-Settings)
- Zenly (2016): it "doesn't track your location when nobody wants to see it"; when a friend
  opens the app, Zenly requests your location. That is on demand through Zenly's servers.
  [https://techcrunch.com/2016/05/19/zenly-solomoyolo/](https://techcrunch.com/2016/05/19/zenly-solomoyolo/)
- Telegram iOS, during a live share, runs continuous updates at ten-metre accuracy with no
  automatic pause and the blue background indicator on.
  [https://github.com/TelegramMessenger/Telegram-iOS/blob/master/submodules/DeviceLocationManager/Sources/DeviceLocationManager.swift](https://github.com/TelegramMessenger/Telegram-iOS/blob/master/submodules/DeviceLocationManager/Sources/DeviceLocationManager.swift)
- OwnTracks iOS, the closest open design to Mismeet:
  - Significant mode publishes after about 500 m, at most every 5 minutes; when you do not move,
    nothing new is published apart from an occasional ping.
    [https://owntracks.org/booklet/features/location/](https://owntracks.org/booklet/features/location/)
  - The ping is a message with trigger `p`, "issued randomly by background task"; the app
    registers a `BGAppRefreshTask` that wakes its location manager.
    [https://owntracks.org/booklet/tech/json/](https://owntracks.org/booklet/tech/json/),
    [https://github.com/owntracks/ios/blob/master/OwnTracks/OwnTracks/OwnTracksAppDelegate.m](https://github.com/owntracks/ios/blob/master/OwnTracks/OwnTracks/OwnTracksAppDelegate.m)
  - Move mode publishes every 100 m or 300 s by default, with battery use "as high as in
    navigation"; "downgrade" drops to Significant below a battery level, and "adapt" after some
    time without movement.
    [https://owntracks.org/booklet/features/location/](https://owntracks.org/booklet/features/location/),
    [https://owntracks.org/booklet/features/ios/](https://owntracks.org/booklet/features/ios/)
  - A "+follow" region is re-centred on each publish, with a radius of the distance covered in
    30 seconds and at least 50 m, so leaving it wakes the app. A home region publishes on
    leaving home even without 500 m of movement.
    [https://owntracks.org/booklet/features/ios/](https://owntracks.org/booklet/features/ios/)
- Grid uses its own `libre_location` plugin. When still, it stops GPS, keeps significant changes
  and drops a geofence at the current position. Its README gives heartbeats while stationary of
  30, 20 or 5 minutes and battery use of about 1 percent a day (low) and 2 to 4 percent
  (balanced); these are the project's own claims, and the README does not say how the heartbeat
  runs while iOS has suspended the app.
  [https://github.com/Rezivure/libre-location](https://github.com/Rezivure/libre-location),
  [https://github.com/Rezivure/libre-location/blob/main/ios/Classes/LocationService.swift](https://github.com/Rezivure/libre-location/blob/main/ios/Classes/LocationService.swift)

### iOS mechanisms

| Mechanism | Wakes a terminated app | Needs a server | Fires when | Cost and visibility |
| --- | --- | --- | --- | --- |
| Significant change | Yes | No | Moved about 500 m, at most every 5 min | Runs around the clock; Apple suggests visits and regions first |
| Visit monitoring | Yes | No | Arrival at or departure from a place | Low; reduced accuracy |
| Region (geofence) | System tries to | No | Entering or leaving a circle; at most 20 per app | Low |
| `BGAppRefreshTask` | Yes | No | When the system decides; up to 30 s | No timing guarantee; needs the `fetch` mode |
| Background push | Yes | Yes | On a push; Apple: no more than 2 or 3 an hour | Low priority, may be held; dropped on force quit |
| Location push extension | Yes | Yes | On a location push | Needs an entitlement from Apple |
| Continuous updates | Keeps the app running | No | Every fix | Blue status bar pill; lower accuracy saves energy |
| `CLBackgroundActivitySession` | Keeps the app running | No | Every fix, also for When In Use | Shows an indicator |
| Widget with location | Widget only | No | Widget reloads while it counts as in use | Limited reloads a day |
| Live Activity | No | Optional | App or ActivityKit push updates it | Cannot use network or location; 8 h limit |

Sources, in row order:
[https://developer.apple.com/documentation/corelocation/cllocationmanager/startmonitoringsignificantlocationchanges()](https://developer.apple.com/documentation/corelocation/cllocationmanager/startmonitoringsignificantlocationchanges()),
[https://developer.apple.com/library/archive/documentation/Performance/Conceptual/EnergyGuide-iOS/LocationBestPractices.html](https://developer.apple.com/library/archive/documentation/Performance/Conceptual/EnergyGuide-iOS/LocationBestPractices.html),
[https://developer.apple.com/documentation/corelocation/cllocationmanager/startmonitoringvisits()](https://developer.apple.com/documentation/corelocation/cllocationmanager/startmonitoringvisits()),
[https://developer.apple.com/documentation/corelocation/monitoring-the-user-s-proximity-to-geographic-regions](https://developer.apple.com/documentation/corelocation/monitoring-the-user-s-proximity-to-geographic-regions),
[https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app](https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app),
[https://developer.apple.com/documentation/backgroundtasks/bgapprefreshtask](https://developer.apple.com/documentation/backgroundtasks/bgapprefreshtask),
[https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate](https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate),
[https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app](https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app),
[https://developer.apple.com/documentation/corelocation/cllocationpushserviceextension](https://developer.apple.com/documentation/corelocation/cllocationpushserviceextension),
[https://developer.apple.com/documentation/corelocation/handling-location-updates-in-the-background](https://developer.apple.com/documentation/corelocation/handling-location-updates-in-the-background),
[https://developer.apple.com/documentation/corelocation/cllocationmanager/showsbackgroundlocationindicator](https://developer.apple.com/documentation/corelocation/cllocationmanager/showsbackgroundlocationindicator),
[https://developer.apple.com/documentation/corelocation/clbackgroundactivitysession-3mzv3](https://developer.apple.com/documentation/corelocation/clbackgroundactivitysession-3mzv3),
[https://developer.apple.com/documentation/widgetkit/accessing-location-information-in-widgets](https://developer.apple.com/documentation/widgetkit/accessing-location-information-in-widgets),
[https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities).

Two limits apply to every row that wakes the app:

- Only Always authorization lets the system launch a terminated app, and only for significant
  change, visits and region monitoring. With When In Use the user must launch the app.
  [https://developer.apple.com/documentation/corelocation/requesting-authorization-to-use-location-services](https://developer.apple.com/documentation/corelocation/requesting-authorization-to-use-location-services)
- With Background App Refresh off, globally or for the app, the system does not relaunch it for
  any location event, significant change and regions included (Apple's archived guide).
  [https://developer.apple.com/library/archive/documentation/UserExperience/Conceptual/LocationAwarenessPG/CoreLocation/CoreLocation.html](https://developer.apple.com/library/archive/documentation/UserExperience/Conceptual/LocationAwarenessPG/CoreLocation/CoreLocation.html)

App Review guideline 2.5.4 allows background services only for their intended purposes, so
keeping location on mainly to hold a relay connection open risks rejection.
[https://developer.apple.com/app-store/review/guidelines/](https://developer.apple.com/app-store/review/guidelines/)

### What this leaves a serverless iOS app

Mismeet today uses significant change alone, with `location` as its only background mode
(`ios/Mismeet/Location/LocationService.swift`, `ios/Mismeet/Info.plist`). Options, not
decisions:

- a. Visit monitoring: publish on arrival and on departure, with the arrival time, so a contact
  reads "here since 08:12" rather than "seen 15 hours ago". Our reading: since departure also
  wakes the app, silence after an arrival suggests the phone is still there. Cost: low.
- b. A geofence around the last fix, as OwnTracks' +follow and Grid: wakes the app on leaving a
  circle of, say, 100 to 200 m, sooner than the 500 m of significant change. Cost: low; uses 1
  of the 20 regions.
- c. A `BGAppRefreshTask` heartbeat, as OwnTracks' ping: republish when iOS grants time, so the
  event does not reach its 24 h expiry while the phone sits still. Cost: low; timing is up to
  the system; needs the `fetch` background mode.
- d. Continuous low-accuracy updates with the blue pill, as Telegram during a share, offered
  only for a chosen period such as one hour. Cost: battery and a visible indicator.
- e. Battery and charging next to "seen", as Google, Life360 and whoo show, so a contact can tell
  a dead phone from a still one. Cost: none; the payload has it.
- f. A third-party push relay holding an APNs key: would allow a Find My style "Now", but adds
  the server Mismeet does not have. Listed for completeness.

## 3. Onboarding

- Find My: not found: onboarding of its own; it uses the Apple Account, and Me > Share My
  Location turns sharing on, with Sharing From and Use This iPhone as My Location.
  [https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios](https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios)
- Google: a signed-in Google Account; recipients must be in Google Contacts. Recipients always
  see your name and photo from the account.
  [https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS)
- Life360 sign-up order: phone number, SMS code, first and last name, email, birthday, invite
  code, then add people, a Tile or a Pet GPS, or Skip. The name is required; no photo step is
  listed. Not found: the order of its permission prompts.
  [https://support.life360.com/hc/en-us/articles/23053525850519-Create-a-Life360-Account](https://support.life360.com/hc/en-us/articles/23053525850519-Create-a-Life360-Account)
- Snap Map explains the choice in its help: Always keeps you on the map after the app closes,
  "Only while using" expires after 24 hours.
  [https://help.snapchat.com/hc/en-us/articles/24547077410580-Snap-Map-Privacy-Safety-Reminder](https://help.snapchat.com/hc/en-us/articles/24547077410580-Snap-Map-Privacy-Safety-Reminder)
- WhatsApp asks for Always or While Using before a live share.
  [https://faq.whatsapp.com/480865177351335/?cms_platform=iphone&locale=en_US](https://faq.whatsapp.com/480865177351335/?cms_platform=iphone&locale=en_US)
- Not found: sources for Zenly's, whoo's or Bump's sign-up steps.
- Mismeet today: asks for the user's own name at first launch, with Skip and Save.

### Always on iOS

- Apple: request authorization right before a part of the app needs it; a request at launch may
  be misread and denied.
  [https://developer.apple.com/documentation/corelocation/requesting-authorization-to-use-location-services](https://developer.apple.com/documentation/corelocation/requesting-authorization-to-use-location-services)
- Two paths: When In Use first, then Always; or Always directly, which grants a provisional
  Always and shows the second prompt later, usually while the app is not running. The Always
  request works only once.
  [https://developer.apple.com/documentation/corelocation/cllocationmanager/requestalwaysauthorization()](https://developer.apple.com/documentation/corelocation/cllocationmanager/requestalwaysauthorization())
- iOS reminds the user from time to time of background use, with the locations on a map, and
  asks whether to keep it.
  [https://support.apple.com/en-us/102515](https://support.apple.com/en-us/102515)
- Mismeet today takes the first path: When In Use, then Always, from Me > Allow location.

### Background location on Android

- Ask for foreground location first. From Android 11 the dialog has no "Allow all the time"; the
  app sends the user to a settings page and can name that option with
  `getBackgroundPermissionOptionLabel()`, which returns it in the user's language.
  [https://developer.android.com/develop/sensors-and-location/location/permissions/background](https://developer.android.com/develop/sensors-and-location/location/permissions/background)
- A foreground service of type `location` counts as foreground use. Background apps otherwise get
  location only a few times each hour. The system reminds the user after background location is
  granted. Approximate location is about 3 square km, precise about 50 m.
  [https://developer.android.com/develop/sensors-and-location/location/permissions](https://developer.android.com/develop/sensors-and-location/location/permissions),
  [https://developer.android.com/develop/sensors-and-location/location/background](https://developer.android.com/develop/sensors-and-location/location/background)
- Life360 on Android: "Allow all the time", "Use precise location", battery optimization "Not
  optimized".
  [https://support.life360.com/hc/en-us/articles/23053398402839-Life360-Permission-Settings](https://support.life360.com/hc/en-us/articles/23053398402839-Life360-Permission-Settings)

## 4. Map and list UI

### Find My

- Tabs: People, Devices, Items, Me.
  [https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios](https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios),
  [https://support.apple.com/guide/iphone/locate-an-item-ipha779f0c10/ios](https://support.apple.com/guide/iphone/locate-an-item-ipha779f0c10/ios)
- The Add button sits at the top of the People list. Favorites sit at the top with a star; swipe
  left on a row to star it. A location label such as Home shows below the name. Distance needs
  Precise Location on.
  [https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios](https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios)
- Person card: Contact, Directions, Notify Me, Edit Location Name, Add to Favorites, Hide
  Location, Stop Sharing My Location, Remove.
  [https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios](https://support.apple.com/guide/iphone/locate-a-friend-ipha24eb4a37/ios),
  [https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios](https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios),
  [https://support.apple.com/guide/iphone/notified-friends-change-location-iph843dd79b6/ios](https://support.apple.com/guide/iphone/notified-friends-change-location-iph843dd79b6/ios)
- Map: a button at the right switches between the explore and satellite map; 2D and 3D too.
  [https://support.apple.com/guide/iphone/adjust-map-settings-iphbdf1de495/ios](https://support.apple.com/guide/iphone/adjust-map-settings-iphbdf1de495/ios)
- Me: Share My Location, Sharing From, Location Label, Allow Friend Requests, Notifications
  About You.
  [https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios](https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios),
  [https://support.apple.com/guide/personal-safety/find-my-and-location-sharing-ips05ede4573/web](https://support.apple.com/guide/personal-safety/find-my-and-location-sharing-ips05ede4573/web)
- Not found: an Apple source for the recentre and compass controls of Find My's map.

### Google Find Hub and Maps

- Find Hub: filters "Sharing with you" and "You're sharing with", My Location button at the
  bottom right, a "+" button to start a share. A person's details show address, last-updated
  time and distance. Actions: Request location, Hide from map, Block, Refresh, add a home screen
  widget, Stop sharing.
  [https://support.google.com/android/answer/15992026?hl=en](https://support.google.com/android/answer/15992026?hl=en)
- Maps: your profile > Location sharing; on a person, More > Refresh, Hide from map, Stop.
  [https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS)

### Others

- Life360: a Circle Switcher, members on the map, a refresh button.
  [https://support.life360.com/hc/en-us/articles/23053441527319-Join-a-Circle](https://support.life360.com/hc/en-us/articles/23053441527319-Join-a-Circle),
  [https://support.life360.com/hc/en-us/articles/23053466420887-Movement-Accuracy](https://support.life360.com/hc/en-us/articles/23053466420887-Movement-Accuracy)
- whoo: avatars on the map, marks for approximate and frozen location, a mark for low battery,
  time at the place beside the icon, the add-friend icon at the top right.
  [https://www.sungrove.co.jp/whoo/](https://www.sungrove.co.jp/whoo/)
- OwnTracks: each friend's photo or tracker ID, a ring that shows speed and a mark for course.
  [https://owntracks.org/booklet/features/ios/](https://owntracks.org/booklet/features/ios/)
- Zenly (2016): one map of you and your friends, with their battery level.
  [https://techcrunch.com/2016/05/19/zenly-solomoyolo/](https://techcrunch.com/2016/05/19/zenly-solomoyolo/)
- Find Hub and Bump offer home screen widgets for people.
  [https://support.google.com/android/answer/15992026?hl=en](https://support.google.com/android/answer/15992026?hl=en),
  [https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217](https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217)

## 5. Privacy and safety

| App | Pause | Approximate | Stop everywhere |
| --- | --- | --- | --- |
| Find My | Hide Location, resumes the next day | Precise Location off; blue circle | Me > Share My Location off |
| Google | Viewer can Hide from map | Not found | Android Settings > Location > Location services > Google Location Sharing > Use Location Sharing |
| Life360 | Bubble: approximate area up to 25 miles for up to 6 h | Bubble | Switch per Circle |
| Snap Map | Ghost Mode: 3 h, 24 h, Until Turned Off | Not found | Ghost Mode |
| whoo | Per friend: approximate or frozen, with a mark | Yes | Not found |
| Zenly | Hide from some or all | City only | Not found |
| WhatsApp | Not found | No | You > Privacy > Live location |
| Mismeet today | Switch per contact | No | Me > Share my location off |

Sources:
[https://support.apple.com/en-us/105104](https://support.apple.com/en-us/105104),
[https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios](https://support.apple.com/guide/iphone/share-your-location-iph01954dc44/ios),
[https://support.apple.com/guide/findmy-mac/locate-a-friend-fmmb20f3d01a/mac](https://support.apple.com/guide/findmy-mac/locate-a-friend-fmmb20f3d01a/mac),
[https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS),
[https://support.google.com/maps/answer/7326816?hl=en&co=GENIE.Platform%3DAndroid](https://support.google.com/maps/answer/7326816?hl=en&co=GENIE.Platform%3DAndroid),
[https://support.life360.com/hc/en-us/articles/23053376685463-Life360-Bubbles-Feature](https://support.life360.com/hc/en-us/articles/23053376685463-Life360-Bubbles-Feature),
[https://help.snapchat.com/hc/en-us/articles/7012322854932-How-do-I-turn-on-Ghost-Mode](https://help.snapchat.com/hc/en-us/articles/7012322854932-How-do-I-turn-on-Ghost-Mode),
[https://www.sungrove.co.jp/whoo/](https://www.sungrove.co.jp/whoo/),
[https://techcrunch.com/2016/05/19/zenly-solomoyolo/](https://techcrunch.com/2016/05/19/zenly-solomoyolo/),
[https://faq.whatsapp.com/480865177351335/?cms_platform=iphone&locale=en_US](https://faq.whatsapp.com/480865177351335/?cms_platform=iphone&locale=en_US).

Other points:

- Life360 Bubbles: popping or bursting a Bubble notifies the Circle, and Circle admins can turn
  the feature off.
  [https://support.life360.com/hc/en-us/articles/23053376685463-Life360-Bubbles-Feature](https://support.life360.com/hc/en-us/articles/23053376685463-Life360-Bubbles-Feature)
- Ghost Mode on Snap hides you from friends, but Snap still gets the location.
  [https://help.snapchat.com/hc/en-us/articles/7012322854932-How-do-I-turn-on-Ghost-Mode](https://help.snapchat.com/hc/en-us/articles/7012322854932-How-do-I-turn-on-Ghost-Mode)
- Google: a request reveals the requester's email address; a recipient can Block.
  [https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/15437054?hl=en&co=GENIE.Platform%3DiOS)
- End-to-end encrypted: WhatsApp live location, Grid (Matrix), Locus (Nostr).
  [https://faq.whatsapp.com/480865177351335/?cms_platform=iphone&locale=en_US](https://faq.whatsapp.com/480865177351335/?cms_platform=iphone&locale=en_US),
  [https://apps.apple.com/us/app/grid-private-location-sharing/id6736839927](https://apps.apple.com/us/app/grid-private-location-sharing/id6736839927),
  [https://github.com/Myzel394/locus](https://github.com/Myzel394/locus)
- Mismeet's own spec notes that on iOS the publish times leak movement (spec/SPEC.md section 9).

## 6. Localization

Finnish in the App Store listing (from Apple's lookup data for the Finnish store, 2026-11-09):
yes for Google Maps, Life360, WhatsApp, Snapchat and Bump; no for Telegram, Glympse, OwnTracks
and Grid (English only). Find My is a system app with a Finnish user guide. whoo: listing not
found.
[https://apps.apple.com/fi/app/google-maps/id585027354](https://apps.apple.com/fi/app/google-maps/id585027354),
[https://apps.apple.com/fi/app/life360-family-safety-gps/id384830320](https://apps.apple.com/fi/app/life360-family-safety-gps/id384830320),
[https://apps.apple.com/fi/app/whatsapp-messenger/id310633997](https://apps.apple.com/fi/app/whatsapp-messenger/id310633997),
[https://apps.apple.com/fi/app/snapchat-chat-with-friends/id447188370](https://apps.apple.com/fi/app/snapchat-chat-with-friends/id447188370),
[https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217](https://apps.apple.com/us/app/bump-real-life-is-happening/id6471519217),
[https://apps.apple.com/fi/app/telegram-messenger/id686449807](https://apps.apple.com/fi/app/telegram-messenger/id686449807),
[https://apps.apple.com/fi/app/glympse-share-your-location/id330316698](https://apps.apple.com/fi/app/glympse-share-your-location/id330316698),
[https://apps.apple.com/fi/app/owntracks/id692424691](https://apps.apple.com/fi/app/owntracks/id692424691),
[https://apps.apple.com/fi/app/grid-private-location-sharing/id6736839927](https://apps.apple.com/fi/app/grid-private-location-sharing/id6736839927).

### Find My in Finnish (Etsi)

From Apple's Finnish iPhone User Guide. The tab the guide calls Käyttäjät is called Henkilöt
in Apple's Finnish support article on sharing, so the two Apple sources disagree; neither was
checked on a phone.

| English | Finnish | Mismeet fi today |
| --- | --- | --- |
| Find My | Etsi | |
| People | Käyttäjät (guide), Henkilöt (article) | Yhteystiedot (list title) |
| Devices / Items | Laitteet / Esineet | |
| Me | Minä | Minä |
| Share My Location | Jaa oma sijainti | Jaa sijaintini |
| Sharing From | Jaetaan laitteesta | |
| Use This iPhone as My Location | Käytä tätä iPhonea sijaintinani | |
| Location Label | Sijainnin kuvaus | |
| Stop Sharing My Location | Lopeta oman sijainnin jako | |
| Hide Location | Kätke sijainti | |
| Ask To Follow Location | Pyydä seurata sijaintia | |
| Share (answer to a request) | Jaa | Jaa |
| Cancel | Kumoa | |
| Done | Valmis | |
| Add button | Lisää-painike | Lisää |
| No location found | Sijaintia ei löytynyt | Ei sijaintia vielä (No location yet) |
| Can see your location | Näkee sijaintisi | |
| Contact / Directions | Ota yhteyttä / Reittiohjeet | Reittiohjeet |
| Add to Favorites | Lisää suosikkeihin | |
| Allow Friend Requests | Salli ystäväpyynnöt | |
| Precise Location | Tarkka sijainti | |
| Custom (duration) | Mukautettu | |
| an hour, until the end of the day, indefinitely | tunnin ajan, päivän loppuun saakka, toistaiseksi | |
| Map types Explore / Satellite | Tutustu / Satelliitti | |

[https://support.apple.com/fi-fi/guide/iphone/iph01954dc44/ios](https://support.apple.com/fi-fi/guide/iphone/iph01954dc44/ios),
[https://support.apple.com/fi-fi/guide/iphone/ipha24eb4a37/ios](https://support.apple.com/fi-fi/guide/iphone/ipha24eb4a37/ios),
[https://support.apple.com/fi-fi/guide/iphone/ipha5f04100d/ios](https://support.apple.com/fi-fi/guide/iphone/ipha5f04100d/ios),
[https://support.apple.com/fi-fi/guide/iphone/iph09b087eda/ios](https://support.apple.com/fi-fi/guide/iphone/iph09b087eda/ios),
[https://support.apple.com/fi-fi/guide/iphone/ipha779f0c10/ios](https://support.apple.com/fi-fi/guide/iphone/ipha779f0c10/ios),
[https://support.apple.com/fi-fi/guide/iphone/iphbdf1de495/ios](https://support.apple.com/fi-fi/guide/iphone/iphbdf1de495/ios),
[https://support.apple.com/fi-fi/105104](https://support.apple.com/fi-fi/105104).
The duration words are the article's prose, not confirmed button labels.

### Google Maps in Finnish

Sijainnin jakaminen (Location sharing), Uusi jako (New share), Jaa (Share), "Kunnes otat
jakamisen pois päältä" (Until you turn this off), Lopeta and Lopeta jakaminen (Stop), Pyydä
(Request), Päivitä (Refresh), Piilota kartalta (Hide from map), Ei and Estä (No and Block),
Estetyt käyttäjät (Blocked users).
[https://support.google.com/maps/answer/15437054?hl=fi&co=GENIE.Platform%3DiOS](https://support.google.com/maps/answer/15437054?hl=fi&co=GENIE.Platform%3DiOS)

Mismeet's own Finnish uses "Ohita" for both Skip and Dismiss
(`ios/Mismeet/Localizable.xcstrings`).

## Shared conventions

"Mismeet today" is the iOS UI by intent. Sources are in the sections above.

| Convention | Apps that follow it | Mismeet today |
| --- | --- | --- |
| People list over the map | Find My; Find Hub | Yes, a sheet under the tab bar |
| Add button at the top of the people list | Find My (top of list), whoo (top right of map); Find Hub uses a "+" button | Yes, top right |
| Tab bar with a people tab and Me | Find My (People, Devices, Items, Me) | Partly: Map and Me, people in the sheet |
| Row: picture, name, place label, time, distance | Find My, Find Hub | Yes, plus a share switch in the row, which no other app shows |
| Time words "Live" and "Now" | Find My; Find Hub shows "now" after a refresh | No: "seen 2 minutes ago" |
| A state for no known location | Find My "No location found" | "No location yet" |
| Battery on the person | Google, Life360, Zenly, whoo, Bump; not Find My: apps disagree | No, though the payload has it |
| Time at the current place | whoo, Bump | No |
| Card with Directions | Find My, Find Hub | Yes |
| Card with Contact, Notify Me, Favorites | Find My | No |
| Refresh one person | Google, Find Hub, Life360 | Pull to refresh the whole list |
| Ask to see someone | Find My (Ask To Follow Location), Google (Request) | Add-back request, sent on adding |
| Duration when sharing: 1 h, end of day, indefinitely | Find My, Find Hub; chat apps 15 min, 1 h, 8 h; Snap, whoo, Life360 ongoing: apps disagree | No: until turned off |
| Words Share and Stop Sharing | Find My, Google, Find Hub, WhatsApp, Life360 | Share; stopping is a switch |
| Stop for everyone from one switch | Find My (Me > Share My Location), WhatsApp, Snap (Ghost Mode) | Yes, on Me |
| Pause that ends by itself | Find My (next day), Snap (3 h, 24 h), Life360 Bubble (6 h): apps disagree on length | No |
| Approximate location drawn as a circle | Find My (Mac) | Accuracy circle on the focused contact |
| My location button at the bottom right | Find Hub; not found for Find My | Yes, with heading on a second tap |
| Map type button | Find My | No |
| Request shows the requester's account name | Find My (Apple Account), Google (email) | The name the sender typed, or "Unknown" |
| Reminder that you are sharing | Telegram bar, Snap reminders, iOS and Android system reminders | System reminders only |

## Ideas for Mismeet

Ideas only, not decisions.

- Idea (whoo, Bump): show "here since 08:12" from a visit's arrival time instead of "seen 15 hours ago".
- Idea (Apple visit monitoring, OwnTracks home region): publish on arrival and departure, so silence reads as "has not left".
- Idea (OwnTracks +follow, Grid): a 100 to 200 m geofence around the last fix to wake the app before 500 m.
- Idea (OwnTracks ping): a `BGAppRefreshTask` heartbeat that republishes before the 24 h expiry.
- Idea (Google, Life360, whoo): show battery and charging beside "seen", from the payload's `bat`.
- Idea (Find My): "Now" for a fix only a minute or two old, and "No location found" wording when none.
- Idea (Find My, Find Hub): move the share switch from the row into the card, as Stop Sharing My Location.
- Idea (Find My, Find Hub): offer one hour, until end of day and indefinitely in "Share your location with X?".
- Idea (Find My Hide Location, Snap Ghost Mode): a pause that ends by itself, such as until tomorrow.
- Idea (Telegram, Find My Live): a "share live for 1 hour" mode with continuous updates and the blue pill.
- Idea (Find My Ask To Follow Location, Google Request): an "Ask again" action on a contact who does not share back.
- Idea (Find My, Etsi): Käyttäjät or Henkilöt instead of Yhteystiedot, once checked on a phone set to Finnish.
- Idea (Find Hub, Bump): a home screen widget per contact.

## Gaps

- No public source on how Find My or Life360 keep a still iPhone fresh.
- WhatsApp's current durations are not on its help page; the figures come from 2017 press.
- No source for Snap's Live Location durations, Zenly's sign-up, or Find My's recentre and compass controls.
- whoo's App Store listing was not found; its facts rest on a press release and one review.
