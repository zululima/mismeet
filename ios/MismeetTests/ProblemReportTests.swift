import XCTest
import NostrSDK
@testable import Mismeet

final class ProblemReportTests: XCTestCase {
    private let reports = Keys.generate()
    // The library takes the wrap's time from the clock, so the tests read at the real time.
    private let now = UInt64(Date().timeIntervalSince1970)

    private func rejection(_ block: () throws -> Void) -> ReportRejection? {
        do {
            try block()
            return nil
        } catch {
            return error as? ReportRejection
        }
    }

    func testReportsKeyIsTheOneInTheSpec() throws {
        XCTAssertEqual(try ProblemReport.reportsKey.toBech32(), "npub1zvqr2zn45kdr0epvfc600z92sc7fdmhf8p46dz3e6tmwug8fk7fquge3l4")
        XCTAssertEqual(ProtocolConstants.reportRelays, ["wss://offchain.pub", "wss://relay.primal.net"])
    }

    func testRoundTripsTheTextAsAChatMessage() throws {
        let wrap = try ProblemReport.wrap(text: "Mismeet problem report\nReason: test", receiver: reports.publicKey(), now: now)
        XCTAssertEqual(wrap.kind().asU16(), ProtocolConstants.giftWrapKind)
        XCTAssertEqual(wrap.tags().map { $0.kind() }.filter { $0 != "expiration" }, ["p"])
        let rumor = try UnwrappedGift.fromGiftWrap(signer: reports, giftWrap: wrap).rumor()
        XCTAssertEqual(rumor.kind().asU16(), ProtocolConstants.chatMessageKind)
        XCTAssertEqual(rumor.tags().map { $0.toVec() }, [["p", reports.publicKey().toHex()]])
        let report = try ProblemReport.read(wrap, keys: reports, now: now)
        XCTAssertEqual(report.text, "Mismeet problem report\nReason: test")
        XCTAssertEqual(report.createdAt, now)
    }

    func testEachReportIsSealedByAKeyOfItsOwn() throws {
        let first = try UnwrappedGift.fromGiftWrap(signer: reports, giftWrap: ProblemReport.wrap(text: "a", receiver: reports.publicKey(), now: now)).sender()
        let second = try UnwrappedGift.fromGiftWrap(signer: reports, giftWrap: ProblemReport.wrap(text: "b", receiver: reports.publicKey(), now: now)).sender()
        XCTAssertNotEqual(first, second)
        XCTAssertNotEqual(first, reports.publicKey())
    }

    func testWrapExpiresAfterThirtyDaysFromItsRandomisedTime() throws {
        let wrap = try ProblemReport.wrap(text: "x", receiver: reports.publicKey(), now: now)
        let expiration = UInt64(wrap.tags().first { $0.kind() == "expiration" }!.content()!)!
        XCTAssertEqual(expiration, wrap.createdAt().asSecs() + ProtocolConstants.reportExpirationSeconds)
        XCTAssertEqual(rejection { _ = try ProblemReport.read(wrap, keys: reports, now: expiration) }, .expired)
    }

    func testRejectsWhatIsNoReportForThisKey() throws {
        let wrap = try ProblemReport.wrap(text: "x", receiver: reports.publicKey(), now: now)
        XCTAssertEqual(rejection { _ = try ProblemReport.read(wrap, keys: Keys.generate(), now: now) }, .unwrapFailed)
        let sender = Keys.generate()
        let request = try AddBackRequest.wrap(
            invite: Invite(publicKey: sender.publicKey(), relays: []), name: nil, keys: sender, receiver: reports.publicKey(), now: now
        )
        XCTAssertEqual(rejection { _ = try ProblemReport.read(request, keys: reports, now: now) }, .wrongRumorKind)
        let location = try LocationEventBuilder.buildRevocation(keys: sender, now: now, previousCreatedAt: nil)
        XCTAssertEqual(rejection { _ = try ProblemReport.read(location, keys: reports, now: now) }, .wrongKind)
    }

    func testCutsLongTextAtTheLimit() {
        let text = ProblemReport.clip(String(repeating: "x", count: ProtocolConstants.reportMaxLength + 100))
        XCTAssertEqual(text.utf16.count, ProtocolConstants.reportMaxLength)
        XCTAssertTrue(text.hasSuffix("\n[cut]"))
        XCTAssertEqual(ProblemReport.clip("short"), "short")
    }

    func testRemovesWhatCouldTellWhoSentIt() throws {
        let key = Keys.generate().publicKey()
        let invite = Invite(publicKey: key, relays: [try RelayUrl.parse(url: "wss://relay.example.com")])
        let text = [
            "Invite: \(try invite.link())",
            "Own key \(try key.toBech32()) and \(try invite.nprofile())",
            "Event \(key.toHex())",
            "No relay accepted it: wss://nos.lol: ok, wss://home.example.org/: timeout",
            "Fix at 60.169912, 24.938412 within 12 m",
            "Add-back request to Bea: accepted, Beatrice too; Mia Lee waits",
        ].joined(separator: "\n")
        let redacted = ProblemReport.redact(text, names: ["Bea", "Mia Lee", " "], keepRelays: ["wss://nos.lol", "wss://relay.primal.net"])
        XCTAssertEqual(redacted, [
            "Invite: [invite]",
            "Own key [key] and [key]",
            "Event [key]",
            "No relay accepted it: wss://nos.lol: ok, [relay]: timeout",
            "Fix at [position] within 12 m",
            "Add-back request to [contact]: accepted, Beatrice too; [contact] waits",
        ].joined(separator: "\n"))
    }

    func testCrashTextNamesTheCauseAndTheCrashedThreadsFrames() {
        let json = """
        {"callStackTree":{"callStackPerThread":true,"callStacks":[
          {"threadAttributed":false,"callStackRootFrames":[{"binaryName":"libsystem_kernel.dylib","binaryUUID":"AAA","offsetIntoBinaryTextSegment":1,"subFrames":[]}]},
          {"threadAttributed":true,"callStackRootFrames":[{"binaryName":"Mismeet","binaryUUID":"70B89F27-1634-3580-A695-57CDB41D7743",
            "offsetIntoBinaryTextSegment":165304,"address":7170766264,
            "subFrames":[{"binaryName":"UIKitCore","binaryUUID":"BBB","offsetIntoBinaryTextSegment":42,"subFrames":[]}]}]}]},
         "diagnosticMetaData":{"appVersion":"0.1.0","appBuildVersion":"19","osVersion":"iPhone OS 18.5 (22F76)","deviceType":"iPhone12,8",
           "regionFormat":"FI","exceptionType":1,"exceptionCode":0,"signal":11,"terminationReason":"Namespace SIGNAL, Code 0xb"}}
        """
        XCTAssertEqual(ProblemReports.crashText(Data(json.utf8)), """
        Crash: exception type 1, code 0, signal 11, Namespace SIGNAL, Code 0xb
        Build: 0.1.0, 19, iPhone OS 18.5 (22F76), iPhone12,8
        0 Mismeet +165304
        1 UIKitCore +42
        Binaries: Mismeet 70B89F27-1634-3580-A695-57CDB41D7743, UIKitCore BBB
        """)
    }
}
