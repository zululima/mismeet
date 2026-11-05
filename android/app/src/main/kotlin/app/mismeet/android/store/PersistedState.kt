package app.mismeet.android.store

import android.content.Context
import app.mismeet.protocol.AddBackRequest
import app.mismeet.protocol.Invite
import app.mismeet.protocol.LocationFix
import app.mismeet.protocol.LocationPayload
import java.io.File
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
    val requests: List<StoredRequest> = emptyList(),
    /** Senders whose add-back requests the user dismissed, as hex public keys. */
    val dismissedRequests: List<String> = emptyList(),
) {
    companion object {
        val DEFAULT_RELAYS = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")
    }
}

@Serializable
data class ShareFailure(val at: Long, val reason: String)

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
        return runCatching { json.decodeFromString(PersistedState.serializer(), text) }.getOrElse {
            savesBlocked = !file.renameTo(File(file.parentFile, "state-unreadable-${System.currentTimeMillis()}.json"))
            PersistedState()
        }
    }

    fun save(state: PersistedState) {
        if (savesBlocked) return
        val temp = File(file.parentFile, "state.json.tmp")
        temp.writeText(json.encodeToString(PersistedState.serializer(), state))
        if (!temp.renameTo(file)) file.writeText(temp.readText())
    }
}
