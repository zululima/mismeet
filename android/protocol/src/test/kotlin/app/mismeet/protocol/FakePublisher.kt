package app.mismeet.protocol

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage
import java.io.File
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import org.nostrdevkit.sdk.AckPolicy
import org.nostrdevkit.sdk.ClientBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.SendEventTarget

/**
 * Publishes one location for one watching phone from a throwaway key that persists under
 * build/, so the phone adds the publisher once and later runs update the same contact.
 * MISMEET_FAKE_NAME in the environment selects another key, for a second fake contact, and a
 * reader of - publishes nothing but still prints the publisher's invite and QR code.
 * MISMEET_FAKE_SPEED (m/s), MISMEET_FAKE_HEADING (degrees) and MISMEET_FAKE_AGE (seconds the
 * fix is backdated) make a moving contact, which the watching app estimates along the roads.
 * Each reader also gets one add-back request, named after the key, the first time it is named.
 * Run through the fakePublish Gradle task.
 */
fun main(args: Array<String>) = runBlocking {
    if (args.size < 3) {
        System.err.println("usage: fakePublish <reader invite[,reader invite...] or -> <lat> <lon> [relay ...]")
        exitProcess(2)
    }
    val readerInvites = if (args[0] == "-") emptyList() else args[0].split(",").map { Invite.parse(it) }
    val readers = readerInvites.map { it.publicKey }
    val latitude = args[1].toDouble()
    val longitude = args[2].toDouble()
    val relays = if (args.size > 3) args.drop(3) else listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")

    val name = System.getenv("MISMEET_FAKE_NAME")?.takeIf { it.isNotBlank() } ?: "fake-publisher"
    val keyFile = File("build/$name.key")
    val keys = if (keyFile.exists()) {
        Keys.parse(keyFile.readText().trim())
    } else {
        Keys.generate().also {
            keyFile.parentFile.mkdirs()
            keyFile.writeText(it.secretKey().toHex())
        }
    }
    val createdAtFile = File("build/$name.created-at")
    val previous = createdAtFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
    val now = System.currentTimeMillis() / 1000
    val payload = LocationPayload(
        fixTime = now - (System.getenv("MISMEET_FAKE_AGE")?.toLongOrNull() ?: 0),
        latitude = latitude,
        longitude = longitude,
        accuracy = 15,
        speed = System.getenv("MISMEET_FAKE_SPEED")?.toDoubleOrNull(),
        course = System.getenv("MISMEET_FAKE_HEADING")?.toIntOrNull(),
        battery = 0.8,
    )
    val location = LocationEventBuilder.build(payload, readers, keys, now, previous)
    val relayList = RelayList.event(relays, keys, now)
    val events = if (readers.isEmpty()) emptyList() else listOf(relayList, location)

    val client = ClientBuilder().connectTimeout(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS)).build()
    relays.forEach { client.addRelay(RelayUrl.parse(it), null, false, null) }
    client.tryConnect(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS))
    try {
        for (event in events) {
            val output = client.sendEvent(event, SendEventTarget.broadcast(), AckPolicy.all(), Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS), null)
            println("kind ${event.kind().asU16()}: accepted by ${output.success.size} relays, rejected by ${output.failed}")
            if (event.kind().asU16() == ProtocolConstants.LOCATION_KIND && output.success.isNotEmpty()) {
                createdAtFile.writeText(event.createdAt().asSecs().toString())
            }
        }
    } finally {
        client.shutdown()
    }

    val ownInvite = Invite(keys.publicKey(), relays.map { RelayUrl.parse(it) })
    val requestedFile = File("build/$name.requested")
    val requested = requestedFile.takeIf { it.exists() }?.readLines()?.toMutableSet() ?: mutableSetOf()
    val displayName = name.replace('-', ' ').replaceFirstChar { it.uppercase() }
    for (reader in readerInvites.filter { it.publicKey.toHex() !in requested }) {
        val wrap = AddBackRequest.wrap(ownInvite, displayName, keys, reader.publicKey, now)
        val targets = reader.relays.map { it.toString() }.ifEmpty { relays }
        val sender = ClientBuilder().connectTimeout(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS)).build()
        targets.forEach { sender.addRelay(RelayUrl.parse(it), null, false, null) }
        sender.tryConnect(Duration.ofSeconds(ProtocolConstants.CONNECT_TIMEOUT_SECONDS))
        try {
            val output = sender.sendEvent(wrap, SendEventTarget.broadcast(), AckPolicy.all(), Duration.ofSeconds(ProtocolConstants.OK_TIMEOUT_SECONDS), null)
            println("add-back request as \"$displayName\": accepted by ${output.success.size} relays, rejected by ${output.failed}")
            if (output.success.isNotEmpty()) requested += reader.publicKey.toHex()
        } finally {
            sender.shutdown()
        }
    }
    requestedFile.writeText(requested.joinToString("\n"))

    val invite = ownInvite.link()
    println()
    println(
        if (readers.isEmpty()) "Published nothing. Add this publisher on the watching phone:"
        else "Published $latitude, $longitude for ${readers.size} reader(s). Add this publisher on the watching phone:",
    )
    println(invite)
    println()
    printQr(invite)
    val png = File("build/$name-invite.png").absoluteFile
    writeQrPng(invite, png)
    println()
    println("QR code to scan from the screen: $png")
}

private fun writeQrPng(text: String, file: File, scale: Int = 12) {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 4))
    val image = BufferedImage(matrix.width * scale, matrix.height * scale, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until image.height) {
        for (x in 0 until image.width) image.setRGB(x, y, if (matrix[x / scale, y / scale]) 0x000000 else 0xFFFFFF)
    }
    ImageIO.write(image, "png", file)
}

private fun printQr(text: String) {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2))
    for (y in 0 until matrix.height) {
        val row = StringBuilder()
        for (x in 0 until matrix.width) row.append(if (matrix[x, y]) "██" else "  ")
        println(row)
    }
}
