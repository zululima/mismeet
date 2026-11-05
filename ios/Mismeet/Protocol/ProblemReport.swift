import Foundation
import NostrSDK

enum ReportRejection: Error, Equatable {
    case wrongKind
    case missingExpiration
    case expired
    case unwrapFailed
    case wrongRumorKind
    case keyMismatch
}

/// A problem report, spec section 8.8: plain text about what went wrong on a phone, sealed for
/// the reports key by keys made for that one report, so that nothing ties it to the phone's own.
struct ProblemReport: Equatable {
    var text: String
    var createdAt: UInt64

    static var reportsKey: PublicKey {
        get throws { try PublicKey.parse(publicKey: ProtocolConstants.reportsPublicKey) }
    }

    static func wrap(text: String, receiver: PublicKey, now: UInt64) throws -> Event {
        let sender = Keys.generate()
        let rumor = EventBuilder(kind: Kind(kind: ProtocolConstants.chatMessageKind), content: clip(text))
            .tags(tags: [Tag.publicKey(publicKey: receiver)])
            .customCreatedAt(createdAt: Timestamp.fromSecs(secs: now))
            .finalizeUnsigned(publicKey: sender.publicKey())
        return try nip59MakeGiftWrap(
            signer: sender,
            receiverPubkey: receiver,
            rumor: rumor,
            expiration: TimeInterval(ProtocolConstants.reportExpirationSeconds)
        )
    }

    static func read(_ wrap: Event, keys: Keys, now: UInt64) throws -> ProblemReport {
        guard wrap.kind().asU16() == ProtocolConstants.giftWrapKind else { throw ReportRejection.wrongKind }
        guard let text = wrap.tags().first(where: { $0.kind() == "expiration" })?.content(),
              let expiration = UInt64(text) else {
            throw ReportRejection.missingExpiration
        }
        guard expiration > now else { throw ReportRejection.expired }
        guard let gift = try? UnwrappedGift.fromGiftWrap(signer: keys, giftWrap: wrap) else { throw ReportRejection.unwrapFailed }
        let rumor = gift.rumor()
        guard rumor.kind().asU16() == ProtocolConstants.chatMessageKind else { throw ReportRejection.wrongRumorKind }
        guard rumor.author() == gift.sender() else { throw ReportRejection.keyMismatch }
        return ProblemReport(text: rumor.content(), createdAt: rumor.createdAt().asSecs())
    }

    /// `text` cut to the limit, counted in UTF-16 units as on Android.
    static func clip(_ text: String) -> String {
        let units = Array(text.utf16)
        guard units.count > ProtocolConstants.reportMaxLength else { return text }
        let kept = Array(units.prefix(ProtocolConstants.reportMaxLength - cut.utf16.count))
        return (String(utf16CodeUnits: kept, count: kept.count)) + cut
    }

    /// `text` without what could tell who sent it: invite links, keys, relays outside
    /// `keepRelays`, coordinate pairs and the `names` the user gave contacts.
    static func redact(_ text: String, names: [String], keepRelays: [String]) -> String {
        let kept = Set(keepRelays.map(trimmingSlashes))
        var out = replace(invite, in: text, with: "[invite]")
        out = replace(entity, in: out, with: "[key]")
        out = replace(hexKey, in: out, with: "[key]")
        out = replace(relay, in: out) { kept.contains(trimmingSlashes($0)) ? $0 : "[relay]" }
        out = replace(position, in: out, with: "[position]")
        let cleaned = names.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        for name in Array(Set(cleaned)).sorted(by: { $0.count > $1.count }) {
            let pattern = "(?<![\\p{L}\\p{N}])" + NSRegularExpression.escapedPattern(for: name) + "(?![\\p{L}\\p{N}])"
            out = replace(pattern, in: out, with: "[contact]")
        }
        return out
    }

    private static let cut = "\n[cut]"
    private static let invite = "https?://\\S*/mismeet/i#\\S*"
    private static let entity = "(?i)(nostr:)?(npub|nprofile|nsec|note|nevent|naddr|ncryptsec)1[02-9ac-hj-np-z]{6,}"
    private static let hexKey = "(?<![0-9a-fA-F])[0-9a-fA-F]{64}(?![0-9a-fA-F])"
    private static let relay = "wss?://[^\\s,;)\\]]*[^\\s,;)\\]:.]"
    private static let position = "-?\\d{1,3}\\.\\d{3,}\\s*,\\s*-?\\d{1,3}\\.\\d{3,}"

    private static func trimmingSlashes(_ url: String) -> String {
        var url = url
        while url.hasSuffix("/") { url.removeLast() }
        return url
    }

    private static func replace(_ pattern: String, in text: String, with template: String) -> String {
        replace(pattern, in: text) { _ in template }
    }

    private static func replace(_ pattern: String, in text: String, using transform: (String) -> String) -> String {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return text }
        var out = text
        let source = text as NSString
        for match in regex.matches(in: text, range: NSRange(location: 0, length: source.length)).reversed() {
            let range = Range(match.range, in: out)!
            out.replaceSubrange(range, with: transform(source.substring(with: match.range)))
        }
        return out
    }
}
