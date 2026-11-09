import Foundation
import MetricKit
import Synchronization
import UIKit

/// The text of the problem reports of spec section 8.8, and what it draws on.
enum ProblemReports {
    private static let lines = Mutex<[String]>([])

    /// Keeps `text` among the last log lines, which only a report ever reads.
    static func record(_ text: String) {
        let stamp = Date().formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits).second(.twoDigits))
        lines.withLock { lines in
            lines.append("\(stamp) \(text)")
            if lines.count > ProtocolConstants.reportLogLines { lines.removeFirst(lines.count - ProtocolConstants.reportLogLines) }
        }
    }

    private static var recentLog: [String] { lines.withLock { $0 } }

    private static var crashURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Mismeet", isDirectory: true)
            .appendingPathComponent("crash.txt")
    }

    static var pendingCrash: String? { try? String(contentsOf: crashURL, encoding: .utf8) }

    static func saveCrash(_ text: String) {
        try? FileManager.default.createDirectory(at: crashURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? Data(text.utf8).write(to: crashURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    static func forgetCrash() {
        try? FileManager.default.removeItem(at: crashURL)
    }

    /// A MetricKit crash diagnostic as text: what stopped the app and the crashed thread's frames,
    /// as binary and offset, which the archive's dSYM turns into code lines.
    static func crashText(_ json: Data) -> String? {
        guard let root = (try? JSONSerialization.jsonObject(with: json)) as? [String: Any] else { return nil }
        let meta = root["diagnosticMetaData"] as? [String: Any] ?? [:]
        var out: [String] = []
        let cause = [
            (meta["exceptionType"] as? Int).map { "exception type \($0)" },
            (meta["exceptionCode"] as? Int).map { "code \($0)" },
            (meta["signal"] as? Int).map { "signal \($0)" },
            meta["terminationReason"] as? String,
        ].compactMap { $0 }
        out.append("Crash: " + (cause.isEmpty ? "unknown cause" : cause.joined(separator: ", ")))
        let build = [meta["appVersion"], meta["appBuildVersion"], meta["osVersion"], meta["deviceType"]].compactMap { $0 as? String }
        if !build.isEmpty { out.append("Build: " + build.joined(separator: ", ")) }
        if let reason = meta["exceptionReason"] as? [String: Any] {
            let name = reason["exceptionName"] as? String ?? reason["className"] as? String ?? "Exception"
            out.append("\(name): \(reason["composedMessage"] as? String ?? "")")
        }
        let tree = root["callStackTree"] as? [String: Any]
        let stacks = tree?["callStacks"] as? [[String: Any]] ?? []
        let crashed = stacks.first { $0["threadAttributed"] as? Bool == true } ?? stacks.first
        var frame = (crashed?["callStackRootFrames"] as? [[String: Any]])?.first
        var binaries: [String: String] = [:]
        var index = 0
        while let current = frame, index < crashFrames {
            let binary = current["binaryName"] as? String ?? "?"
            let offset = current["offsetIntoBinaryTextSegment"] as? Int ?? 0
            out.append("\(index) \(binary) +\(offset)")
            if let uuid = current["binaryUUID"] as? String { binaries[binary] = uuid }
            frame = (current["subFrames"] as? [[String: Any]])?.first
            index += 1
        }
        if !binaries.isEmpty {
            out.append("Binaries: " + binaries.sorted { $0.key < $1.key }.map { "\($0.key) \($0.value)" }.joined(separator: ", "))
        }
        return out.joined(separator: "\n")
    }

    @MainActor
    static func text(
        model: AppModel, servicesEnabled: Bool?, relayChecks: [RelayCheck], reason: String, automatic: Bool, crash: String?
    ) -> String {
        let state = model.state
        let info = Bundle.main.infoDictionary
        let version = "\(info?["CFBundleShortVersionString"] as? String ?? "?") (\(info?["CFBundleVersion"] as? String ?? "?"))"
        var out = [
            "Mismeet problem report",
            "Reason: \(reason)",
            "Sent: \(automatic ? "automatically" : "by the user")",
            "App: Mismeet \(version)\(isDebug ? ", debug build" : "")",
            "Phone: iOS \(UIDevice.current.systemVersion), \(deviceModel)",
            "",
            "This phone",
        ]
        out += SetupChecks.phone(model: model, servicesEnabled: servicesEnabled, openSettings: {}, in: .report).map(line)
        out += ["", "Relays"]
        out += relayChecks.map { check in
            var shown = SetupChecks.relay(check, state: state, in: .report)
            if !PersistedState.defaultRelays.contains(check.url.trimmingCharacters(in: CharacterSet(charactersIn: "/"))) {
                shown.title = "another relay"
            }
            return line(shown)
        }
        let contacts = state.contacts
        out += [
            "",
            "Contacts: \(contacts.count), sharing with \(contacts.filter(\.share).count), "
                + "\(contacts.filter { $0.sharing == .sharing }.count) share back, "
                + "\(contacts.filter { $0.sharing == .notSharing }.count) do not, "
                + "\(contacts.filter { $0.sharing == .unknown }.count) not found yet; "
                + "\(state.requests.count) add-back requests waiting",
        ]
        if let crash { out += ["", "Crash on an earlier run", crash] }
        out += ["", "Log"] + recentLog
        let names = contacts.map(\.name) + state.requests.compactMap(\.name) + [state.ownName]
        let text = out.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        return ProblemReport.clip(ProblemReport.redact(text, names: names, keepRelays: PersistedState.defaultRelays))
    }

    private static func line(_ check: SetupChecks.Check) -> String {
        "- \(check.outcome.rawValue): \(check.title): \(check.detail)"
    }

    private static var deviceModel: String {
        if let simulated = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] { return "\(simulated) simulator" }
        var info = utsname()
        uname(&info)
        return withUnsafeBytes(of: &info.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
    }

    private static var isDebug: Bool {
        #if DEBUG
        true
        #else
        false
        #endif
    }

    private static let crashFrames = 40
}

/// Hands the crash diagnostics MetricKit delivers after a crash to the problem reports.
final class CrashReceiver: NSObject, MXMetricManagerSubscriber, @unchecked Sendable {
    static let shared = CrashReceiver()

    func start() {
        MXMetricManager.shared.add(self)
    }

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        let crashes = payloads.flatMap { $0.crashDiagnostics ?? [] }.compactMap { ProblemReports.crashText($0.jsonRepresentation()) }
        guard !crashes.isEmpty else { return }
        ProblemReports.saveCrash(crashes.joined(separator: "\n\n"))
        Task { @MainActor in AppModel.shared.crashArrived() }
    }
}
