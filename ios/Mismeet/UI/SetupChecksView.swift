import CoreLocation
import SwiftUI

/// What has to hold for sharing to work, checked one by one as Slack's connection test does, each
/// with a way to fix it where iOS offers one.
struct SetupChecksView: View {
    private enum Outcome {
        case ok, warning, problem, info
    }

    private struct Check: Identifiable {
        let title: String
        let outcome: Outcome
        let detail: String
        var fix: (label: String, action: () -> Void)?

        var id: String { title }
    }

    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var servicesEnabled: Bool?
    @State private var relayChecks: [RelayCheck]?
    @State private var round = 0

    var body: some View {
        List {
            Section("This phone") {
                ForEach(phoneChecks) { row($0) }
            }
            Section("Relays") {
                if let relayChecks {
                    ForEach(relayChecks.map(relayCheck)) { row($0) }
                } else {
                    ProgressView("Asking each relay")
                }
            }
            Section("Contacts") {
                if model.state.contacts.isEmpty {
                    row(Check(title: "No contacts", outcome: .warning, detail: "Add someone from the invite they sent you."))
                }
                ForEach(model.state.contacts.map(contactCheck)) { row($0) }
            }
            Section("App") {
                row(Check(title: "Version", outcome: .info, detail: version))
            }
        }
        .navigationTitle("Check setup")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            Button { round += 1 } label: {
                Text("Run checks again").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .padding()
            .disabled(relayChecks == nil)
        }
        .task(id: round) {
            relayChecks = nil
            servicesEnabled = await Task.detached { CLLocationManager.locationServicesEnabled() }.value
            relayChecks = await model.checkRelays()
        }
    }

    private var phoneChecks: [Check] {
        let state = model.state
        guard state.sharingEnabled else {
            return [Check(
                title: "Share my location", outcome: .warning,
                detail: "Off: this phone only watches its contacts and shares nothing.",
                fix: ("Turn on", { model.setSharingEnabled(true) })
            )]
        }
        var checks = [Check(title: "Share my location", outcome: .ok, detail: "On")]
        if servicesEnabled == false {
            checks.append(Check(
                title: "Location Services", outcome: .problem,
                detail: "Off for the whole phone, under Settings > Privacy & Security > Location Services."
            ))
        }
        switch model.authorization {
        case .authorizedAlways:
            checks.append(Check(title: "Location permission", outcome: .ok, detail: "Always"))
        case .authorizedWhenInUse:
            checks.append(Check(
                title: "Location permission", outcome: .problem,
                detail: "While using the app: sharing stops once Mismeet is in the background. Choose Always.",
                fix: ("Open Settings", openSettings)
            ))
        case .notDetermined:
            checks.append(Check(
                title: "Location permission", outcome: .problem, detail: "Not asked yet.",
                fix: ("Allow location", { model.requestLocationAuthorization() })
            ))
        default:
            checks.append(Check(
                title: "Location permission", outcome: .problem, detail: "Denied. Choose Always.",
                fix: ("Open Settings", openSettings)
            ))
        }
        if !model.preciseLocation {
            checks.append(Check(
                title: "Precise location", outcome: .problem,
                detail: "Off: contacts see a point that can be kilometres away.",
                fix: ("Open Settings", openSettings)
            ))
        }
        if ProcessInfo.processInfo.isLowPowerModeEnabled {
            checks.append(Check(
                title: "Low Power Mode", outcome: .warning,
                detail: "On: iOS cuts background work, which can delay sharing."
            ))
        }
        let sharingWith = state.contacts.filter(\.share).count
        checks.append(sharingWith == 0
            ? Check(title: "Sharing with", outcome: .warning, detail: "Nobody: add a contact, or turn sharing on in a contact's card.")
            : Check(title: "Sharing with", outcome: .ok, detail: sharingWith == 1 ? "1 contact" : "\(sharingWith) contacts"))
        if let failure = state.lastShareFailure, failure.at > state.lastPublishedAt ?? .distantPast {
            checks.append(Check(title: "Last share", outcome: .problem, detail: "Failed \(ago(failure.at)): \(failure.reason)"))
        } else if let at = state.lastPublishedAt {
            checks.append(Check(
                title: "Last share", outcome: .ok,
                detail: "\(ago(at).capitalizedFirst). An iPhone shares when it has moved about 500 m and when Mismeet opens."
            ))
        } else {
            checks.append(Check(title: "Last share", outcome: .warning, detail: "Not yet"))
        }
        if let fix = state.lastFix {
            checks.append(Check(
                title: "Last position", outcome: .info,
                detail: "\(ago(fix.time).capitalizedFirst), within \(Int(fix.horizontalAccuracy.rounded())) m"
            ))
        }
        return checks
    }

    private func relayCheck(_ check: RelayCheck) -> Check {
        let name = URL(string: check.url)?.host() ?? check.url
        if let problem = check.problem {
            return Check(title: name, outcome: .problem, detail: "Unreachable: \(problem)")
        }
        guard let at = check.ownLocationAt else {
            let shared = model.state.sharingEnabled && model.state.lastPublishedCreatedAt != nil
            return Check(title: name, outcome: shared ? .warning : .ok, detail: shared ? "Answers, but holds no location of yours" : "Answers")
        }
        let when = ago(Date(timeIntervalSince1970: TimeInterval(at)))
        if let last = model.state.lastPublishedCreatedAt, at < last {
            return Check(title: name, outcome: .warning, detail: "Holds an older location of yours, shared \(when)")
        }
        return Check(title: name, outcome: .ok, detail: "Holds your location shared \(when)")
    }

    private func contactCheck(_ contact: Contact) -> Check {
        let status = ContactRow.status(of: contact, place: nil)
        switch contact.sharing {
        case .sharing: return Check(title: contact.name, outcome: .ok, detail: "Shares with you. \(status)")
        case .notSharing: return Check(title: contact.name, outcome: .warning, detail: "Has not added you, or does not share with you")
        case .unknown: return Check(title: contact.name, outcome: .warning, detail: "No location found on their relays yet")
        }
    }

    private func row(_ check: Check) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Image(systemName: symbol(check.outcome)).foregroundStyle(color(check.outcome))
            VStack(alignment: .leading, spacing: 4) {
                Text(check.title).font(.headline)
                Text(check.detail).font(.subheadline).foregroundStyle(.secondary)
                if let fix = check.fix {
                    Button(fix.label, action: fix.action).buttonStyle(.bordered)
                }
            }
        }
    }

    private func symbol(_ outcome: Outcome) -> String {
        switch outcome {
        case .ok: "checkmark.circle.fill"
        case .warning: "exclamationmark.triangle.fill"
        case .problem: "xmark.octagon.fill"
        case .info: "info.circle.fill"
        }
    }

    private func color(_ outcome: Outcome) -> Color {
        switch outcome {
        case .ok: .green
        case .warning: .orange
        case .problem: .red
        case .info: .secondary
        }
    }

    private func ago(_ date: Date) -> String {
        RelativeDateTimeFormatter().localizedString(for: date, relativeTo: Date())
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
    }

    private var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "Mismeet \(short) (\(build)), iOS \(UIDevice.current.systemVersion)"
    }
}

private extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
