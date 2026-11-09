import XCTest
@testable import Mismeet

final class StateStoreTests: XCTestCase {
    private var directory: URL!

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try FileManager.default.removeItem(at: directory)
    }

    private func store(_ text: String) throws -> StateStore {
        try Data(text.utf8).write(to: directory.appendingPathComponent("state.json"))
        return StateStore(url: directory.appendingPathComponent("state.json"))
    }

    func testLoadsTheStateFileThat010Writes() throws {
        let state = try store(Self.state010).load()
        XCTAssertEqual(state.contacts.count, 1)
        let contact = try XCTUnwrap(state.contacts.first)
        XCTAssertEqual(contact.name, "Aino")
        XCTAssertEqual(contact.relays, ["wss://nos.lol"])
        XCTAssertFalse(contact.share)
        XCTAssertEqual(contact.sharing, .sharing)
        XCTAssertEqual(contact.lastPayload?.latitude, 60.1699)
        XCTAssertEqual(contact.lastPayload?.battery, 0.5)
        XCTAssertEqual(contact.lastCreatedAt, 1_761_379_120)
        XCTAssertEqual(contact.photoSetAt, Date(timeIntervalSinceReferenceDate: 783_072_100))
        XCTAssertEqual(state.relays, PersistedState.defaultRelays)
        XCTAssertEqual(state.lastFix?.horizontalAccuracy, 8)
        XCTAssertNil(state.lastFix?.speed)
        XCTAssertTrue(state.sharingEnabled)
    }

    func testFillsInFieldsAnOlderFileLacks() throws {
        let state = try store(#"{"contacts":[{"id":"\#(Self.id)","name":"Eero","relays":[],"addedAt":1}]}"#).load()
        let contact = try XCTUnwrap(state.contacts.first)
        XCTAssertTrue(contact.share)
        XCTAssertEqual(contact.sharing, .unknown)
        XCTAssertNil(contact.photoSetAt)
        XCTAssertEqual(state.relays, PersistedState.defaultRelays)
        XCTAssertFalse(state.sharingEnabled)
        XCTAssertFalse(state.askedName)
    }

    func testMovesAnUndecodableFileAsideBeforeStartingEmpty() throws {
        let text = #"{"contacts":"not a list"}"#
        let store = try store(text)
        XCTAssertTrue(store.load().contacts.isEmpty)
        store.save(PersistedState(relays: ["wss://nos.lol"]))
        let names = try FileManager.default.contentsOfDirectory(atPath: directory.path)
        let aside = try XCTUnwrap(names.first { $0.hasPrefix("state-unreadable-") })
        XCTAssertEqual(try String(contentsOf: directory.appendingPathComponent(aside), encoding: .utf8), text)
        XCTAssertEqual(store.load().relays, ["wss://nos.lol"])
    }

    private static let id = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"

    // state.json as version 0.1.0 writes it. Keep it as it is: later versions must still read it.
    private static let state010 = """
        {"contacts":[{"id":"\(id)","name":"Aino","relays":["wss://nos.lol"],"share":false,"addedAt":783071800,\
        "sharing":"sharing","lastPayload":{"fixTime":1761379100,"latitude":60.1699,"longitude":24.9384,"accuracy":12,\
        "altitude":21,"speed":1.4,"course":0,"battery":0.5},"lastCreatedAt":1761379120,\
        "lastEventId":"5c83da77af1dec6d7289834998ad7aafbd9e2191396d75ec3cc27f5a77226f36","lastSeenAt":783072000,\
        "relayListCheckedAt":783072000,"photoSetAt":783072100}],\
        "relays":["wss://relay.damus.io","wss://nos.lol","wss://relay.primal.net"],"lastPublishedCreatedAt":1761379000,\
        "lastPublishedAt":783071800,"lastFix":{"time":783071790,"latitude":60.1712,"longitude":24.9414,\
        "horizontalAccuracy":8},"relayListPublishedAt":783062800}
        """
}
