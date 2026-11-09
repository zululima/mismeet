package app.mismeet.android.store

import android.content.Context
import app.mismeet.protocol.AddBackRequest
import app.mismeet.protocol.Invite
import app.mismeet.protocol.LocationFix
import app.mismeet.protocol.LocationPayload
import app.mismeet.protocol.ProtocolConstants
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.RelayUrl

/** A contact record, spec section 4.3. Everything here stays on the device. */
@Serializable
data class Contact(
    val id: String,
    val name: String,
    val relays: List<String>,
    val share: Boolean = true,
    /** When a share for a limited time ends, in milliseconds; null shares until switched off. */
    val shareEndsAt: Long? = null,
    val addedAt: Long,
    val sharing: SharingStatus = SharingStatus.UNKNOWN,
    val lastPayload: LocationPayload? = null,
    val lastCreatedAt: Long? = null,
    val lastEventId: String? = null,
    val lastSeenAt: Long? = null,
    val relayListCheckedAt: Long? = null,
    val photoSetAt: Long? = null,
    /** The add-back request of spec section 4.5 was sent, or is not needed. */
    val addBackDone: Boolean = false,
) {
    enum class SharingStatus { UNKNOWN, SHARING, NOT_SHARING }

    fun publicKey(): PublicKey? = runCatching { PublicKey.parse(id) }.getOrNull()

    /** Whether an event published at [millis] includes this contact, spec section 8.3. */
    fun sharesAt(millis: Long): Boolean = share && (shareEndsAt == null || millis < shareEndsAt)

    /** The relays to ask about this contact: its own, or the app's list until a kind 10002 is seen. */
    fun queryRelays(defaults: List<String>): List<RelayUrl> =
        relays.ifEmpty { defaults }.mapNotNull { runCatching { RelayUrl.parse(it) }.getOrNull() }
}

@Serializable
data class PersistedState(
    val contacts: List<Contact> = emptyList(),
    val relays: List<String> = DEFAULT_RELAYS,
    val lastPublishedCreatedAt: Long? = null,
    val lastPublishedAt: Long? = null,
    val lastFix: LocationFix? = null,
    val relayListPublishedAt: Long? = null,
    val sharingEnabled: Boolean = false,
    /** Kept so that the setup checks can show a share that failed while the app was in the background. */
    val lastShareFailure: ShareFailure? = null,
    /** The name sent with add-back requests, spec section 4.5; empty sends none. */
    val ownName: String = "",
    /** The name was asked for once, at the first launch or the first after the question existed. */
    val askedName: Boolean = false,
    val requests: List<StoredRequest> = emptyList(),
    /** Senders whose add-back requests the user dismissed, as hex public keys. */
    val dismissedRequests: List<String> = emptyList(),
    /** Problem reports of spec section 8.8 after a failed share or a crash; off until turned on. */
    val reportsEnabled: Boolean = false,
    /** The last automatic report, which the daily limit counts from. */
    val lastReportAt: Long? = null,
) {
    /**
     * Spec section 7.4: nos.lol no longer accepts events, so relay.nostr.net takes its place, and
     * the relay list counts as unpublished until the new one goes out.
     */
    fun replacingRetiredRelay(): PersistedState {
        if (RETIRED_RELAY !in relays) return this
        return copy(relays = relays.map { if (it == RETIRED_RELAY) REPLACEMENT_RELAY else it }.distinct(), relayListPublishedAt = null)
    }

    companion object {
        val DEFAULT_RELAYS = listOf("wss://relay.damus.io", "wss://relay.nostr.net", "wss://relay.primal.net")
        private const val RETIRED_RELAY = "wss://nos.lol"
        private const val REPLACEMENT_RELAY = "wss://relay.nostr.net"
    }
}

/** The answers of the share question, spec section 4.2. */
enum class ShareDuration {
    HOUR,
    END_OF_DAY,
    INDEFINITELY;

    /** When a share given at [now] ends, in milliseconds, or null when it does not end. */
    fun endsAt(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? = when (this) {
        HOUR -> now + ProtocolConstants.LIMITED_SHARE_SECONDS * 1000
        END_OF_DAY -> Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        INDEFINITELY -> null
    }

    companion object {
        /** Whether [millis] is the start of a day in [zone], as the end of a share until the end of the day is. */
        fun isStartOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
            val local = Instant.ofEpochMilli(millis).atZone(zone)
            return local.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli() == millis
        }
    }
}

@Serializable
data class ShareFailure(
    val at: Long,
    /** In English, for problem reports and for versions that predate [kind]. */
    val reason: String,
    val kind: Kind? = null,
    /** What the relays answered, for [Kind.NOT_ACCEPTED]. */
    val detail: String? = null,
) {
    enum class Kind { NO_POSITION, OLD_POSITION, NOT_ACCEPTED, NOT_BUILT }
}

/** An add-back request waiting for the user, by the sender's hex public key. */
@Serializable
data class StoredRequest(val id: String, val invite: String, val name: String? = null, val createdAt: Long) {
    fun request(): AddBackRequest? = runCatching { AddBackRequest(Invite.parse(invite), name, createdAt) }.getOrNull()

    companion object {
        fun of(request: AddBackRequest) =
            StoredRequest(request.sender.toHex(), request.invite.nprofile(), request.name, request.createdAt)
    }
}

class StateStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var savesBlocked = false

    constructor(context: Context) : this(File(context.filesDir, "state.json"))

    /**
     * Starts empty when the file cannot be used, without losing it: a file that does not decode,
     * such as one a newer version wrote, moves aside to state-unreadable-<millis>.json, and one
     * that cannot be read at all blocks every save.
     */
    fun load(): PersistedState {
        if (!file.exists()) return PersistedState()
        val text = runCatching { file.readText() }.getOrElse {
            savesBlocked = true
            return PersistedState()
        }
        val state = runCatching { json.decodeFromString(PersistedState.serializer(), text) }.getOrElse {
            savesBlocked = !file.renameTo(File(file.parentFile, "state-unreadable-${System.currentTimeMillis()}.json"))
            return PersistedState()
        }
        return state.replacingRetiredRelay()
    }

    fun save(state: PersistedState) {
        if (savesBlocked) return
        val temp = File(file.parentFile, "state.json.tmp")
        temp.writeText(json.encodeToString(PersistedState.serializer(), state))
        if (!temp.renameTo(file)) file.writeText(temp.readText())
    }
}
