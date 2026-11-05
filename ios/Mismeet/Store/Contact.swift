import Foundation
import NostrSDK

/// A contact record, spec section 4.3. Everything here stays on the device.
struct Contact: Codable, Identifiable, Equatable {
    enum SharingStatus: String, Codable {
        case unknown, sharing, notSharing
    }

    var id: String
    var name: String
    var relays: [String]
    var share = true
    var addedAt: Date
    var sharing = SharingStatus.unknown
    var lastPayload: LocationPayload?
    var lastCreatedAt: UInt64?
    var lastEventId: String?
    var lastSeenAt: Date?
    var relayListCheckedAt: Date?
    var photoSetAt: Date?
    /// The add-back request of spec section 4.5 was sent, or is not needed.
    var addBackDone = false

    var publicKey: PublicKey? {
        try? PublicKey.parse(publicKey: id)
    }

    /// The relays to ask about this contact: its own, or the app's list until a kind 10002 is seen.
    func queryRelays(defaults: [String]) -> [RelayUrl] {
        (relays.isEmpty ? defaults : relays).compactMap { try? RelayUrl.parse(url: $0) }
    }
}

extension Contact {
    /// The synthesized decoder ignores default values and rejects a file written before a field
    /// existed, so every field but the four a contact always had is optional here. A new field
    /// has to be added here too, or it is never read back.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        relays = try container.decode([String].self, forKey: .relays)
        share = try container.decodeIfPresent(Bool.self, forKey: .share) ?? true
        addedAt = try container.decode(Date.self, forKey: .addedAt)
        sharing = try container.decodeIfPresent(SharingStatus.self, forKey: .sharing) ?? .unknown
        lastPayload = try container.decodeIfPresent(LocationPayload.self, forKey: .lastPayload)
        lastCreatedAt = try container.decodeIfPresent(UInt64.self, forKey: .lastCreatedAt)
        lastEventId = try container.decodeIfPresent(String.self, forKey: .lastEventId)
        lastSeenAt = try container.decodeIfPresent(Date.self, forKey: .lastSeenAt)
        relayListCheckedAt = try container.decodeIfPresent(Date.self, forKey: .relayListCheckedAt)
        photoSetAt = try container.decodeIfPresent(Date.self, forKey: .photoSetAt)
        addBackDone = try container.decodeIfPresent(Bool.self, forKey: .addBackDone) ?? false
    }
}
