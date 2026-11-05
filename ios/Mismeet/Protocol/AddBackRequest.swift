import Foundation
import NostrSDK

enum AddBackRejection: Error, Equatable {
    case wrongKind
    case missingExpiration
    case expired
    case unwrapFailed
    case wrongRumorKind
    case malformedContent
    case unsupportedVersion
    case keyMismatch
    case ownKey
}

/// An add-back request, spec section 4.5: someone added this phone's user from their invite and
/// sends their own `invite`, with the `name` they gave themselves, so that they can be added back.
struct AddBackRequest: Equatable {
    var invite: Invite
    var name: String?
    var createdAt: UInt64

    var sender: PublicKey { invite.publicKey }

    static func content(invite: Invite, name: String?) throws -> String {
        var content = "{\"v\":\(ProtocolConstants.addBackVersion),\"invite\":\"\(try invite.nprofile())\""
        if let name = cleanName(name) {
            let encoder = JSONEncoder()
            encoder.outputFormatting = .withoutEscapingSlashes
            content += ",\"name\":" + String(decoding: try encoder.encode(name), as: UTF8.self)
        }
        return content + "}"
    }

    /// The gift wrap carrying `invite` and `name` from `keys` to `receiver`.
    static func wrap(invite: Invite, name: String?, keys: Keys, receiver: PublicKey, now: UInt64) throws -> Event {
        let rumor = EventBuilder(kind: Kind(kind: ProtocolConstants.addBackKind), content: try content(invite: invite, name: name))
            .customCreatedAt(createdAt: Timestamp.fromSecs(secs: now))
            .finalizeUnsigned(publicKey: keys.publicKey())
        return try nip59MakeGiftWrap(
            signer: keys,
            receiverPubkey: receiver,
            rumor: rumor,
            expiration: TimeInterval(ProtocolConstants.addBackExpirationSeconds)
        )
    }

    static func read(_ wrap: Event, keys: Keys, now: UInt64) throws -> AddBackRequest {
        guard wrap.kind().asU16() == ProtocolConstants.giftWrapKind else { throw AddBackRejection.wrongKind }
        guard let text = wrap.tags().first(where: { $0.kind() == "expiration" })?.content(),
              let expiration = UInt64(text) else {
            throw AddBackRejection.missingExpiration
        }
        guard expiration > now else { throw AddBackRejection.expired }
        guard let gift = try? UnwrappedGift.fromGiftWrap(signer: keys, giftWrap: wrap) else { throw AddBackRejection.unwrapFailed }
        let rumor = gift.rumor()
        guard rumor.kind().asU16() == ProtocolConstants.addBackKind else { throw AddBackRejection.wrongRumorKind }
        let sender = gift.sender()
        guard rumor.author() == sender else { throw AddBackRejection.keyMismatch }
        guard let data = rumor.content().data(using: .utf8),
              let fields = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            throw AddBackRejection.malformedContent
        }
        guard let version = fields["v"] as? Int, version == ProtocolConstants.addBackVersion else {
            throw AddBackRejection.unsupportedVersion
        }
        guard let inviteText = fields["invite"] as? String, let invite = try? Invite.parse(inviteText) else {
            throw AddBackRejection.malformedContent
        }
        guard invite.publicKey == sender else { throw AddBackRejection.keyMismatch }
        guard sender != keys.publicKey() else { throw AddBackRejection.ownKey }
        return AddBackRequest(invite: invite, name: cleanName(fields["name"] as? String), createdAt: rumor.createdAt().asSecs())
    }

    /// The newest request of each sender among `requests`, newest first.
    static func newestPerSender(_ requests: [AddBackRequest]) -> [AddBackRequest] {
        Dictionary(grouping: requests, by: { $0.sender.toHex() })
            .compactMap { $0.value.max { $0.createdAt < $1.createdAt } }
            .sorted { $0.createdAt > $1.createdAt }
    }

    static func cleanName(_ name: String?) -> String? {
        guard let name else { return nil }
        let cut = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(ProtocolConstants.maxNameLength))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return cut.isEmpty ? nil : cut
    }
}
