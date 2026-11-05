package app.mismeet.protocol

import org.nostrdevkit.sdk.Nip19
import org.nostrdevkit.sdk.Nip19Enum
import org.nostrdevkit.sdk.Nip19Profile
import org.nostrdevkit.sdk.Nip21
import org.nostrdevkit.sdk.Nip21Enum
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.RelayUrl

class NotAProfileException : Exception("not an npub or nprofile")

/** A contact invite, spec section 4.1: an invite link, a `nostr:` URI, or a bare `nprofile` or `npub`. */
data class Invite(val publicKey: PublicKey, val relays: List<RelayUrl>) {
    fun link(): String = ProtocolConstants.INVITE_LINK_PREFIX + nprofile()

    fun nprofile(): String = Nip19Profile(publicKey, relays.take(ProtocolConstants.MAX_RELAYS_PER_LIST)).toBech32()

    companion object {
        fun parse(text: String): Invite {
            val trimmed = text.trim()
                .removePrefix(ProtocolConstants.INVITE_LINK_PREFIX)
                .removePrefix(ProtocolConstants.EARLIER_INVITE_LINK_PREFIX)
            if (trimmed.startsWith("nostr:")) {
                return when (val entity = Nip21.parse(trimmed).asEnum()) {
                    is Nip21Enum.Pubkey -> Invite(entity.publicKey, emptyList())
                    is Nip21Enum.Profile -> Invite(entity.profile.publicKey(), entity.profile.relays())
                    else -> throw NotAProfileException()
                }
            }
            return when (val entity = Nip19.fromBech32(trimmed).asEnum()) {
                is Nip19Enum.Pubkey -> Invite(entity.npub, emptyList())
                is Nip19Enum.Profile -> Invite(entity.nprofile.publicKey(), entity.nprofile.relays())
                else -> throw NotAProfileException()
            }
        }
    }
}
