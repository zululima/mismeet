package app.mismeet.protocol

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.Timestamp
import org.nostrdevkit.sdk.nip59MakeGiftWrap

class AddBackRequestTest {
    private val sender = Keys.generate()
    private val receiver = Keys.generate()
    private val invite = Invite(sender.publicKey(), listOf(RelayUrl.parse("wss://relay.example.com")))
    // The library takes the wrap's time from the clock, so the tests read at the real time.
    private val now = System.currentTimeMillis() / 1000

    private fun rejection(block: () -> Unit): AddBackRejection =
        assertThrows(RejectedRequestException::class.java, block).rejection

    @Test
    fun roundTripsTheInviteAndName() {
        val wrap = AddBackRequest.wrap(invite, " Akira ", sender, receiver.publicKey(), now)
        assertEquals(ProtocolConstants.GIFT_WRAP_KIND, wrap.kind().asU16())
        assertTrue("the wrap is signed by a throwaway key", wrap.author().toHex() != sender.publicKey().toHex())
        assertEquals(listOf("p"), wrap.tags().map { it.kind() }.filter { it != "expiration" })
        val request = AddBackRequest.read(wrap, receiver, now)
        assertEquals(sender.publicKey().toHex(), request.sender.toHex())
        assertEquals(listOf("wss://relay.example.com"), request.invite.relays.map { it.toString() })
        assertEquals("Akira", request.name)
        assertEquals(now, request.createdAt)
    }

    @Test
    fun contentIsCompactWithKeysInOrderAndNoBlankName() {
        assertEquals("{\"v\":1,\"invite\":\"${invite.nprofile()}\",\"name\":\"Bea\"}", AddBackRequest.content(invite, "Bea"))
        assertEquals("{\"v\":1,\"invite\":\"${invite.nprofile()}\"}", AddBackRequest.content(invite, "  "))
        val long = "x".repeat(ProtocolConstants.MAX_NAME_LENGTH + 10)
        assertEquals(ProtocolConstants.MAX_NAME_LENGTH, AddBackRequest.cleanName(long)?.length)
        assertNull(AddBackRequest.cleanName(null))
    }

    @Test
    fun wrapExpiresAfterThirtyDaysFromItsRandomisedTime() {
        val wrap = AddBackRequest.wrap(invite, null, sender, receiver.publicKey(), now)
        val expiration = wrap.tags().first { it.kind() == "expiration" }.content()!!.toLong()
        assertEquals(wrap.createdAt().asSecs().toLong() + ProtocolConstants.ADD_BACK_EXPIRATION_SECONDS, expiration)
        assertTrue(expiration > now)
        assertEquals(AddBackRejection.EXPIRED, rejection { AddBackRequest.read(wrap, receiver, expiration) })
    }

    @Test
    fun onlyTheReceiverCanReadIt() {
        val wrap = AddBackRequest.wrap(invite, "Akira", sender, receiver.publicKey(), now)
        assertEquals(AddBackRejection.UNWRAP_FAILED, rejection { AddBackRequest.read(wrap, Keys.generate(), now) })
    }

    @Test
    fun rejectsAnInviteForAnotherKey() {
        val other = Invite(Keys.generate().publicKey(), emptyList())
        val wrap = AddBackRequest.wrap(other, "Akira", sender, receiver.publicKey(), now)
        assertEquals(AddBackRejection.KEY_MISMATCH, rejection { AddBackRequest.read(wrap, receiver, now) })
    }

    @Test
    fun rejectsItsOwnRequest() {
        val wrap = AddBackRequest.wrap(invite, "Akira", sender, sender.publicKey(), now)
        assertEquals(AddBackRejection.OWN_KEY, rejection { AddBackRequest.read(wrap, sender, now) })
    }

    @Test
    fun rejectsOtherRumorsAndVersions() {
        fun wrapped(kind: UShort, content: String) = nip59MakeGiftWrap(
            sender,
            receiver.publicKey(),
            EventBuilder(Kind(kind), content).customCreatedAt(Timestamp.fromSecs(now.toULong())).finalizeUnsigned(sender.publicKey()),
            Duration.ofSeconds(ProtocolConstants.ADD_BACK_EXPIRATION_SECONDS),
            emptyList(),
        )
        assertEquals(AddBackRejection.WRONG_RUMOR_KIND, rejection { AddBackRequest.read(wrapped(14u, "hello"), receiver, now) })
        val nprofile = invite.nprofile()
        assertEquals(
            AddBackRejection.UNSUPPORTED_VERSION,
            rejection { AddBackRequest.read(wrapped(ProtocolConstants.ADD_BACK_KIND, "{\"v\":2,\"invite\":\"$nprofile\"}"), receiver, now) },
        )
        assertEquals(
            AddBackRejection.MALFORMED_CONTENT,
            rejection { AddBackRequest.read(wrapped(ProtocolConstants.ADD_BACK_KIND, "{\"v\":1}"), receiver, now) },
        )
        assertEquals(
            AddBackRejection.MALFORMED_CONTENT,
            rejection { AddBackRequest.read(wrapped(ProtocolConstants.ADD_BACK_KIND, "not json"), receiver, now) },
        )
        val extra = AddBackRequest.read(
            wrapped(ProtocolConstants.ADD_BACK_KIND, "{\"v\":1,\"invite\":\"$nprofile\",\"photo\":\"later\"}"),
            receiver,
            now,
        )
        assertNull(extra.name)
    }

    @Test
    fun rejectsOtherEventKinds() {
        val location = RelayList.event(listOf("wss://relay.example.com"), sender, now)
        assertEquals(AddBackRejection.WRONG_KIND, rejection { AddBackRequest.read(location, receiver, now) })
    }

    @Test
    fun keepsTheNewestRequestOfEachSender() {
        val other = Invite(Keys.generate().publicKey(), emptyList())
        val requests = listOf(
            AddBackRequest(invite, "old", 10),
            AddBackRequest(other, "other", 15),
            AddBackRequest(invite, "new", 20),
        )
        assertEquals(listOf("new", "other"), AddBackRequest.newestPerSender(requests).map { it.name })
    }
}
