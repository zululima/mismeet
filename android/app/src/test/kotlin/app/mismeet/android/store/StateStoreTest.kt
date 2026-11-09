package app.mismeet.android.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StateStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(text: String?): Pair<StateStore, File> {
        val file = File(folder.root, "state.json")
        if (text != null) file.writeText(text)
        return StateStore(file) to file
    }

    @Test
    fun loadsTheStateFileThat010Writes() {
        val state = store(STATE_0_1_0).first.load()
        val contact = state.contacts.single()
        assertEquals("Aino", contact.name)
        assertEquals(listOf("wss://nos.lol"), contact.relays)
        assertFalse(contact.share)
        assertEquals(Contact.SharingStatus.SHARING, contact.sharing)
        assertEquals(60.1699, contact.lastPayload!!.latitude, 0.0)
        assertEquals(0.5, contact.lastPayload!!.battery!!, 0.0)
        assertEquals(1_761_379_120L, contact.lastCreatedAt)
        assertEquals(1_761_379_300_000L, contact.photoSetAt)
        assertEquals(PersistedState.DEFAULT_RELAYS, state.relays)
        assertEquals(8.0, state.lastFix!!.horizontalAccuracy, 0.0)
        assertNull(state.lastFix!!.speed)
        assertTrue(state.sharingEnabled)
        assertFalse(state.askedName)
        assertEquals("", state.ownName)
    }

    @Test
    fun fillsInFieldsAnOlderFileLacks() {
        val contact = store("""{"contacts":[{"id":"$ID","name":"Eero","relays":[],"addedAt":1}]}""").first.load().contacts.single()
        assertTrue(contact.share)
        assertEquals(Contact.SharingStatus.UNKNOWN, contact.sharing)
        assertNull(contact.photoSetAt)
    }

    @Test
    fun keepsTheNameQuestionAnswered() {
        val (store, _) = store(null)
        store.save(PersistedState(ownName = "Aino", askedName = true))
        val state = store.load()
        assertTrue(state.askedName)
        assertEquals("Aino", state.ownName)
    }

    @Test
    fun readsAShareFailureStoredWithoutItsKind() {
        val failure = store("""{"lastShareFailure":{"at":1,"reason":"Could not get a position"}}""").first.load().lastShareFailure!!
        assertEquals("Could not get a position", failure.reason)
        assertNull(failure.kind)
        assertNull(failure.detail)
    }

    @Test
    fun movesAnUndecodableFileAsideBeforeStartingEmpty() {
        val text = """{"contacts":"not a list"}"""
        val (store, file) = store(text)
        assertTrue(store.load().contacts.isEmpty())
        store.save(PersistedState(sharingEnabled = true))
        val aside = folder.root.listFiles { f -> f.name.startsWith("state-unreadable-") }!!.single()
        assertEquals(text, aside.readText())
        assertTrue(file.readText().contains("\"sharingEnabled\":true"))
    }

    private companion object {
        const val ID = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"

        // state.json as version 0.1.0 writes it. Keep it as it is: later versions must still read it.
        val STATE_0_1_0 = """
            {"contacts":[{"id":"$ID","name":"Aino","relays":["wss://nos.lol"],"share":false,"addedAt":1761379000000,
            "sharing":"SHARING","lastPayload":{"fixTime":1761379100,"latitude":60.1699,"longitude":24.9384,"accuracy":12,
            "altitude":21,"speed":1.4,"course":0,"battery":0.5},"lastCreatedAt":1761379120,
            "lastEventId":"5c83da77af1dec6d7289834998ad7aafbd9e2191396d75ec3cc27f5a77226f36","lastSeenAt":1761379200000,
            "relayListCheckedAt":1761379200000,"photoSetAt":1761379300000}],
            "relays":["wss://relay.damus.io","wss://nos.lol","wss://relay.primal.net"],"lastPublishedCreatedAt":1761379000,
            "lastPublishedAt":1761379000000,"lastFix":{"timeMillis":1761378990000,"latitude":60.1712,"longitude":24.9414,
            "horizontalAccuracy":8.0,"ellipsoidalAltitude":null,"speed":null,"course":null},
            "relayListPublishedAt":1761370000000,"sharingEnabled":true}
        """.trimIndent().replace("\n", "")
    }
}
