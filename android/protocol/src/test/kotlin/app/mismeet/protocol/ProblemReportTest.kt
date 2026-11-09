package app.mismeet.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.UnwrappedGift

class ProblemReportTest {
    private val reports = Keys.generate()
    // The library takes the wrap's time from the clock, so the tests read at the real time.
    private val now = System.currentTimeMillis() / 1000

    private fun rejection(block: () -> Unit): ReportRejection =
        assertThrows(RejectedReportException::class.java, block).rejection

    @Test
    fun reportsKeyIsTheOneInTheSpec() {
        assertEquals(
            "npub1zvqr2zn45kdr0epvfc600z92sc7fdmhf8p46dz3e6tmwug8fk7fquge3l4",
            PublicKey.parse(ProtocolConstants.REPORTS_PUBLIC_KEY).toBech32(),
        )
        assertEquals(listOf("wss://relay.nostr.net", "wss://relay.primal.net"), ProtocolConstants.REPORT_RELAYS)
    }

    @Test
    fun roundTripsTheTextAsAChatMessage() {
        val wrap = ProblemReport.wrap("Mismeet problem report\nReason: test", reports.publicKey(), now)
        assertEquals(ProtocolConstants.GIFT_WRAP_KIND, wrap.kind().asU16())
        assertEquals(listOf("p"), wrap.tags().map { it.kind() }.filter { it != "expiration" })
        val rumor = UnwrappedGift.fromGiftWrap(reports, wrap).rumor()
        assertEquals(ProtocolConstants.CHAT_MESSAGE_KIND, rumor.kind().asU16())
        assertEquals(listOf(listOf("p", reports.publicKey().toHex())), rumor.tags().map { it.toVec() })
        val report = ProblemReport.read(wrap, reports, now)
        assertEquals("Mismeet problem report\nReason: test", report.text)
        assertEquals(now, report.createdAt)
    }

    @Test
    fun eachReportIsSealedByAKeyOfItsOwn() {
        val first = UnwrappedGift.fromGiftWrap(reports, ProblemReport.wrap("a", reports.publicKey(), now)).sender().toHex()
        val second = UnwrappedGift.fromGiftWrap(reports, ProblemReport.wrap("b", reports.publicKey(), now)).sender().toHex()
        assertNotEquals(first, second)
        assertNotEquals(reports.publicKey().toHex(), first)
    }

    @Test
    fun wrapExpiresAfterThirtyDaysFromItsRandomisedTime() {
        val wrap = ProblemReport.wrap("x", reports.publicKey(), now)
        val expiration = wrap.tags().first { it.kind() == "expiration" }.content()!!.toLong()
        assertEquals(wrap.createdAt().asSecs().toLong() + ProtocolConstants.REPORT_EXPIRATION_SECONDS, expiration)
        assertEquals(ReportRejection.EXPIRED, rejection { ProblemReport.read(wrap, reports, expiration) })
    }

    @Test
    fun rejectsWhatIsNoReportForThisKey() {
        val wrap = ProblemReport.wrap("x", reports.publicKey(), now)
        assertEquals(ReportRejection.UNWRAP_FAILED, rejection { ProblemReport.read(wrap, Keys.generate(), now) })
        val sender = Keys.generate()
        val request = AddBackRequest.wrap(Invite(sender.publicKey(), emptyList()), null, sender, reports.publicKey(), now)
        assertEquals(ReportRejection.WRONG_RUMOR_KIND, rejection { ProblemReport.read(request, reports, now) })
        val location = LocationEventBuilder.buildRevocation(sender, now, null)
        assertEquals(ReportRejection.WRONG_KIND, rejection { ProblemReport.read(location, reports, now) })
    }

    @Test
    fun cutsLongTextAtTheLimit() {
        val text = ProblemReport.clip("x".repeat(ProtocolConstants.REPORT_MAX_LENGTH + 100))
        assertEquals(ProtocolConstants.REPORT_MAX_LENGTH, text.length)
        assertTrue(text.endsWith("\n[cut]"))
        assertEquals("short", ProblemReport.clip("short"))
    }

    @Test
    fun removesWhatCouldTellWhoSentIt() {
        val key = Keys.generate().publicKey()
        val invite = Invite(key, listOf(RelayUrl.parse("wss://relay.example.com")))
        val text = listOf(
            "Invite: ${invite.link()}",
            "Own key ${key.toBech32()} and ${invite.nprofile()}",
            "Event ${key.toHex()}",
            "No relay accepted it: wss://nos.lol: ok, wss://home.example.org/: timeout",
            "Fix at 60.169912, 24.938412 within 12 m",
            "Add-back request to Bea: accepted, Beatrice too; Mia Lee waits",
        ).joinToString("\n")
        val redacted = ProblemReport.redact(text, listOf("Bea", "Mia Lee", " "), listOf("wss://nos.lol", "wss://relay.primal.net"))
        assertEquals(
            listOf(
                "Invite: [invite]",
                "Own key [key] and [key]",
                "Event [key]",
                "No relay accepted it: wss://nos.lol: ok, [relay]: timeout",
                "Fix at [position] within 12 m",
                "Add-back request to [contact]: accepted, Beatrice too; [contact] waits",
            ).joinToString("\n"),
            redacted,
        )
    }
}
