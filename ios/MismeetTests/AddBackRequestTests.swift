import XCTest
import NostrSDK
@testable import Mismeet

final class AddBackRequestTests: XCTestCase {
    private let sender = Keys.generate()
    private let receiver = Keys.generate()
    // The library takes the wrap's time from the clock, so the tests read at the real time.
    private let now = UInt64(Date().timeIntervalSince1970)
    private lazy var invite = Invite(publicKey: sender.publicKey(), relays: [try! RelayUrl.parse(url: "wss://relay.example.com")])

    private func rejection(_ block: () throws -> Void) -> AddBackRejection? {
        do {
            try block()
            return nil
        } catch {
            return error as? AddBackRejection
        }
    }

    private func wrapped(kind: UInt16, content: String) throws -> Event {
        let rumor = EventBuilder(kind: Kind(kind: kind), content: content)
            .customCreatedAt(createdAt: Timestamp.fromSecs(secs: now))
            .finalizeUnsigned(publicKey: sender.publicKey())
        return try nip59MakeGiftWrap(
            signer: sender, receiverPubkey: receiver.publicKey(), rumor: rumor,
            expiration: TimeInterval(ProtocolConstants.addBackExpirationSeconds)
        )
    }

    func testRoundTripsTheInviteAndName() throws {
        let wrap = try AddBackRequest.wrap(invite: invite, name: " Akira ", keys: sender, receiver: receiver.publicKey(), now: now)
        XCTAssertEqual(wrap.kind().asU16(), ProtocolConstants.giftWrapKind)
        XCTAssertNotEqual(wrap.author(), sender.publicKey(), "the wrap is signed by a throwaway key")
        XCTAssertEqual(wrap.tags().map { $0.kind() }.filter { $0 != "expiration" }, ["p"])
        let request = try AddBackRequest.read(wrap, keys: receiver, now: now)
        XCTAssertEqual(request.sender, sender.publicKey())
        XCTAssertEqual(request.invite.relays.map(\.description), ["wss://relay.example.com"])
        XCTAssertEqual(request.name, "Akira")
        XCTAssertEqual(request.createdAt, now)
    }

    func testContentIsCompactWithKeysInOrderAndNoBlankName() throws {
        let nprofile = try invite.nprofile()
        XCTAssertEqual(try AddBackRequest.content(invite: invite, name: "Bea"), "{\"v\":1,\"invite\":\"\(nprofile)\",\"name\":\"Bea\"}")
        XCTAssertEqual(try AddBackRequest.content(invite: invite, name: "  "), "{\"v\":1,\"invite\":\"\(nprofile)\"}")
        XCTAssertEqual(AddBackRequest.cleanName(String(repeating: "x", count: ProtocolConstants.maxNameLength + 10))?.count, ProtocolConstants.maxNameLength)
        XCTAssertNil(AddBackRequest.cleanName(nil))
    }

    func testWrapExpiresAfterThirtyDaysFromItsRandomisedTime() throws {
        let wrap = try AddBackRequest.wrap(invite: invite, name: nil, keys: sender, receiver: receiver.publicKey(), now: now)
        let expiration = try XCTUnwrap(UInt64(try XCTUnwrap(wrap.tags().first { $0.kind() == "expiration" }?.content())))
        XCTAssertEqual(expiration, wrap.createdAt().asSecs() + ProtocolConstants.addBackExpirationSeconds)
        XCTAssertGreaterThan(expiration, now)
        XCTAssertEqual(rejection { _ = try AddBackRequest.read(wrap, keys: self.receiver, now: expiration) }, .expired)
    }

    func testOnlyTheReceiverCanReadIt() throws {
        let wrap = try AddBackRequest.wrap(invite: invite, name: "Akira", keys: sender, receiver: receiver.publicKey(), now: now)
        XCTAssertEqual(rejection { _ = try AddBackRequest.read(wrap, keys: Keys.generate(), now: self.now) }, .unwrapFailed)
    }

    func testRejectsAnInviteForAnotherKey() throws {
        let other = Invite(publicKey: Keys.generate().publicKey(), relays: [])
        let wrap = try AddBackRequest.wrap(invite: other, name: "Akira", keys: sender, receiver: receiver.publicKey(), now: now)
        XCTAssertEqual(rejection { _ = try AddBackRequest.read(wrap, keys: self.receiver, now: self.now) }, .keyMismatch)
    }

    func testRejectsItsOwnRequest() throws {
        let wrap = try AddBackRequest.wrap(invite: invite, name: "Akira", keys: sender, receiver: sender.publicKey(), now: now)
        XCTAssertEqual(rejection { _ = try AddBackRequest.read(wrap, keys: self.sender, now: self.now) }, .ownKey)
    }

    func testRejectsOtherRumorsAndVersions() throws {
        let nprofile = try invite.nprofile()
        let cases: [(UInt16, String, AddBackRejection)] = [
            (14, "hello", .wrongRumorKind),
            (ProtocolConstants.addBackKind, "{\"v\":2,\"invite\":\"\(nprofile)\"}", .unsupportedVersion),
            (ProtocolConstants.addBackKind, "{\"v\":1}", .malformedContent),
            (ProtocolConstants.addBackKind, "not json", .malformedContent),
        ]
        for (kind, content, expected) in cases {
            let wrap = try wrapped(kind: kind, content: content)
            XCTAssertEqual(rejection { _ = try AddBackRequest.read(wrap, keys: self.receiver, now: self.now) }, expected, content)
        }
        let extra = try AddBackRequest.read(
            try wrapped(kind: ProtocolConstants.addBackKind, content: "{\"v\":1,\"invite\":\"\(nprofile)\",\"photo\":\"later\"}"),
            keys: receiver, now: now
        )
        XCTAssertNil(extra.name)
    }

    func testRejectsOtherEventKinds() throws {
        let list = try RelayList.event(relays: ["wss://relay.example.com"], keys: sender, now: now)
        XCTAssertEqual(rejection { _ = try AddBackRequest.read(list, keys: self.receiver, now: self.now) }, .wrongKind)
    }

    func testKeepsTheNewestRequestOfEachSender() {
        let other = Invite(publicKey: Keys.generate().publicKey(), relays: [])
        let requests = [
            AddBackRequest(invite: invite, name: "old", createdAt: 10),
            AddBackRequest(invite: other, name: "other", createdAt: 15),
            AddBackRequest(invite: invite, name: "new", createdAt: 20),
        ]
        XCTAssertEqual(AddBackRequest.newestPerSender(requests).map(\.name), ["new", "other"])
    }
}
