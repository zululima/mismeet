package app.mismeet.protocol

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import org.nostrdevkit.sdk.ClientBuilder
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.Filter
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.ReqExitPolicy
import org.nostrdevkit.sdk.ReqTarget

/**
 * Reads the problem reports of spec section 8.8 with the reports key and saves each one under
 * reports/ at the repository root, since relays drop them after 30 days. With the argument new
 * it makes the key instead. The git-ignored android/.reports-key names the file that holds the
 * key's secret. Run through the reports Gradle task.
 */
fun main(args: Array<String>) = runBlocking {
    val keyPath = System.getProperty("mismeet.reportsKey").orEmpty().replaceFirst(Regex("^~"), System.getProperty("user.home"))
    if (keyPath.isBlank()) fail("android/.reports-key is missing or empty; it names the file with the reports key")
    val keyFile = File(keyPath)

    if (args.firstOrNull() == "new") {
        if (keyFile.exists()) fail("$keyFile exists already; a new key would leave its reports unreadable")
        val keys = Keys.generate()
        keyFile.parentFile?.mkdirs()
        keyFile.writeText(keys.secretKey().toBech32() + "\n")
        Files.setPosixFilePermissions(keyFile.toPath(), PosixFilePermissions.fromString("rw-------"))
        println("Wrote the secret to $keyFile")
        println("npub ${keys.publicKey().toBech32()}")
        println("hex  ${keys.publicKey().toHex()}")
        return@runBlocking
    }

    if (!keyFile.exists()) fail("$keyFile does not exist; make the key with -PreportsArgs=new")
    val keys = Keys.parse(keyFile.readText().trim())
    val now = System.currentTimeMillis() / 1000
    val wraps = mutableMapOf<String, Event>()
    for (url in ProtocolConstants.REPORT_RELAYS) {
        val filter = Filter().kinds(listOf(Kind(ProtocolConstants.GIFT_WRAP_KIND))).pubkey(keys.publicKey()).limit(500u)
        val client = ClientBuilder().connectTimeout(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS)).build()
        try {
            client.addRelay(RelayUrl.parse(url), null, false, null)
            client.tryConnect(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS))
            val events = client.fetchEvents(
                ReqTarget.manual(mapOf(RelayUrl.parse(url) to listOf(filter))),
                Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS), ReqExitPolicy.ExitOnEose, null,
            )
            System.err.println("$url: ${events.size} wraps")
            events.forEach { wraps[it.id().toHex()] = it }
        } catch (e: Exception) {
            System.err.println("$url: ${e.message}")
        } finally {
            client.shutdown()
        }
    }

    val directory = File(System.getProperty("mismeet.reportsDir") ?: "reports").apply { mkdirs() }
    val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    val shown = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
    val reports = wraps.mapNotNull { (id, wrap) ->
        runCatching { id to ProblemReport.read(wrap, keys, now) }
            .onFailure { System.err.println("Skipped $id: ${it.message}") }
            .getOrNull()
    }.sortedBy { it.second.createdAt }
    for ((id, report) in reports) {
        val at = Instant.ofEpochSecond(report.createdAt)
        val file = File(directory, "${stamp.format(at)}-${id.take(8)}.txt")
        val isNew = !file.exists()
        if (isNew) file.writeText(report.text + "\n")
        println("== ${shown.format(at)}${if (isNew) ", new" else ""}: ${file.path}")
        println(report.text)
        println()
    }
    println("${reports.size} reports on the relays, saved under $directory")
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
