import Foundation

/// The values of spec/SPEC.md section 10. Change the spec first.
enum ProtocolConstants {
    static let locationKind: UInt16 = 31122
    static let relayListKind: UInt16 = 10002
    static let giftWrapKind: UInt16 = 1059
    static let addBackKind: UInt16 = 1122
    static let addBackVersion = 1
    static let addBackExpirationSeconds: UInt64 = 2_592_000
    static let addBackFetchLimit: UInt64 = 100
    static let maxNameLength = 64
    static let chatMessageKind: UInt16 = 14
    static let reportExpirationSeconds: UInt64 = 2_592_000
    static let reportMaxLength = 8_000
    static let reportIntervalSeconds: TimeInterval = 86_400
    static let reportLogLines = 50
    static let reportsPublicKey = "1300350a75a59a37e42c4e34f788aa863c96eee9386ba68a39d2f6ee20e9b792"
    static let reportRelays = ["wss://relay.nostr.net", "wss://relay.primal.net"]
    static let identifier = "location"
    static let contentVersion = 1
    static let payloadVersion = 1
    static let expirationSeconds: UInt64 = 86_400
    static let entryBuckets = [4, 8, 16, 32, 64]
    static let maxSharingContacts = 64
    static let minRelaysPerList = 2
    static let maxRelaysPerList = 4
    static let maxRelaysPerContact = 8
    static let relayListRefreshSeconds: UInt64 = 86_400
    static let publishThrottleSeconds: TimeInterval = 60
    static let maxFixAgeSeconds: TimeInterval = 3_600
    static let regionRadiusMetres: Double = 150
    static let backgroundRefreshSeconds: TimeInterval = 14_400
    static let shareHourSeconds: TimeInterval = 3_600
    static let foregroundFetchIntervalSeconds: TimeInterval = 60
    static let connectTimeoutSeconds: TimeInterval = 5
    static let okTimeoutSeconds: TimeInterval = 10
    static let publishTimeoutSeconds: TimeInterval = 25
    static let rateLimitBackoffSeconds: TimeInterval = 300
    static let inviteLinkPrefix = "https://zululima.github.io/mismeet/i#"
    static let earlierInviteLinkPrefix = "https://lambdakilo.github.io/mismeet/i#"
    static let coordinateDecimals = 6
    static let revocationFillerLength = 100
    static let estimateMinSpeed = 1.0
    static let bikeSpeedFrom = 2.5
    static let carSpeedFrom = 8.5
    static let estimateSpanSeconds: Int64 = 300
    static let estimateMaxFixAgeSeconds: Int64 = 900
    static let roadSnapMetres = 50.0
    static let roadSnapBearingDegrees = 60.0
    static let roadCellsPerEstimate = 12
    static let roadCellCacheSeconds: TimeInterval = 2_592_000
    static let placeCellCacheSeconds: TimeInterval = 2_592_000
    static let placeNameMetres = 5_000.0
}
