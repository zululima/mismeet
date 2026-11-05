import CoreLocation
import Foundation

/// The setup checks of the Check setup screen, which problem reports repeat.
@MainActor
enum SetupChecks {
    enum Outcome: String {
        case ok = "OK", warning = "WARNING", problem = "PROBLEM", info = "INFO"
    }

    struct Check: Identifiable {
        var title: String
        let outcome: Outcome
        let detail: String
        var fix: (label: String, action: () -> Void)?

        var id: String { title }
    }

    static func phone(model: AppModel, servicesEnabled: Bool?, openSettings: @escaping () -> Void) -> [Check] {
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

    static func relay(_ check: RelayCheck, state: PersistedState) -> Check {
        let name = URL(string: check.url)?.host() ?? check.url
        if let problem = check.problem {
            return Check(title: name, outcome: .problem, detail: "Unreachable: \(problem)")
        }
        guard let at = check.ownLocationAt else {
            let shared = state.sharingEnabled && state.lastPublishedCreatedAt != nil
            return Check(title: name, outcome: shared ? .warning : .ok, detail: shared ? "Answers, but holds no location of yours" : "Answers")
        }
        let when = ago(Date(timeIntervalSince1970: TimeInterval(at)))
        if let last = state.lastPublishedCreatedAt, at < last {
            return Check(title: name, outcome: .warning, detail: "Holds an older location of yours, shared \(when)")
        }
        return Check(title: name, outcome: .ok, detail: "Holds your location shared \(when)")
    }

    static func contact(_ contact: Contact) -> Check {
        let status = ContactRow.status(of: contact, place: nil)
        switch contact.sharing {
        case .sharing: return Check(title: contact.name, outcome: .ok, detail: "Shares with you. \(status)")
        case .notSharing: return Check(title: contact.name, outcome: .warning, detail: "Has not added you, or does not share with you")
        case .unknown: return Check(title: contact.name, outcome: .warning, detail: "No location found on their relays yet")
        }
    }

    static func ago(_ date: Date) -> String {
        let now = Date()
        return RelativeDateTimeFormatter().localizedString(for: min(date, now), relativeTo: now)
    }
}

private extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
