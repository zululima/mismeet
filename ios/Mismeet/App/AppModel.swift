import CoreLocation
import Foundation
import ImageIO
import NostrSDK
import Observation
import OSLog
import UIKit

@MainActor
@Observable
final class AppModel {
    static let shared = AppModel()

    enum PublishTrigger {
        case wakeup, foreground, manual, contactsChanged
    }

    private(set) var state: PersistedState
    /// Why an action the user took did not happen, shown in an alert until dismissed.
    var notice: String?
    /// An invite link the app was opened with, until the Add contact sheet takes it.
    var openedInvite: String?
    /// A contact just added, whom the app asks whether to share this phone's location with.
    private(set) var shareQuestion: Contact?
    private(set) var authorization: CLAuthorizationStatus
    /// False when the user gave Mismeet only an approximate location.
    private(set) var preciseLocation: Bool
    private(set) var isRefreshing = false
    private(set) var isPublishing = false
    @ObservationIgnored private var isSendingRequests = false
    @ObservationIgnored private var isReporting = false
    let keys: Keys
    let npub: String
    let roads = RoadData()
    /// The district, town or village of each contact's last position by contact id, spec section 8.7.
    private(set) var placeNames: [String: String] = [:]
    private let places = PlaceData()

    private let store = StateStore()
    private let location = LocationService()
    private let relays = RelayService()
    private let logger = Logger(subsystem: "app.mismeet.ios", category: "model")
    private var pendingTrigger: PublishTrigger?
    @ObservationIgnored private var photos: [String: (setAt: Date, image: UIImage)] = [:]

    private init() {
        state = store.load()
        var startupProblem: String?
        do {
            keys = try KeyStore.loadOrCreate()
        } catch {
            keys = Keys.generate()
            startupProblem = "Keychain failed, using a temporary key: \(error)"
        }
        npub = (try? keys.publicKey().toBech32()) ?? keys.publicKey().toHex()
        authorization = location.authorizationStatus
        preciseLocation = location.preciseLocation
        UIDevice.current.isBatteryMonitoringEnabled = true
        if let startupProblem { log(startupProblem) }
        location.onFix = { [weak self] fix in self?.handle(fix: fix) }
        location.onFailure = { [weak self] error in
            guard let self, self.pendingTrigger != nil else { return }
            self.pendingTrigger = nil
            self.log("No fix: \(error)")
            self.shareFailed("Could not get a position")
        }
        location.onAuthorizationChange = { [weak self] status in
            self?.authorization = status
            self?.preciseLocation = self?.location.preciseLocation ?? true
            if self?.state.sharingEnabled == true { self?.location.startMonitoringIfAuthorized() }
        }
        if state.sharingEnabled {
            location.startMonitoringIfAuthorized()
        } else {
            location.stopMonitoring()
        }
        NotificationCenter.default.addObserver(
            forName: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self, self.store.savesBlocked else { return }
                self.state = self.store.load()
            }
        }
        if state.sharingEnabled, state.relayListPublishedAt == nil {
            Task { await publishRelayList() }
        }
        #if DEBUG
        // Test hook: `SIMCTL_CHILD_MISMEET_TEST_INVITES="uri;name|uri" xcrun simctl launch ...` adds contacts
        // and shares with them without asking.
        if let invites = ProcessInfo.processInfo.environment["MISMEET_TEST_INVITES"] {
            for (index, entry) in invites.split(separator: ";").enumerated() {
                let parts = entry.split(separator: "|", maxSplits: 1)
                let name = parts.count == 2 ? String(parts[0]) : "Test \(index + 1)"
                try? addContact(invite: String(parts[parts.count - 1]), name: name)
                if let id = shareQuestion?.id, let index = state.contacts.firstIndex(where: { $0.id == id }) {
                    shareQuestion = nil
                    state.contacts[index].share = true
                    save()
                    if state.sharingEnabled { recipientsChanged() }
                }
            }
        }
        #endif
        log("Invite: \(inviteLink)")
        CrashReceiver.shared.start()
        crashArrived()
    }

    var inviteLink: String {
        (try? ownInvite.link()) ?? ProtocolConstants.inviteLinkPrefix + npub
    }

    private var ownInvite: Invite {
        Invite(publicKey: keys.publicKey(), relays: state.relays.compactMap { try? RelayUrl.parse(url: $0) })
    }

    func setOwnName(_ name: String) {
        state.ownName = String(name.prefix(ProtocolConstants.maxNameLength))
        save()
    }

    /// Asked once, at the first launch, or at the first launch after this question existed for a
    /// user who never gave a name; an empty name is a valid answer.
    var asksName: Bool {
        #if DEBUG
        if ProcessInfo.processInfo.environment.keys.contains(where: { $0.hasPrefix("MISMEET_TEST_") }) { return false }
        #endif
        return !state.askedName && state.ownName.isEmpty
    }

    func answerName(_ name: String?) {
        if let name { setOwnName(name.trimmingCharacters(in: .whitespacesAndNewlines)) }
        state.askedName = true
        save()
    }

    var authorizationText: String {
        switch authorization {
        case .authorizedAlways: String(localized: "Always")
        case .authorizedWhenInUse: String(localized: "While using the app")
        case .denied: String(localized: "Denied")
        case .restricted: String(localized: "Restricted")
        case .notDetermined: String(localized: "Not asked yet")
        @unknown default: String(localized: "Unknown")
        }
    }

    // MARK: Lifecycle

    func becameActive() {
        Task { await refresh() }
        if state.sharingEnabled, authorization == .authorizedAlways || authorization == .authorizedWhenInUse {
            pendingTrigger = .foreground
            location.requestFreshFix()
        }
    }

    func requestLocationAuthorization() {
        location.requestAuthorization()
    }

    /// Publishing is opt-in: off, the phone only watches its contacts, publishes nothing and needs
    /// no permission. On, it advertises its relays, asks for location and monitors it.
    func setSharingEnabled(_ enabled: Bool) {
        guard state.sharingEnabled != enabled else { return }
        state.sharingEnabled = enabled
        save()
        if enabled {
            Task {
                await publishRelayList()
                await sendAddBackRequests()
            }
            if authorization == .notDetermined { location.requestAuthorization() }
            location.startMonitoringIfAuthorized()
            if authorization == .authorizedAlways || authorization == .authorizedWhenInUse {
                pendingTrigger = .manual
                location.requestFreshFix()
            }
        } else {
            location.stopMonitoring()
            recipientsChanged()
        }
    }

    // MARK: Contacts

    func addContact(invite text: String, name: String) throws {
        let invite = try Invite.parse(text)
        let id = invite.publicKey.toHex()
        guard id != keys.publicKey().toHex() else { throw ContactError.ownKey }
        guard !state.contacts.contains(where: { $0.id == id }) else { throw ContactError.duplicate }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw ContactError.noName }
        var contact = Contact(id: id, name: trimmed, relays: invite.relays.map(\.description), addedAt: Date())
        contact.share = false
        // Whoever sent a request has added this phone already.
        contact.addBackDone = state.requests.contains { $0.id == id }
        state.contacts.append(contact)
        state.requests.removeAll { $0.id == id }
        save()
        shareQuestion = contact
        Task { await refresh() }
    }

    /// The answer to `shareQuestion`: sharing with the new contact turns on sharing from this
    /// phone too, with its permission prompt, when it was off.
    func answerShareQuestion(share: Bool) {
        guard let id = shareQuestion?.id else { return }
        shareQuestion = nil
        guard share else { return }
        setSharingEnabled(true)
        setShare(id, true)
    }

    /// Hides an add-back request for good; adding the sender from their invite still works.
    func dismissRequest(_ id: String) {
        state.requests.removeAll { $0.id == id }
        if !state.dismissedRequests.contains(id) { state.dismissedRequests.append(id) }
        save()
    }

    func removeContacts(at offsets: IndexSet) {
        for contact in offsets.map({ state.contacts[$0] }) {
            try? FileManager.default.removeItem(at: photoURL(contact.id))
        }
        state.contacts.remove(atOffsets: offsets)
        save()
        recipientsChanged()
    }

    /// A picked picture, upright and decoded just large enough to crop on screen, or nil when it
    /// cannot be read.
    func loadPhoto(data: Data) -> UIImage? {
        let options = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: Self.cropSourceSize,
        ] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, options) else {
            tell(String(localized: "Could not use that picture"))
            return nil
        }
        return UIImage(cgImage: thumbnail)
    }

    /// A square picture for a contact, kept on this phone only; it is never published.
    func setPhoto(_ id: String, square: UIImage) {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let size = CGSize(width: Self.photoSize, height: Self.photoSize)
        let scaled = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            square.draw(in: CGRect(origin: .zero, size: size))
        }
        guard let jpeg = scaled.jpegData(compressionQuality: 0.85),
              let index = state.contacts.firstIndex(where: { $0.id == id }) else { return }
        do {
            try FileManager.default.createDirectory(at: photoURL(id).deletingLastPathComponent(), withIntermediateDirectories: true)
            try jpeg.write(to: photoURL(id), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            return tell(String(localized: "Could not save that picture"), detail: "\(error)")
        }
        state.contacts[index].photoSetAt = Date()
        save()
    }

    func removePhoto(_ id: String) {
        try? FileManager.default.removeItem(at: photoURL(id))
        guard let index = state.contacts.firstIndex(where: { $0.id == id }) else { return }
        state.contacts[index].photoSetAt = nil
        save()
    }

    func photo(for contact: Contact) -> UIImage? {
        guard let setAt = contact.photoSetAt else { return nil }
        if let cached = photos[contact.id], cached.setAt == setAt { return cached.image }
        guard let image = UIImage(contentsOfFile: photoURL(contact.id).path) else { return nil }
        photos[contact.id] = (setAt, image)
        return image
    }

    private static let photoSize = 256
    private static let cropSourceSize = 2048

    private func photoURL(_ id: String) -> URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Mismeet/photos/\(id).jpg")
    }

    func setShare(_ id: String, _ share: Bool) {
        guard let index = state.contacts.firstIndex(where: { $0.id == id }), state.contacts[index].share != share else { return }
        if share, state.contacts.filter(\.share).count >= ProtocolConstants.maxSharingContacts {
            tell(String(localized: "Cannot share with more than \(ProtocolConstants.maxSharingContacts) contacts"))
            return
        }
        state.contacts[index].share = share
        save()
        recipientsChanged()
        if share { Task { await sendAddBackRequests() } }
    }

    enum ContactError: LocalizedError {
        case ownKey, duplicate, noName

        var errorDescription: String? {
            switch self {
            case .ownKey: String(localized: "That is your own invite.")
            case .duplicate: String(localized: "That contact is already in the list.")
            case .noName: String(localized: "Give the contact a name.")
            }
        }
    }

    // MARK: Publishing

    func publishNow() {
        guard state.sharingEnabled else { return }
        guard authorization == .authorizedAlways || authorization == .authorizedWhenInUse else {
            tell(String(localized: "Location permission is needed to share your location"))
            return
        }
        pendingTrigger = .manual
        location.requestFreshFix()
    }

    private func handle(fix: LocationFix) {
        guard state.sharingEnabled else { return }
        // Core Location answers a request with its cached position first, which after hours in
        // one place is that old; the fresh one follows.
        guard Date().timeIntervalSince(fix.time) <= ProtocolConstants.maxFixAgeSeconds else {
            log("Waiting for a fresh position: Core Location delivered one older than an hour")
            return
        }
        let trigger = pendingTrigger ?? .wakeup
        pendingTrigger = nil
        state.lastFix = fix
        let task = BackgroundTask(name: "publish")
        Task {
            await publish(fix: fix, trigger: trigger)
            task.end()
        }
    }

    private func publish(fix: LocationFix, trigger: PublishTrigger) async {
        if trigger == .wakeup || trigger == .foreground,
           let last = state.lastPublishedAt,
           Date().timeIntervalSince(last) < ProtocolConstants.publishThrottleSeconds {
            log("Skipped publish: published less than a minute ago")
            return
        }
        let recipients = sharingRecipients()
        guard !recipients.isEmpty else {
            log("Skipped publish: nobody to share with")
            return
        }
        do {
            let event = try LocationEventBuilder.build(
                payload: fix.payload(battery: batteryLevel()),
                recipients: recipients,
                keys: keys,
                now: UInt64(Date().timeIntervalSince1970),
                previousCreatedAt: state.lastPublishedCreatedAt
            )
            await send(event, describing: "Location")
        } catch {
            log("Publish failed: \(error)")
            shareFailed("Could not build the event")
        }
    }

    /// Spec section 8.3: after a contact or share change, replace the event on the relays at once.
    private func recipientsChanged() {
        guard state.lastPublishedCreatedAt != nil else {
            if authorization == .authorizedAlways || authorization == .authorizedWhenInUse, !sharingRecipients().isEmpty {
                pendingTrigger = .contactsChanged
                location.requestFreshFix()
            }
            return
        }
        Task {
            let recipients = sharingRecipients()
            let now = UInt64(Date().timeIntervalSince1970)
            do {
                if let fix = state.lastFix,
                   Date().timeIntervalSince(fix.time) <= ProtocolConstants.maxFixAgeSeconds,
                   !recipients.isEmpty {
                    let event = try LocationEventBuilder.build(
                        payload: fix.payload(battery: batteryLevel()),
                        recipients: recipients,
                        keys: keys,
                        now: now,
                        previousCreatedAt: state.lastPublishedCreatedAt
                    )
                    await send(event, describing: "Location")
                } else {
                    let event = try LocationEventBuilder.buildRevocation(keys: keys, now: now, previousCreatedAt: state.lastPublishedCreatedAt)
                    await send(event, describing: recipients.isEmpty ? "Revocation" : "Placeholder")
                    if !recipients.isEmpty, authorization == .authorizedAlways || authorization == .authorizedWhenInUse {
                        pendingTrigger = .contactsChanged
                        location.requestFreshFix()
                    }
                }
            } catch {
                log("Republish failed: \(error)")
            }
        }
    }

    private func publishRelayList() async {
        do {
            let event = try RelayList.event(relays: state.relays, keys: keys, now: UInt64(Date().timeIntervalSince1970))
            let result = await relays.publish(event: event, relayUrls: state.relays)
            if result.succeeded.isEmpty {
                log("Relay list: no relay accepted it, \(result.summary)")
            } else {
                state.relayListPublishedAt = Date()
                save()
                log("Relay list: accepted by \(result.succeeded.count) of \(state.relays.count) relays\(result.failed.isEmpty ? "" : ", refused by \(result.summary)")")
            }
        } catch {
            log("Relay list failed: \(error)")
        }
    }

    private func send(_ event: Event, describing what: String) async {
        isPublishing = true
        defer { isPublishing = false }
        let result = await relays.publish(event: event, relayUrls: state.relays)
        if result.succeeded.isEmpty {
            log("\(what): no relay accepted it, \(result.summary)")
            shareFailed("No relay accepted it: \(result.summary)")
        } else {
            state.lastPublishedCreatedAt = event.createdAt().asSecs()
            state.lastPublishedAt = Date()
            save()
            log("\(what): accepted by \(result.succeeded.count) of \(state.relays.count) relays\(result.failed.isEmpty ? "" : ", refused by \(result.summary)")")
        }
    }

    private func shareFailed(_ reason: String) {
        state.lastShareFailure = ShareFailure(at: Date(), reason: reason)
        save()
        Task { await reportAutomatically(reason: "A share failed") }
    }

    // MARK: Problem reports, spec section 8.8

    func setReportsEnabled(_ enabled: Bool) {
        state.reportsEnabled = enabled
        save()
        if !enabled { ProblemReports.forgetCrash() }
    }

    /// A crash diagnostic arrived from MetricKit, or was waiting from an earlier run.
    func crashArrived() {
        guard ProblemReports.pendingCrash != nil else { return }
        if state.reportsEnabled {
            Task { await reportAutomatically(reason: "The app crashed") }
        } else {
            ProblemReports.forgetCrash()
        }
    }

    /// The report the user would send now, with a crash still waiting to be reported.
    func reportText(relayChecks: [RelayCheck]?) async -> String {
        let servicesEnabled = await Task.detached { CLLocationManager.locationServicesEnabled() }.value
        let checks = if let relayChecks { relayChecks } else { await checkRelays() }
        return ProblemReports.text(
            model: self, servicesEnabled: servicesEnabled, relayChecks: checks,
            reason: "Sent from Check setup", automatic: false, crash: ProblemReports.pendingCrash
        )
    }

    /// Sends a report the user has read; true when a relay took it.
    func sendReport(_ text: String) async -> Bool {
        await publishReport(text, automatic: false)
    }

    private func reportAutomatically(reason: String) async {
        guard state.reportsEnabled, !isReporting else { return }
        if let last = state.lastReportAt, Date().timeIntervalSince(last) < ProtocolConstants.reportIntervalSeconds { return }
        isReporting = true
        defer { isReporting = false }
        let task = BackgroundTask(name: "report")
        defer { task.end() }
        let servicesEnabled = await Task.detached { CLLocationManager.locationServicesEnabled() }.value
        let crash = ProblemReports.pendingCrash
        let text = ProblemReports.text(
            model: self, servicesEnabled: servicesEnabled, relayChecks: await checkRelays(),
            reason: crash != nil ? "The app crashed" : reason, automatic: true, crash: crash
        )
        await publishReport(text, automatic: true)
    }

    @discardableResult
    private func publishReport(_ text: String, automatic: Bool) async -> Bool {
        let wrap: Event
        do {
            wrap = try ProblemReport.wrap(text: text, receiver: try ProblemReport.reportsKey, now: UInt64(Date().timeIntervalSince1970))
        } catch {
            log("Problem report failed: \(error)")
            return false
        }
        let result = await relays.publish(event: wrap, relayUrls: ProtocolConstants.reportRelays)
        guard !result.succeeded.isEmpty else {
            log("Problem report: no relay accepted it, \(result.summary)")
            return false
        }
        if automatic {
            state.lastReportAt = Date()
            save()
        }
        ProblemReports.forgetCrash()
        log("Problem report: accepted by \(result.succeeded.count) of \(ProtocolConstants.reportRelays.count) relays")
        return true
    }

    /// Whether each of this phone's relays answers, and the location event of this phone it holds.
    func checkRelays() async -> [RelayCheck] {
        await relays.check(relayUrls: state.relays, author: keys.publicKey())
    }

    private func sharingRecipients() -> [PublicKey] {
        guard state.sharingEnabled else { return [] }
        return state.contacts.filter(\.share).compactMap(\.publicKey)
    }

    private func batteryLevel() -> Double? {
        let level = UIDevice.current.batteryLevel
        return level >= 0 ? Double(level) : nil
    }

    /// Spec section 4.5: asks each contact this phone shares with to add it back, once, unless the
    /// contact already shares with it. Runs after each refresh, which tells which do.
    private func sendAddBackRequests() async {
        guard state.sharingEnabled, !isSendingRequests else { return }
        isSendingRequests = true
        defer { isSendingRequests = false }
        let invite = ownInvite
        for contact in state.contacts where contact.share && !contact.addBackDone {
            if contact.sharing == .sharing {
                markAddBackDone(contact.id)
                continue
            }
            guard let receiver = contact.publicKey else { continue }
            let wrap: Event
            do {
                wrap = try AddBackRequest.wrap(
                    invite: invite, name: state.ownName, keys: keys, receiver: receiver,
                    now: UInt64(Date().timeIntervalSince1970)
                )
            } catch {
                log("Add-back request to \(contact.name) failed: \(error)")
                continue
            }
            let relayUrls = contact.queryRelays(defaults: state.relays).map(\.description)
            let result = await relays.publish(event: wrap, relayUrls: relayUrls)
            if result.succeeded.isEmpty {
                log("Add-back request to \(contact.name): no relay accepted it, \(result.summary)")
            } else {
                markAddBackDone(contact.id)
                log("Add-back request to \(contact.name): accepted by \(result.succeeded.count) of \(relayUrls.count) relays")
            }
        }
    }

    private func markAddBackDone(_ id: String) {
        guard let index = state.contacts.firstIndex(where: { $0.id == id }) else { return }
        state.contacts[index].addBackDone = true
        save()
    }

    // MARK: Reading

    func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        async let requests: Void = fetchRequests()
        if !state.contacts.isEmpty { await fetchLocations() }
        // A relay that never ends the request query holds it to the timeout; the spinner stops
        // once the locations are in.
        isRefreshing = false
        await requests
        await sendAddBackRequests()
    }

    /// Spec section 4.5: the add-back requests addressed to this phone, on connections of their
    /// own, so that no relay sees this phone's key beside the authors it asks about.
    private func fetchRequests() async {
        let filter = Filter()
            .kinds(kinds: [Kind(kind: ProtocolConstants.giftWrapKind)])
            .pubkey(pubkey: keys.publicKey())
            .limit(limit: ProtocolConstants.addBackFetchLimit)
        var targets: [RelayUrl: [Filter]] = [:]
        for url in state.relays.compactMap({ try? RelayUrl.parse(url: $0) }) { targets[url] = [filter] }
        guard !targets.isEmpty else { return }
        let now = UInt64(Date().timeIntervalSince1970)
        let fetched = await relays.fetch(targets: targets).compactMap { try? AddBackRequest.read($0, keys: keys, now: now) }
        let contacts = Set(state.contacts.map(\.id))
        let oldest = now - ProtocolConstants.addBackExpirationSeconds
        state.requests = AddBackRequest.newestPerSender(state.requests.compactMap(\.request) + fetched)
            .filter { request in
                let id = request.sender.toHex()
                return !contacts.contains(id) && !state.dismissedRequests.contains(id) && request.createdAt > oldest
            }
            .map(StoredRequest.init)
        save()
        log("Add-back requests: \(fetched.count) read, \(state.requests.count) waiting")
    }

    private func fetchLocations() async {
        let now = Date()
        let due = Set(state.contacts.filter { contact in
            contact.relayListCheckedAt.map { now.timeIntervalSince($0) > Double(ProtocolConstants.relayListRefreshSeconds) } ?? true
        }.map(\.id))
        let plan = RelayPlan.authorsByRelay(state.contacts.compactMap { contact in
            contact.publicKey.map { ContactRelays(publicKey: $0, relays: contact.queryRelays(defaults: state.relays)) }
        })
        var targets: [RelayUrl: [Filter]] = [:]
        for (relay, authors) in plan {
            var filters = [
                Filter()
                    .kinds(kinds: [Kind(kind: ProtocolConstants.locationKind)])
                    .authors(authors: authors)
                    .identifier(identifier: ProtocolConstants.identifier),
            ]
            let dueAuthors = authors.filter { due.contains($0.toHex()) }
            if !dueAuthors.isEmpty {
                filters.append(Filter().kinds(kinds: [Kind(kind: ProtocolConstants.relayListKind)]).authors(authors: dueAuthors))
            }
            targets[relay] = filters
        }
        let events = await relays.fetch(targets: targets)
        let nowSecs = UInt64(now.timeIntervalSince1970)
        var decoded = 0
        var unchanged = 0
        for index in state.contacts.indices {
            guard let publicKey = state.contacts[index].publicKey else { continue }
            let own = events.filter { $0.author() == publicKey }
            if due.contains(state.contacts[index].id) {
                let lists = own.filter { $0.kind().asU16() == ProtocolConstants.relayListKind }
                if let newest = lists.max(by: { $0.createdAt().asSecs() < $1.createdAt().asSecs() }) {
                    let urls = RelayList.writeRelays(of: newest)
                    if !urls.isEmpty { state.contacts[index].relays = urls }
                }
                state.contacts[index].relayListCheckedAt = now
            }
            let locations = own.filter { $0.kind().asU16() == ProtocolConstants.locationKind }
            guard let newest = LocationEventReader.newest(locations, now: nowSecs, cachedCreatedAt: state.contacts[index].lastCreatedAt) else {
                if !locations.isEmpty { unchanged += 1 }
                continue
            }
            do {
                switch try LocationEventReader.read(event: newest, contact: publicKey, keys: keys, now: nowSecs) {
                case .shared(let payload):
                    state.contacts[index].lastPayload = payload
                    state.contacts[index].lastSeenAt = now
                    state.contacts[index].sharing = .sharing
                    decoded += 1
                case .notSharing:
                    state.contacts[index].sharing = .notSharing
                }
                state.contacts[index].lastCreatedAt = newest.createdAt().asSecs()
                state.contacts[index].lastEventId = newest.id().toHex()
            } catch {
                log("Ignored an event from \(state.contacts[index].name): \(error)")
            }
        }
        save()
        log("Refreshed from \(targets.count) relays: \(decoded) new, \(unchanged) unchanged since last time, \(state.contacts.count - decoded - unchanged) without an event")
    }

    func updatePlaceNames() async {
        for contact in state.contacts {
            guard let payload = contact.lastPayload else { continue }
            let name = await places.name(at: LatLon(latitude: payload.latitude, longitude: payload.longitude), now: Date())
            if Task.isCancelled { return }
            placeNames[contact.id] = name
        }
    }

    // MARK: Helpers

    private func save() {
        store.save(state)
    }

    private func tell(_ text: String, detail: String? = nil) {
        log(detail.map { "\(text): \($0)" } ?? text)
        notice = text
    }

    private func log(_ text: String) {
        logger.info("\(text, privacy: .public)")
        ProblemReports.record(text)
        #if DEBUG
        FileHandle.standardError.write(Data((text + "\n").utf8))
        #endif
    }
}

@MainActor
private final class BackgroundTask {
    private var id = UIBackgroundTaskIdentifier.invalid

    init(name: String) {
        id = UIApplication.shared.beginBackgroundTask(withName: name) { [weak self] in
            self?.end()
        }
    }

    func end() {
        guard id != .invalid else { return }
        UIApplication.shared.endBackgroundTask(id)
        id = .invalid
    }
}
