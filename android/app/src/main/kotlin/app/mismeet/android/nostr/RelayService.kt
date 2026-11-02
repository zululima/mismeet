package app.mismeet.android.nostr

import app.mismeet.protocol.ProtocolConstants
import java.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.nostrdevkit.sdk.AckPolicy
import org.nostrdevkit.sdk.Client
import org.nostrdevkit.sdk.ClientBuilder
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.Filter
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.ReqExitPolicy
import org.nostrdevkit.sdk.ReqTarget
import org.nostrdevkit.sdk.SendEventTarget

/** One short-lived client per operation: connect, do the work, shut down. Spec sections 7.2 and 7.3. */
class RelayService {
    data class PublishResult(val succeeded: List<String>, val failed: Map<String, String>) {
        val summary: String
            get() = failed.entries.sortedBy { it.key }.joinToString("; ") { "${it.key}: ${it.value}" }
    }

    /**
     * One relay as the setup checks found it: [problem] is null when it answered, and
     * [ownLocationAt] is the `created_at` of this phone's location event it holds.
     */
    data class RelayCheck(val url: String, val problem: String? = null, val ownLocationAt: Long? = null)

    /** Connects to each relay on its own and asks it for [author]'s location event, all at once. */
    suspend fun check(relayUrls: List<String>, author: PublicKey): List<RelayCheck> = coroutineScope {
        relayUrls.map { async { check(it, author) } }.awaitAll()
    }

    private suspend fun check(text: String, author: PublicKey): RelayCheck {
        val url = runCatching { RelayUrl.parse(text) }.getOrNull() ?: return RelayCheck(text, "Not a relay address")
        val client = ClientBuilder().connectTimeout(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS)).build()
        try {
            if (runCatching { client.addRelay(url, null, false, null) }.isFailure) return RelayCheck(text, "Not a relay address")
            val output = client.tryConnect(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS))
            if (output.success.isEmpty()) {
                return RelayCheck(text, output.failed.values.firstOrNull() ?: "No answer within ${ProtocolConstants.CONNECT_TIMEOUT_SECONDS} seconds")
            }
            val filter = Filter()
                .kinds(listOf(Kind(ProtocolConstants.LOCATION_KIND)))
                .authors(listOf(author))
                .identifier(ProtocolConstants.IDENTIFIER)
            val events = client.fetchEvents(
                ReqTarget.manual(mapOf(url to listOf(filter))),
                Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS),
                ReqExitPolicy.ExitOnEose,
                null,
            )
            return RelayCheck(text, ownLocationAt = events.maxOfOrNull { it.createdAt().asSecs().toLong() })
        } catch (e: Exception) {
            return RelayCheck(text, e.message ?: e.toString())
        } finally {
            client.shutdown()
        }
    }

    suspend fun publish(event: Event, relayUrls: List<String>): PublishResult {
        val client = connectedClient(relayUrls) ?: return PublishResult(emptyList(), mapOf("relays" to "none reachable"))
        try {
            val output = client.sendEvent(
                event,
                SendEventTarget.broadcast(),
                AckPolicy.all(),
                Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS),
                null,
            )
            return PublishResult(
                output.success.map { it.toString() },
                output.failed.entries.associate { it.key.toString() to it.value },
            )
        } catch (e: Exception) {
            return PublishResult(emptyList(), mapOf("send" to (e.message ?: e.toString())))
        } finally {
            client.shutdown()
        }
    }

    suspend fun fetch(targets: Map<RelayUrl, List<Filter>>): List<Event> {
        val client = connectedClient(targets.keys.map { it.toString() }) ?: return emptyList()
        try {
            return client.fetchEvents(
                ReqTarget.manual(targets),
                Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS),
                ReqExitPolicy.ExitOnEose,
                null,
            )
        } catch (e: Exception) {
            return emptyList()
        } finally {
            client.shutdown()
        }
    }

    private suspend fun connectedClient(relayUrls: List<String>): Client? {
        val client = ClientBuilder().connectTimeout(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS)).build()
        var added = 0
        for (text in relayUrls) {
            val url = runCatching { RelayUrl.parse(text) }.getOrNull() ?: continue
            if (runCatching { client.addRelay(url, null, false, null) }.isSuccess) added++
        }
        if (added == 0) return null
        client.tryConnect(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS))
        return client
    }
}
