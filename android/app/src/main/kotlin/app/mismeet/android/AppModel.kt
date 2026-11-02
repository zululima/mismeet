package app.mismeet.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.BatteryManager
import android.util.Log
import app.mismeet.android.identity.DeviceKeys
import app.mismeet.android.location.Countries
import app.mismeet.android.location.LocationProvider
import app.mismeet.android.location.LocationService
import app.mismeet.android.location.PlaceData
import app.mismeet.android.location.RoadData
import app.mismeet.android.nostr.RelayService
import app.mismeet.android.store.Contact
import app.mismeet.android.store.PersistedState
import app.mismeet.android.store.ShareFailure
import app.mismeet.android.store.StateStore
import app.mismeet.protocol.ContactRelays
import app.mismeet.protocol.Invite
import app.mismeet.protocol.LatLon
import app.mismeet.protocol.LocationEventBuilder
import app.mismeet.protocol.LocationEventReader
import app.mismeet.protocol.LocationFix
import app.mismeet.protocol.LocationReadResult
import app.mismeet.protocol.ProtocolConstants
import app.mismeet.protocol.RelayList
import app.mismeet.protocol.RelayPlan
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.Filter
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.PublicKey
import org.nostrdevkit.sdk.RelayUrl

// The context is the application context, which lives as long as the process.
@SuppressLint("StaticFieldLeak")
class AppModel private constructor(private val context: Context) {
    enum class Trigger { TIMER, FOREGROUND, MANUAL, CONTACTS_CHANGED }

    private val store = StateStore(context)
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<PersistedState> = _state.asStateFlow()
    private val _openedInvite = MutableStateFlow<String?>(null)

    /** An invite link the app was opened with, until the Add contact dialog takes it. */
    val openedInvite: StateFlow<String?> = _openedInvite.asStateFlow()
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Why an action the user took did not happen, each shown once in a snackbar. */
    val notices: SharedFlow<String> = _notices.asSharedFlow()
    val keys: Keys = DeviceKeys.loadOrCreate(context)
    val npub: String = keys.publicKey().toBech32()

    private val relays = RelayService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val publishing = Mutex()
    private val refreshing = Mutex()
    private val photos = ConcurrentHashMap<String, Pair<Long, Bitmap>>()
    val roads = RoadData(context)
    private val places = PlaceData(context)
    private val _placeNames = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The district, town or village of each contact's last position by contact id, spec section 8.7. */
    val placeNames: StateFlow<Map<String, String>> = _placeNames.asStateFlow()
    val countries by lazy { Countries(context.assets.open("countries.json").bufferedReader().use { it.readText() }) }

    init {
        if (_state.value.sharingEnabled && _state.value.relayListPublishedAt == null) scope.launch { publishRelayList() }
        log("Invite: ${inviteLink()}")
    }

    fun inviteLink(): String {
        val urls = _state.value.relays.mapNotNull { runCatching { RelayUrl.parse(it) }.getOrNull() }
        return runCatching { Invite(keys.publicKey(), urls).link() }.getOrDefault(ProtocolConstants.INVITE_LINK_PREFIX + npub)
    }

    // Lifecycle

    fun becameActive() {
        scope.launch { refresh() }
        if (_state.value.sharingEnabled && LocationProvider.hasForegroundPermission(context)) {
            scope.launch { publishFresh(Trigger.FOREGROUND) }
            LocationService.start(context)
        }
    }

    fun refreshNow() {
        scope.launch { refresh() }
    }

    /**
     * Publishing is opt-in: off, the phone only watches its contacts, publishes nothing and needs
     * no permission. On, it advertises its relays, asks for location and runs the service.
     */
    fun setSharingEnabled(enabled: Boolean) {
        if (_state.value.sharingEnabled == enabled) return
        update { it.copy(sharingEnabled = enabled) }
        if (enabled) {
            scope.launch { publishRelayList() }
            if (LocationProvider.hasForegroundPermission(context)) LocationService.start(context)
        } else {
            LocationService.stop(context)
            recipientsChanged()
        }
    }

    fun permissionsChanged() {
        if (_state.value.sharingEnabled && LocationProvider.hasForegroundPermission(context)) LocationService.start(context)
    }

    fun publishNow() {
        if (!_state.value.sharingEnabled) return
        if (!LocationProvider.hasForegroundPermission(context)) {
            tell("Location permission is needed to share your location")
            return
        }
        scope.launch { publishFresh(Trigger.MANUAL) }
    }

    // Contacts

    fun openInvite(link: String) {
        _openedInvite.value = link
    }

    fun inviteTaken() {
        _openedInvite.value = null
    }

    fun addContact(inviteText: String, name: String) {
        val invite = Invite.parse(inviteText)
        val id = invite.publicKey.toHex()
        require(id != keys.publicKey().toHex()) { "That is your own invite." }
        require(_state.value.contacts.none { it.id == id }) { "That contact is already in the list." }
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Give the contact a name." }
        val contact = Contact(
            id = id,
            name = trimmed,
            relays = invite.relays.map { it.toString() },
            addedAt = System.currentTimeMillis(),
        )
        update { it.copy(contacts = it.contacts + contact) }
        recipientsChanged()
        scope.launch { refresh() }
    }

    fun removeContact(id: String) {
        update { it.copy(contacts = it.contacts.filterNot { contact -> contact.id == id }) }
        photoFile(id).delete()
        recipientsChanged()
    }

    /** A picked picture, decoded just large enough to crop on screen, or null when it cannot be read. */
    suspend fun loadPhoto(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.setTargetSampleSize(max(1, min(info.size.width, info.size.height) / CROP_SOURCE_SIZE))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.onFailure { tell("Could not use that picture", it.message) }.getOrNull()
    }

    /** A square picture for a contact, kept on this phone only; it is never published. */
    fun setPhoto(id: String, square: Bitmap) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val file = photoFile(id).apply { parentFile?.mkdirs() }
                file.outputStream().use { Bitmap.createScaledBitmap(square, PHOTO_SIZE, PHOTO_SIZE, true).compress(Bitmap.CompressFormat.JPEG, 85, it) }
            }.onSuccess {
                val now = System.currentTimeMillis()
                update { state -> state.copy(contacts = state.contacts.map { if (it.id == id) it.copy(photoSetAt = now) else it }) }
            }.onFailure { tell("Could not use that picture", it.message) }
        }
    }

    fun removePhoto(id: String) {
        photoFile(id).delete()
        update { state -> state.copy(contacts = state.contacts.map { if (it.id == id) it.copy(photoSetAt = null) else it }) }
    }

    fun photo(contact: Contact): Bitmap? {
        val setAt = contact.photoSetAt ?: return null
        photos[contact.id]?.takeIf { it.first == setAt }?.let { return it.second }
        return BitmapFactory.decodeFile(photoFile(contact.id).path)?.also { photos[contact.id] = setAt to it }
    }

    private fun photoFile(id: String) = File(File(context.filesDir, "photos"), "$id.jpg")

    fun setShare(id: String, share: Boolean) {
        val current = _state.value
        val contact = current.contacts.firstOrNull { it.id == id } ?: return
        if (contact.share == share) return
        if (share && current.contacts.count { it.share } >= ProtocolConstants.MAX_SHARING_CONTACTS) {
            tell("Cannot share with more than ${ProtocolConstants.MAX_SHARING_CONTACTS} contacts")
            return
        }
        update { s -> s.copy(contacts = s.contacts.map { if (it.id == id) it.copy(share = share) else it }) }
        recipientsChanged()
    }

    // Publishing

    private suspend fun publishFresh(trigger: Trigger) {
        LocationProvider.currentFix(context)?.let { onFix(it, trigger) } ?: run {
            log("No fix available")
            shareFailed("Could not get a position")
        }
    }

    suspend fun onFix(fix: LocationFix, trigger: Trigger) {
        update { it.copy(lastFix = fix) }
        publishing.withLock {
            val current = _state.value
            val now = System.currentTimeMillis()
            if ((trigger == Trigger.TIMER || trigger == Trigger.FOREGROUND) &&
                current.lastPublishedAt != null &&
                now - current.lastPublishedAt < ProtocolConstants.PUBLISH_THROTTLE_SECONDS * 1000
            ) {
                log("Skipped publish: published less than a minute ago")
                return
            }
            if (now - fix.timeMillis > ProtocolConstants.MAX_FIX_AGE_SECONDS * 1000) {
                log("Skipped publish: the fix is older than an hour")
                shareFailed("The newest position was over an hour old")
                return
            }
            val recipients = sharingRecipients()
            if (recipients.isEmpty()) {
                log("Skipped publish: nobody to share with")
                return
            }
            runCatching {
                LocationEventBuilder.build(fix.payload(batteryLevel()), recipients, keys, now / 1000, current.lastPublishedCreatedAt)
            }.onSuccess { send(it, "Location") }.onFailure {
                log("Publish failed: ${it.message}")
                shareFailed("Could not build the event")
            }
        }
    }

    /** Spec section 8.3: after a contact or share change, replace the event on the relays at once. */
    private fun recipientsChanged() {
        scope.launch {
            val needsFreshFix = publishing.withLock {
                val current = _state.value
                val recipients = if (current.sharingEnabled) sharingRecipients() else emptyList()
                if (current.lastPublishedCreatedAt == null) return@withLock recipients.isNotEmpty()
                val now = System.currentTimeMillis()
                val fix = current.lastFix
                runCatching {
                    if (fix != null && now - fix.timeMillis <= ProtocolConstants.MAX_FIX_AGE_SECONDS * 1000 && recipients.isNotEmpty()) {
                        send(LocationEventBuilder.build(fix.payload(batteryLevel()), recipients, keys, now / 1000, current.lastPublishedCreatedAt), "Location")
                        false
                    } else {
                        send(LocationEventBuilder.buildRevocation(keys, now / 1000, current.lastPublishedCreatedAt), if (recipients.isEmpty()) "Revocation" else "Placeholder")
                        recipients.isNotEmpty()
                    }
                }.getOrElse {
                    log("Republish failed: ${it.message}")
                    false
                }
            }
            if (needsFreshFix && _state.value.sharingEnabled && LocationProvider.hasForegroundPermission(context)) {
                publishFresh(Trigger.CONTACTS_CHANGED)
            }
        }
    }

    private suspend fun publishRelayList() {
        val current = _state.value
        runCatching { RelayList.event(current.relays, keys, System.currentTimeMillis() / 1000) }
            .onFailure { log("Relay list failed: ${it.message}") }
            .onSuccess { event ->
                val result = relays.publish(event, current.relays)
                if (result.succeeded.isEmpty()) {
                    log("Relay list: no relay accepted it, ${result.summary}")
                } else {
                    update { it.copy(relayListPublishedAt = System.currentTimeMillis()) }
                    log("Relay list: accepted by ${result.succeeded.size} of ${current.relays.size} relays")
                }
            }
    }

    private suspend fun send(event: Event, what: String) {
        val relayUrls = _state.value.relays
        val result = relays.publish(event, relayUrls)
        if (result.succeeded.isEmpty()) {
            log("$what: no relay accepted it, ${result.summary}")
            shareFailed("No relay accepted it: ${result.summary}")
        } else {
            update { it.copy(lastPublishedCreatedAt = event.createdAt().asSecs().toLong(), lastPublishedAt = System.currentTimeMillis()) }
            log("$what: accepted by ${result.succeeded.size} of ${relayUrls.size} relays")
        }
    }

    private fun shareFailed(reason: String) {
        update { it.copy(lastShareFailure = ShareFailure(System.currentTimeMillis(), reason)) }
    }

    /** Whether each of this phone's relays answers, and the location event of this phone it holds. */
    suspend fun checkRelays(): List<RelayService.RelayCheck> = relays.check(_state.value.relays, keys.publicKey())

    private fun sharingRecipients(): List<PublicKey> =
        if (_state.value.sharingEnabled) _state.value.contacts.filter { it.share }.mapNotNull { it.publicKey() } else emptyList()

    private fun batteryLevel(): Double? {
        val percent = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: return null
        return if (percent in 0..100) percent / 100.0 else null
    }

    // Reading

    suspend fun refresh() {
        if (_state.value.contacts.isEmpty() || refreshing.isLocked) return
        refreshing.withLock {
            val current = _state.value
            val now = System.currentTimeMillis()
            val due = current.contacts.filter { contact ->
                contact.relayListCheckedAt?.let { now - it > ProtocolConstants.RELAY_LIST_REFRESH_SECONDS * 1000 } ?: true
            }.map { it.id }.toSet()
            val plan = RelayPlan.authorsByRelay(current.contacts.mapNotNull { contact ->
                contact.publicKey()?.let { ContactRelays(it, contact.queryRelays(current.relays)) }
            })
            val targets = plan.mapValues { (_, authors) ->
                val filters = mutableListOf(
                    Filter()
                        .kinds(listOf(Kind(ProtocolConstants.LOCATION_KIND)))
                        .authors(authors)
                        .identifier(ProtocolConstants.IDENTIFIER),
                )
                val dueAuthors = authors.filter { it.toHex() in due }
                if (dueAuthors.isNotEmpty()) {
                    filters += Filter().kinds(listOf(Kind(ProtocolConstants.RELAY_LIST_KIND))).authors(dueAuthors)
                }
                filters.toList()
            }
            val events = relays.fetch(targets)
            val nowSeconds = now / 1000
            var decoded = 0
            var unchanged = 0
            val fetched = current.contacts.map { contact ->
                val publicKey = contact.publicKey() ?: return@map contact
                val own = events.filter { it.author().toHex() == contact.id }
                var updated = contact
                if (contact.id in due) {
                    val list = own.filter { it.kind().asU16() == ProtocolConstants.RELAY_LIST_KIND }
                        .maxByOrNull { it.createdAt().asSecs() }
                    val urls = list?.let { RelayList.writeRelays(it) }.orEmpty()
                    updated = updated.copy(relays = urls.ifEmpty { contact.relays }, relayListCheckedAt = now)
                }
                val locations = own.filter { it.kind().asU16() == ProtocolConstants.LOCATION_KIND }
                val newest = LocationEventReader.newest(locations, nowSeconds, contact.lastCreatedAt)
                if (newest == null) {
                    if (locations.isNotEmpty()) unchanged++
                    return@map updated
                }
                runCatching { LocationEventReader.read(newest, publicKey, keys, nowSeconds) }
                    .onFailure { log("Ignored an event from ${contact.name}: ${it.message}") }
                    .getOrNull()
                    ?.let { result ->
                        updated = when (result) {
                            is LocationReadResult.Shared -> {
                                decoded++
                                updated.copy(lastPayload = result.payload, lastSeenAt = now, sharing = Contact.SharingStatus.SHARING)
                            }
                            LocationReadResult.NotSharing -> updated.copy(sharing = Contact.SharingStatus.NOT_SHARING)
                        }.copy(lastCreatedAt = newest.createdAt().asSecs().toLong(), lastEventId = newest.id().toHex())
                    }
                updated
            }.associateBy { it.id }
            // Contacts added, removed, renamed or toggled during the fetch stay as they are now;
            // only the fields read from the relays come from the fetch.
            update { state ->
                state.copy(contacts = state.contacts.map { live ->
                    fetched[live.id]?.let {
                        live.copy(
                            relays = it.relays,
                            relayListCheckedAt = it.relayListCheckedAt,
                            sharing = it.sharing,
                            lastPayload = it.lastPayload,
                            lastSeenAt = it.lastSeenAt,
                            lastCreatedAt = it.lastCreatedAt,
                            lastEventId = it.lastEventId,
                        )
                    } ?: live
                })
            }
            log("Refreshed from ${targets.size} relays: $decoded new, $unchanged unchanged since last time, ${current.contacts.size - decoded - unchanged} without an event")
        }
    }

    suspend fun updatePlaceNames() {
        _state.value.contacts.forEach { contact ->
            val payload = contact.lastPayload ?: return@forEach
            val name = places.name(LatLon(payload.latitude, payload.longitude), System.currentTimeMillis() / 1000)
            _placeNames.update { if (name == null) it - contact.id else it + (contact.id to name) }
        }
    }

    // Helpers

    // Refreshes, publishes and the UI update from different threads; one at a time keeps the
    // file in step with the newest state and the temporary file to one writer.
    private fun update(transform: (PersistedState) -> PersistedState) = synchronized(store) {
        _state.update(transform)
        store.save(_state.value)
    }

    private fun tell(text: String, detail: String? = null) {
        log(if (detail == null) text else "$text: $detail")
        _notices.tryEmit(text)
    }

    private fun log(text: String) {
        Log.i("Mismeet", text)
    }

    companion object {
        @Volatile
        private var instance: AppModel? = null

        fun get(context: Context): AppModel = instance ?: synchronized(this) {
            instance ?: AppModel(context.applicationContext).also { instance = it }
        }

        private const val PHOTO_SIZE = 256
        private const val CROP_SOURCE_SIZE = 1024
    }
}
