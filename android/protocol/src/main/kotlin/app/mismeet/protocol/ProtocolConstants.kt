package app.mismeet.protocol

/** The values of spec/SPEC.md section 10. Change the spec first. */
object ProtocolConstants {
    val LOCATION_KIND: UShort = 31122u
    val RELAY_LIST_KIND: UShort = 10002u
    val GIFT_WRAP_KIND: UShort = 1059u
    val ADD_BACK_KIND: UShort = 1122u
    const val ADD_BACK_VERSION = 1
    const val ADD_BACK_EXPIRATION_SECONDS = 2_592_000L
    const val ADD_BACK_FETCH_LIMIT = 100L
    const val MAX_NAME_LENGTH = 64
    val CHAT_MESSAGE_KIND: UShort = 14u
    const val REPORT_EXPIRATION_SECONDS = 2_592_000L
    const val REPORT_MAX_LENGTH = 8_000
    const val REPORT_INTERVAL_SECONDS = 86_400L
    const val REPORT_LOG_LINES = 50
    const val REPORTS_PUBLIC_KEY = "1300350a75a59a37e42c4e34f788aa863c96eee9386ba68a39d2f6ee20e9b792"
    val REPORT_RELAYS = listOf("wss://offchain.pub", "wss://relay.primal.net")
    const val IDENTIFIER = "location"
    const val CONTENT_VERSION = 1
    const val PAYLOAD_VERSION = 1
    const val EXPIRATION_SECONDS = 86_400L
    val ENTRY_BUCKETS = listOf(4, 8, 16, 32, 64)
    const val MAX_SHARING_CONTACTS = 64
    const val MIN_RELAYS_PER_LIST = 2
    const val MAX_RELAYS_PER_LIST = 4
    const val MAX_RELAYS_PER_CONTACT = 8
    const val RELAY_LIST_REFRESH_SECONDS = 86_400L
    const val PUBLISH_THROTTLE_SECONDS = 60L
    const val MAX_FIX_AGE_SECONDS = 3_600L
    const val ANDROID_PUBLISH_INTERVAL_SECONDS = 600L
    const val LIMITED_SHARE_SECONDS = 3_600L
    const val FOREGROUND_FETCH_INTERVAL_SECONDS = 60L
    const val CONNECT_TIMEOUT_SECONDS = 5L
    const val OK_TIMEOUT_SECONDS = 10L
    const val PUBLISH_TIMEOUT_SECONDS = 25L
    const val RATE_LIMIT_BACKOFF_SECONDS = 300L
    const val INVITE_LINK_PREFIX = "https://zululima.github.io/mismeet/i#"
    const val EARLIER_INVITE_LINK_PREFIX = "https://lambdakilo.github.io/mismeet/i#"
    const val COORDINATE_DECIMALS = 6
    const val REVOCATION_FILLER_LENGTH = 100
    const val ESTIMATE_MIN_SPEED = 1.0
    const val BIKE_SPEED_FROM = 2.5
    const val CAR_SPEED_FROM = 8.5
    const val ESTIMATE_SPAN_SECONDS = 300L
    const val ESTIMATE_MAX_FIX_AGE_SECONDS = 900L
    const val ROAD_SNAP_METRES = 50.0
    const val ROAD_SNAP_BEARING_DEGREES = 60.0
    const val ROAD_CELLS_PER_ESTIMATE = 12
    const val ROAD_CELL_CACHE_SECONDS = 2_592_000L
    const val PLACE_CELL_CACHE_SECONDS = 2_592_000L
    const val PLACE_NAME_METRES = 5_000.0
}
