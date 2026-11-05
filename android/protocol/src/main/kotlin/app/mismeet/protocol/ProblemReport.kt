package app.mismeet.protocol

import java.time.Duration
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.Tag
import org.nostrdevkit.sdk.Timestamp
import org.nostrdevkit.sdk.UnwrappedGift
import org.nostrdevkit.sdk.nip59MakeGiftWrap

enum class ReportRejection {
    WRONG_KIND,
    MISSING_EXPIRATION,
    EXPIRED,
    UNWRAP_FAILED,
    WRONG_RUMOR_KIND,
    KEY_MISMATCH,
}

class RejectedReportException(val rejection: ReportRejection) : Exception(rejection.name)

/**
 * A problem report, spec section 8.8: plain text about what went wrong on a phone, sealed for
 * the reports key by keys made for that one report, so that nothing ties it to the phone's own.
 */
data class ProblemReport(val text: String, val createdAt: Long) {
    companion object {
        val reportsKey: PublicKey by lazy { PublicKey.parse(ProtocolConstants.REPORTS_PUBLIC_KEY) }

        fun wrap(text: String, receiver: PublicKey, now: Long): Event {
            val sender = Keys.generate()
            val rumor = EventBuilder(Kind(ProtocolConstants.CHAT_MESSAGE_KIND), clip(text))
                .tags(listOf(Tag.publicKey(receiver)))
                .customCreatedAt(Timestamp.fromSecs(now.toULong()))
                .finalizeUnsigned(sender.publicKey())
            return nip59MakeGiftWrap(sender, receiver, rumor, Duration.ofSeconds(ProtocolConstants.REPORT_EXPIRATION_SECONDS), emptyList())
        }

        fun read(wrap: Event, keys: Keys, now: Long): ProblemReport {
            if (wrap.kind().asU16() != ProtocolConstants.GIFT_WRAP_KIND) throw RejectedReportException(ReportRejection.WRONG_KIND)
            val expiration = wrap.tags().firstOrNull { it.kind() == "expiration" }?.content()?.toLongOrNull()
                ?: throw RejectedReportException(ReportRejection.MISSING_EXPIRATION)
            if (expiration <= now) throw RejectedReportException(ReportRejection.EXPIRED)
            val gift = try {
                UnwrappedGift.fromGiftWrap(keys, wrap)
            } catch (e: Exception) {
                throw RejectedReportException(ReportRejection.UNWRAP_FAILED)
            }
            val rumor = gift.rumor()
            if (rumor.kind().asU16() != ProtocolConstants.CHAT_MESSAGE_KIND) throw RejectedReportException(ReportRejection.WRONG_RUMOR_KIND)
            if (rumor.author().toHex() != gift.sender().toHex()) throw RejectedReportException(ReportRejection.KEY_MISMATCH)
            return ProblemReport(rumor.content(), rumor.createdAt().asSecs().toLong())
        }

        fun clip(text: String): String =
            if (text.length <= ProtocolConstants.REPORT_MAX_LENGTH) text else text.take(ProtocolConstants.REPORT_MAX_LENGTH - CUT.length) + CUT

        /**
         * [text] without what could tell who sent it: invite links, keys, relays outside
         * [keepRelays], coordinate pairs and the [names] the user gave contacts.
         */
        fun redact(text: String, names: Collection<String>, keepRelays: Collection<String>): String {
            val kept = keepRelays.map { it.trimEnd('/') }.toSet()
            var out = text
                .replace(INVITE, "[invite]")
                .replace(ENTITY, "[key]")
                .replace(HEX_KEY, "[key]")
                .replace(RELAY) { if (it.value.trimEnd('/') in kept) it.value else "[relay]" }
                .replace(POSITION, "[position]")
            names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }.forEach { name ->
                out = out.replace(Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])"), "[contact]")
            }
            return out
        }

        private const val CUT = "\n[cut]"
        private val INVITE = Regex("https?://\\S*/mismeet/i#\\S*")
        private val ENTITY = Regex("(?i)(nostr:)?(npub|nprofile|nsec|note|nevent|naddr|ncryptsec)1[02-9ac-hj-np-z]{6,}")
        private val HEX_KEY = Regex("(?<![0-9a-fA-F])[0-9a-fA-F]{64}(?![0-9a-fA-F])")
        private val RELAY = Regex("wss?://[^\\s,;)\\]]*[^\\s,;)\\]:.]")
        private val POSITION = Regex("-?\\d{1,3}\\.\\d{3,}\\s*,\\s*-?\\d{1,3}\\.\\d{3,}")
    }
}
