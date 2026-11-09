package app.mismeet.protocol

import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.nostrdevkit.sdk.AckPolicy
import org.nostrdevkit.sdk.Client
import org.nostrdevkit.sdk.ClientBuilder
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.Filter
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.ReqExitPolicy
import org.nostrdevkit.sdk.ReqTarget
import org.nostrdevkit.sdk.SendEventTarget

/**
 * Asks each relay on its own what it does with Mismeet's events from a new key: whether it
 * takes a relay list and a location event, keeps it, replaces it with one published a few seconds
 * later as the app does after a contact change, and serves the newest back. Prints each relay's
 * `OK` message, which the apps log only when every relay refuses. Run through the relayProbe
 * Gradle task, with relays as arguments or none for the defaults.
 */
fun main(args: Array<String>) = runBlocking {
    val relays = args.toList().ifEmpty { listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net") }
    for (relay in relays) probe(relay)
}

private suspend fun probe(relay: String) {
    println("== $relay")
    val url = RelayUrl.parse(relay)
    val publisher = Keys.generate()
    val readers = List(3) { Keys.generate().publicKey() }
    val client = ClientBuilder().connectTimeout(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS)).build()
    client.addRelay(url, null, false, null)
    val started = System.currentTimeMillis()
    val connected = client.tryConnect(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS))
    if (connected.success.isEmpty()) {
        println("connect: failed after ${System.currentTimeMillis() - started} ms: ${connected.failed.values.firstOrNull()}")
        client.shutdown()
        return
    }
    println("connect: ${System.currentTimeMillis() - started} ms")
    try {
        var now = System.currentTimeMillis() / 1000
        send(client, "relay list", RelayList.event(listOf(relay), publisher, now))
        var previous: Long? = null
        for ((index, wait) in listOf(0L, 5L, 1L).withIndex()) {
            delay(wait * 1000)
            now = System.currentTimeMillis() / 1000
            val payload = LocationPayload(fixTime = now, latitude = 60.1699, longitude = 24.9384, accuracy = 10)
            val event = LocationEventBuilder.build(payload, readers, publisher, now, previous)
            if (send(client, "location ${index + 1} (${event.asJson().length} bytes, ${wait}s after the last)", event)) {
                previous = event.createdAt().asSecs().toLong()
            }
        }
        delay(2000)
        val served = fetch(client, url, Filter().kinds(listOf(Kind(ProtocolConstants.LOCATION_KIND))).authors(listOf(publisher.publicKey())).identifier(ProtocolConstants.IDENTIFIER))
        println("served back: ${served.size} location event(s), created_at ${served.map { it.createdAt().asSecs() }}, newest published $previous")
        val lists = fetch(client, url, Filter().kinds(listOf(Kind(ProtocolConstants.RELAY_LIST_KIND))).authors(listOf(publisher.publicKey())))
        println("served back: ${lists.size} relay list(s)")
    } finally {
        client.shutdown()
    }
}

private suspend fun send(client: Client, what: String, event: Event): Boolean {
    val started = System.currentTimeMillis()
    val output = client.sendEvent(
        event, SendEventTarget.broadcast(), AckPolicy.all(),
        Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS), null,
    )
    val took = System.currentTimeMillis() - started
    if (output.success.isNotEmpty()) {
        println("$what: OK true in $took ms")
    } else {
        println("$what: refused in $took ms: ${output.failed.values.firstOrNull()}")
    }
    return output.success.isNotEmpty()
}

private suspend fun fetch(client: Client, url: RelayUrl, filter: Filter): List<Event> = client.fetchEvents(
    ReqTarget.manual(mapOf(url to listOf(filter))),
    Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS), ReqExitPolicy.ExitOnEose, null,
)
