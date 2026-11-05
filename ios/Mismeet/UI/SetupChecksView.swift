import CoreLocation
import SwiftUI

/// What has to hold for sharing to work, checked one by one as Slack's connection test does, each
/// with a way to fix it where iOS offers one.
struct SetupChecksView: View {
    private typealias Check = SetupChecks.Check
    private typealias Outcome = SetupChecks.Outcome

    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var servicesEnabled: Bool?
    @State private var relayChecks: [RelayCheck]?
    @State private var round = 0
    @State private var preparing = false
    @State private var report: Report?
    @State private var sent: Bool?

    private struct Report: Identifiable {
        let text: String
        var id: String { text }
    }

    var body: some View {
        List {
            Section("This phone") {
                ForEach(SetupChecks.phone(model: model, servicesEnabled: servicesEnabled, openSettings: openSettings)) { row($0) }
            }
            Section("Relays") {
                if let relayChecks {
                    ForEach(relayChecks.map { SetupChecks.relay($0, state: model.state) }) { row($0) }
                } else {
                    ProgressView("Asking each relay")
                }
            }
            Section("Contacts") {
                if model.state.contacts.isEmpty {
                    row(Check(title: "No contacts", outcome: .warning, detail: "Add someone from the invite they sent you."))
                }
                ForEach(model.state.contacts.map(SetupChecks.contact)) { row($0) }
            }
            Section("App") {
                row(Check(title: "Version", outcome: .info, detail: version))
                if let sent {
                    row(sent
                        ? Check(title: "Report", outcome: .ok, detail: "Sent to the developer")
                        : Check(title: "Report", outcome: .problem, detail: "Not sent: no relay took it. Try again later."))
                }
            }
        }
        .navigationTitle("Check setup")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                Button {
                    preparing = true
                    Task {
                        report = Report(text: await model.reportText(relayChecks: relayChecks))
                        preparing = false
                    }
                } label: {
                    Text(preparing ? "Preparing report..." : "Send report to the developer").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(preparing)
                Button { round += 1 } label: {
                    Text("Run checks again").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(relayChecks == nil)
            }
            .controlSize(.large)
            .padding()
            .background(.bar)
        }
        .sheet(item: $report) { report in
            ReportPreview(text: report.text) { sent = $0 }
        }
        .task(id: round) {
            relayChecks = nil
            servicesEnabled = await Task.detached { CLLocationManager.locationServicesEnabled() }.value
            relayChecks = await model.checkRelays()
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

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
    }

    private struct ReportPreview: View {
        let text: String
        let onSent: (Bool) -> Void
        @Environment(AppModel.self) private var model
        @Environment(\.dismiss) private var dismiss
        @State private var sending = false

        var body: some View {
            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("It goes encrypted to the developer, from a key made for this one report. It holds no location, contact names or keys.")
                        Text(text).font(.system(.footnote, design: .monospaced)).textSelection(.enabled)
                    }
                    .padding()
                }
                .navigationTitle("Send this report?")
                .navigationBarTitleDisplayMode(.inline)
                .safeAreaInset(edge: .bottom) {
                    HStack {
                        Button { dismiss() } label: { Text("Cancel").frame(maxWidth: .infinity) }
                            .buttonStyle(.bordered)
                        Button {
                            sending = true
                            Task {
                                onSent(await model.sendReport(text))
                                dismiss()
                            }
                        } label: {
                            Text(sending ? "Sending..." : "Send").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .controlSize(.large)
                    .disabled(sending)
                    .padding()
                    .background(.bar)
                }
            }
            .interactiveDismissDisabled(sending)
        }
    }

    private var version: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "Mismeet \(short) (\(build)), iOS \(UIDevice.current.systemVersion)"
    }
}
