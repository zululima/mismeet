package app.mismeet.protocol

import java.time.Duration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.Timestamp
import org.nostrdevkit.sdk.UnwrappedGift
import org.nostrdevkit.sdk.nip59MakeGiftWrap

enum class AddBackRejection {
    WRONG_KIND,
    MISSING_EXPIRATION,
    EXPIRED,
    UNWRAP_FAILED,
    WRONG_RUMOR_KIND,
    MALFORMED_CONTENT,
    UNSUPPORTED_VERSION,
    KEY_MISMATCH,
    OWN_KEY,
}

class RejectedRequestException(val rejection: AddBackRejection) : Exception(rejection.name)

/**
 * An add-back request, spec section 4.5: someone added this phone's user from their invite and
 * sends their own [invite], with the [name] they gave themselves, so that they can be added back.
 */
data class AddBackRequest(val invite: Invite, val name: String?, val createdAt: Long) {
    val sender: PublicKey get() = invite.publicKey

    companion object {
        fun content(invite: Invite, name: String?): String = Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("v", ProtocolConstants.ADD_BACK_VERSION)
                put("invite", invite.nprofile())
                cleanName(name)?.let { put("name", it) }
            },
        )

        /** The gift wrap carrying [invite] and [name] from [keys] to [receiver]. */
        fun wrap(invite: Invite, name: String?, keys: Keys, receiver: PublicKey, now: Long): Event {
            val rumor = EventBuilder(Kind(ProtocolConstants.ADD_BACK_KIND), content(invite, name))
                .customCreatedAt(Timestamp.fromSecs(now.toULong()))
                .finalizeUnsigned(keys.publicKey())
            return nip59MakeGiftWrap(keys, receiver, rumor, Duration.ofSeconds(ProtocolConstants.ADD_BACK_EXPIRATION_SECONDS), emptyList())
        }

        fun read(wrap: Event, keys: Keys, now: Long): AddBackRequest {
            if (wrap.kind().asU16() != ProtocolConstants.GIFT_WRAP_KIND) throw RejectedRequestException(AddBackRejection.WRONG_KIND)
            val expiration = wrap.tags().firstOrNull { it.kind() == "expiration" }?.content()?.toLongOrNull()
                ?: throw RejectedRequestException(AddBackRejection.MISSING_EXPIRATION)
            if (expiration <= now) throw RejectedRequestException(AddBackRejection.EXPIRED)
            val gift = try {
                UnwrappedGift.fromGiftWrap(keys, wrap)
            } catch (e: Exception) {
                throw RejectedRequestException(AddBackRejection.UNWRAP_FAILED)
            }
            val rumor = gift.rumor()
            if (rumor.kind().asU16() != ProtocolConstants.ADD_BACK_KIND) throw RejectedRequestException(AddBackRejection.WRONG_RUMOR_KIND)
            val sender = gift.sender().toHex()
            if (rumor.author().toHex() != sender) throw RejectedRequestException(AddBackRejection.KEY_MISMATCH)
            val fields = runCatching { Json.parseToJsonElement(rumor.content()) as JsonObject }.getOrNull()
                ?: throw RejectedRequestException(AddBackRejection.MALFORMED_CONTENT)
            val version = (fields["v"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            if (version != ProtocolConstants.ADD_BACK_VERSION) throw RejectedRequestException(AddBackRejection.UNSUPPORTED_VERSION)
            val inviteText = (fields["invite"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw RejectedRequestException(AddBackRejection.MALFORMED_CONTENT)
            val invite = runCatching { Invite.parse(inviteText) }.getOrNull()
                ?: throw RejectedRequestException(AddBackRejection.MALFORMED_CONTENT)
            if (invite.publicKey.toHex() != sender) throw RejectedRequestException(AddBackRejection.KEY_MISMATCH)
            if (sender == keys.publicKey().toHex()) throw RejectedRequestException(AddBackRejection.OWN_KEY)
            val name = (fields["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            return AddBackRequest(invite, cleanName(name), rumor.createdAt().asSecs().toLong())
        }

        /** The newest request of each sender among [requests], newest first. */
        fun newestPerSender(requests: List<AddBackRequest>): List<AddBackRequest> = requests
            .groupBy { it.sender.toHex() }
            .map { (_, own) -> own.maxBy { it.createdAt } }
            .sortedByDescending { it.createdAt }

        fun cleanName(name: String?): String? =
            name?.trim()?.take(ProtocolConstants.MAX_NAME_LENGTH)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
