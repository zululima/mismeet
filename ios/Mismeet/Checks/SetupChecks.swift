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

    /// The language of the checks: the phone's on screen, English in problem reports.
    struct Language {
        let bundle: Bundle
        let locale: Locale

        static let screen = Language(bundle: .main, locale: .current)
        /// A key missing from the bundle comes back as itself, which is the English text.
        static let report = Language(
            bundle: Bundle.main.path(forResource: "en", ofType: "lproj").flatMap(Bundle.init(path:)) ?? Bundle(for: BundleMarker.self),
            locale: Locale(identifier: "en")
        )

        func text(_ value: String.LocalizationValue) -> String {
            String(localized: value, bundle: bundle, locale: locale)
        }

        func ago(_ date: Date) -> String {
            let now = Date()
            let formatter = RelativeDateTimeFormatter()
            formatter.locale = locale
            formatter.dateTimeStyle = .named
            return formatter.localizedString(for: min(date, now), relativeTo: now)
        }
    }

    private final class BundleMarker {}

    static func phone(model: AppModel, servicesEnabled: Bool?, openSettings: @escaping () -> Void, in language: Language = .screen) -> [Check] {
        let state = model.state
        let t = language.text
        guard state.sharingEnabled else {
            return [Check(
                title: t("Share my location"), outcome: .warning,
                detail: t("Off: this phone only watches its contacts and shares nothing."),
                fix: (t("Turn on"), { model.setSharingEnabled(true) })
            )]
        }
        var checks = [Check(title: t("Share my location"), outcome: .ok, detail: t("On"))]
        if servicesEnabled == false {
            checks.append(Check(
                title: t("Location Services"), outcome: .problem,
                detail: t("Off for the whole phone, under Settings > Privacy & Security > Location Services.")
            ))
        }
        switch model.authorization {
        case .authorizedAlways:
            checks.append(Check(title: t("Location permission"), outcome: .ok, detail: t("Always")))
        case .authorizedWhenInUse:
            checks.append(Check(
                title: t("Location permission"), outcome: .problem,
                detail: t("While using the app: sharing stops once Mismeet is in the background. Choose Always."),
                fix: (t("Open Settings"), openSettings)
            ))
        case .notDetermined:
            checks.append(Check(
                title: t("Location permission"), outcome: .problem, detail: t("Not asked yet."),
                fix: (t("Allow location"), { model.requestLocationAuthorization() })
            ))
        default:
            checks.append(Check(
                title: t("Location permission"), outcome: .problem, detail: t("Denied. Choose Always."),
                fix: (t("Open Settings"), openSettings)
            ))
        }
        if !model.preciseLocation {
            checks.append(Check(
                title: t("Precise location"), outcome: .problem,
                detail: t("Off: contacts see a point that can be kilometres away."),
                fix: (t("Open Settings"), openSettings)
            ))
        }
        if ProcessInfo.processInfo.isLowPowerModeEnabled {
            checks.append(Check(
                title: t("Low Power Mode"), outcome: .warning,
                detail: t("On: iOS cuts background work, which can delay sharing.")
            ))
        }
        let sharingWith = state.contacts.filter(\.share).count
        checks.append(sharingWith == 0
            ? Check(title: t("Sharing with"), outcome: .warning, detail: t("Nobody: add a contact, or turn sharing on in a contact's card."))
            : Check(title: t("Sharing with"), outcome: .ok, detail: t("\(sharingWith) contacts")))
        if let failure = state.lastShareFailure, failure.at > state.lastPublishedAt ?? .distantPast {
            checks.append(Check(title: t("Last share"), outcome: .problem, detail: t("Failed \(language.ago(failure.at)): \(failure.reason)")))
        } else if let at = state.lastPublishedAt {
            checks.append(Check(
                title: t("Last share"), outcome: .ok,
                detail: t("\(language.ago(at).capitalizedFirst). An iPhone shares when it has moved about 500 m and when Mismeet opens.")
            ))
        } else {
            checks.append(Check(title: t("Last share"), outcome: .warning, detail: t("Not yet")))
        }
        if let fix = state.lastFix {
            checks.append(Check(
                title: t("Last position"), outcome: .info,
                detail: t("\(language.ago(fix.time).capitalizedFirst), within \(Int(fix.horizontalAccuracy.rounded())) m")
            ))
        }
        return checks
    }

    static func relay(_ check: RelayCheck, state: PersistedState, in language: Language = .screen) -> Check {
        let t = language.text
        let name = URL(string: check.url)?.host() ?? check.url
        if let problem = check.problem {
            return Check(title: name, outcome: .problem, detail: t("Unreachable: \(problem)"))
        }
        guard let at = check.ownLocationAt else {
            let shared = state.sharingEnabled && state.lastPublishedCreatedAt != nil
            return Check(title: name, outcome: shared ? .warning : .ok, detail: shared ? t("Answers, but holds no location of yours") : t("Answers"))
        }
        let when = language.ago(Date(timeIntervalSince1970: TimeInterval(at)))
        if let last = state.lastPublishedCreatedAt, at < last {
            return Check(title: name, outcome: .warning, detail: t("Holds an older location of yours, shared \(when)"))
        }
        return Check(title: name, outcome: .ok, detail: t("Holds your location shared \(when)"))
    }

    static func contact(_ contact: Contact, in language: Language = .screen) -> Check {
        let t = language.text
        let status = ContactRow.status(of: contact, place: nil, bundle: language.bundle, locale: language.locale)
        switch contact.sharing {
        case .sharing: return Check(title: contact.name, outcome: .ok, detail: t("Shares with you. \(status)"))
        case .notSharing: return Check(title: contact.name, outcome: .warning, detail: t("Has not added you, or does not share with you"))
        case .unknown: return Check(title: contact.name, outcome: .warning, detail: t("No location found on their relays yet"))
        }
    }

    static func ago(_ date: Date) -> String {
        Language.screen.ago(date)
    }
}

private extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
