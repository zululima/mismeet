import Foundation
import NostrSDK

/// A contact invite, spec section 4.1: an invite link, a `nostr:` URI, or a bare `nprofile` or
/// `npub`.
struct Invite: Equatable {
    var publicKey: PublicKey
    var relays: [RelayUrl]

    enum ParseError: Error, Equatable {
        case notAProfile
    }

    func link() throws -> String {
        ProtocolConstants.inviteLinkPrefix + (try nprofile())
    }

    func nprofile() throws -> String {
        try Nip19Profile(publicKey: publicKey, relays: Array(relays.prefix(ProtocolConstants.maxRelaysPerList))).toBech32()
    }

    static func parse(_ text: String) throws -> Invite {
        var trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        for prefix in [ProtocolConstants.inviteLinkPrefix, ProtocolConstants.earlierInviteLinkPrefix]
        where trimmed.hasPrefix(prefix) {
            trimmed.removeFirst(prefix.count)
        }
        if trimmed.hasPrefix("nostr:") {
            switch try Nip21.parse(uri: trimmed).asEnum() {
            case .pubkey(let publicKey):
                return Invite(publicKey: publicKey, relays: [])
            case .profile(let profile):
                return Invite(publicKey: profile.publicKey(), relays: profile.relays())
            default:
                throw ParseError.notAProfile
            }
        }
        switch try Nip19.fromBech32(bech32: trimmed).asEnum() {
        case .pubkey(let publicKey):
            return Invite(publicKey: publicKey, relays: [])
        case .profile(let profile):
            return Invite(publicKey: profile.publicKey(), relays: profile.relays())
        default:
            throw ParseError.notAProfile
        }
    }
}
