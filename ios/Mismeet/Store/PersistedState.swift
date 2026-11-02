import Foundation

struct PersistedState: Codable, Equatable {
    static let defaultRelays = ["wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net"]

    var contacts: [Contact] = []
    var relays = PersistedState.defaultRelays
    var lastPublishedCreatedAt: UInt64?
    var lastPublishedAt: Date?
    var lastFix: LocationFix?
    var relayListPublishedAt: Date?
    var sharingEnabled = false
    /// Kept so that the setup checks can show a share that failed in a background wakeup.
    var lastShareFailure: ShareFailure?
}

struct ShareFailure: Codable, Equatable {
    var at: Date
    var reason: String
}

extension PersistedState {
    /// Every field is optional here for the reason given on `Contact.init(from:)`.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        contacts = try container.decodeIfPresent([Contact].self, forKey: .contacts) ?? []
        relays = try container.decodeIfPresent([String].self, forKey: .relays) ?? PersistedState.defaultRelays
        lastPublishedCreatedAt = try container.decodeIfPresent(UInt64.self, forKey: .lastPublishedCreatedAt)
        lastPublishedAt = try container.decodeIfPresent(Date.self, forKey: .lastPublishedAt)
        lastFix = try container.decodeIfPresent(LocationFix.self, forKey: .lastFix)
        relayListPublishedAt = try container.decodeIfPresent(Date.self, forKey: .relayListPublishedAt)
        // Before the switch existed, a phone that had published a location was sharing.
        sharingEnabled = try container.decodeIfPresent(Bool.self, forKey: .sharingEnabled) ?? (lastPublishedCreatedAt != nil)
        lastShareFailure = try container.decodeIfPresent(ShareFailure.self, forKey: .lastShareFailure)
    }
}

final class StateStore {
    private let url: URL
    private(set) var savesBlocked = false

    convenience init() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Mismeet", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        self.init(url: directory.appendingPathComponent("state.json"))
    }

    init(url: URL) {
        self.url = url
    }

    /// Starts empty when the file cannot be used, without losing it: a file that does not decode,
    /// such as one a newer version wrote, moves aside to state-unreadable-<seconds>.json, and one
    /// that cannot be read, such as before the first unlock after a reboot, blocks every save
    /// until a later load reads it.
    func load() -> PersistedState {
        guard FileManager.default.fileExists(atPath: url.path) else { return PersistedState() }
        guard let data = try? Data(contentsOf: url) else {
            savesBlocked = true
            return PersistedState()
        }
        savesBlocked = false
        do {
            return try JSONDecoder().decode(PersistedState.self, from: data)
        } catch {
            let aside = url.deletingLastPathComponent()
                .appendingPathComponent("state-unreadable-\(Int(Date().timeIntervalSince1970)).json")
            savesBlocked = (try? FileManager.default.moveItem(at: url, to: aside)) == nil
            return PersistedState()
        }
    }

    func save(_ state: PersistedState) {
        guard !savesBlocked, let data = try? JSONEncoder().encode(state) else { return }
        try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}
