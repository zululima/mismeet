package app.mismeet.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.BatteryManager
import android.util.Log
import app.mismeet.android.checks.ProblemReports
import app.mismeet.android.checks.failureText
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
import app.mismeet.android.store.StoredRequest
import app.mismeet.protocol.AddBackRequest
import app.mismeet.protocol.ContactRelays
import app.mismeet.protocol.Invite
import app.mismeet.protocol.LatLon
import app.mismeet.protocol.LocationEventBuilder
import app.mismeet.protocol.LocationEventReader
import app.mismeet.protocol.LocationFix
import app.mismeet.protocol.LocationReadResult
import app.mismeet.protocol.ProblemReport
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
import kotlinx.coroutines.coroutineScope
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
    private val _shareQuestion = MutableStateFlow<Contact?>(null)

    /** A contact just added, whom the app asks whether to share this phone's location with. */
    val shareQuestion: StateFlow<Contact?> = _shareQuestion.asStateFlow()

    /** Set when a debug launch used the test hooks, so that no question blocks a screenshot run. */
    @Volatile
    var testLaunch = false
    private val english by lazy { context.inEnglish() }
    val keys: Keys = DeviceKeys.loadOrCreate(context)
    val npub: String = keys.publicKey().toBech32()

    private val relays = RelayService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val publishing = Mutex()
    private val refreshing = Mutex()
    private val requesting = Mutex()
    private val reporting = Mutex()
    private val photos = ConcurrentHashMap<String, Pair<Long, Bitmap>>()
    val roads = RoadData(context)
    private val places = PlaceData(context)
    private val _placeNames = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The district, town or village of each contact's last position by contact id, spec section 8.7. */
    val placeNames: StateFlow<Map<String, String>> = _placeNames.asStateFlow()
    val countries by lazy { Countries(context.assets.open("countries.json").bufferedReader().use { it.readText() }) }

    init {
        ProblemReports.catchCrashes(context)
        if (_state.value.sharingEnabled && _state.value.relayListPublishedAt == null) scope.launch { publishRelayList() }
        log("Invite: ${inviteLink()}")
        if (ProblemReports.pendingCrash(context) != null) {
            if (_state.value.reportsEnabled) scope.launch { reportAutomatically("The app crashed") } else ProblemReports.forgetCrash(context)
        }
    }

    fun inviteLink(): String =
        runCatching { ownInvite().link() }.getOrDefault(ProtocolConstants.INVITE_LINK_PREFIX + npub)

    private fun ownInvite() = Invite(keys.publicKey(), _state.value.relays.mapNotNull { runCatching { RelayUrl.parse(it) }.getOrNull() })

    fun setOwnName(name: String) {
        update { it.copy(ownName = name.take(ProtocolConstants.MAX_NAME_LENGTH)) }
    }

    /**
     * Asked once, at the first launch, or at the first launch after this question existed for a
     * user who never gave a name; an empty name is a valid answer.
     */
    fun asksName(state: PersistedState): Boolean = !testLaunch && !state.askedName && state.ownName.isEmpty()

    /** The answer to [asksName]: a name to keep, or null to skip. */
    fun answerName(name: String?) {
        update { state ->
            state.copy(ownName = name?.trim()?.take(ProtocolConstants.MAX_NAME_LENGTH) ?: state.ownName, askedName = true)
        }
    }

    // Lifecycle

    fun becameActive() {
        scope.launch { refresh() }
        if (_state.value.sharingEnabled && LocationProvider.hasForegroundPermission(context)) {
            scope.launch { publishFresh(Trigger.FOREGROUND) }
            LocationService.start(context)
        }
    }

    /**
     * Publishing is opt-in: off, the phone only watches its contacts, publishes nothing and needs
     * no permission. On, it advertises its relays, asks for location and runs the service.
     */
    fun setSharingEnabled(enabled: Boolean) {
        if (_state.value.sharingEnabled == enabled) return
        update { it.copy(sharingEnabled = enabled) }
        if (enabled) {
            scope.launch {
                publishRelayList()
                sendAddBackRequests()
            }
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
            tell { it.getString(R.string.notice_location_needed) }
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

    /**
     * Adds a contact with sharing off and asks, through [shareQuestion], whether to share with it.
     * The debug test hook passes [ask] false, which shares at once instead.
     */
    fun addContact(inviteText: String, name: String, ask: Boolean = true) {
        val invite = runCatching { Invite.parse(inviteText) }
            .getOrElse { throw IllegalArgumentException(context.getString(R.string.invite_not_valid), it) }
        val id = invite.publicKey.toHex()
        require(id != keys.publicKey().toHex()) { context.getString(R.string.error_own_invite) }
        require(_state.value.contacts.none { it.id == id }) { context.getString(R.string.error_duplicate) }
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { context.getString(R.string.error_no_name) }
        val contact = Contact(
            id = id,
            name = trimmed,
            relays = invite.relays.map { it.toString() },
            share = !ask,
            addedAt = System.currentTimeMillis(),
            // Whoever sent a request has added this phone already.
            addBackDone = _state.value.requests.any { it.id == id },
        )
        update { state -> state.copy(contacts = state.contacts + contact, requests = state.requests.filterNot { it.id == id }) }
        if (ask) _shareQuestion.value = contact else if (_state.value.sharingEnabled) recipientsChanged()
        scope.launch { refresh() }
    }

    /**
     * The answer to [shareQuestion]: sharing with the new contact also turns on sharing from this
     * phone when it was off, as the Me screen's switch does.
     */
    fun answerShareQuestion(share: Boolean) {
        val contact = _shareQuestion.value ?: return
        _shareQuestion.value = null
        if (!share) return
        setSharingEnabled(true)
        setShare(contact.id, true)
    }

    /** Hides an add-back request for good; adding the sender from their invite still works. */
    fun dismissRequest(id: String) {
        update { state ->
            state.copy(requests = state.requests.filterNot { it.id == id }, dismissedRequests = (state.dismissedRequests + id).distinct())
        }
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
        }.onFailure { error -> tell(error.message) { it.getString(R.string.notice_picture) } }.getOrNull()
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
            }.onFailure { error -> tell(error.message) { it.getString(R.string.notice_picture) } }
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
            val most = ProtocolConstants.MAX_SHARING_CONTACTS
            tell { it.resources.getQuantityString(R.plurals.notice_max_sharing, most, most) }
            return
        }
        update { s -> s.copy(contacts = s.contacts.map { if (it.id == id) it.copy(share = share) else it }) }
        recipientsChanged()
        if (share) scope.launch { sendAddBackRequests() }
    }

    // Publishing

    suspend fun publishFresh(trigger: Trigger) {
        LocationProvider.currentFix(context)?.let {
            log("Position from ${it.source}, within ${it.fix.horizontalAccuracy.toInt()} m")
            onFix(it.fix, trigger)
        } ?: run {
            log("No fix available")
            shareFailed(ShareFailure.Kind.NO_POSITION)
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
                shareFailed(ShareFailure.Kind.OLD_POSITION)
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
                shareFailed(ShareFailure.Kind.NOT_BUILT)
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
            shareFailed(ShareFailure.Kind.NOT_ACCEPTED, result.summary)
        } else {
            update { it.copy(lastPublishedCreatedAt = event.createdAt().asSecs().toLong(), lastPublishedAt = System.currentTimeMillis()) }
            log("$what: accepted by ${result.succeeded.size} of ${relayUrls.size} relays")
        }
    }

    private fun shareFailed(kind: ShareFailure.Kind, detail: String? = null) {
        val failure = ShareFailure(System.currentTimeMillis(), failureText(english, kind, detail), kind, detail)
        update { it.copy(lastShareFailure = failure) }
        scope.launch { reportAutomatically("A share failed") }
    }

    // Problem reports, spec section 8.8

    fun setReportsEnabled(enabled: Boolean) {
        update { it.copy(reportsEnabled = enabled) }
        if (!enabled) ProblemReports.forgetCrash(context)
    }

    /** The report the user would send now, with a crash still waiting to be reported. */
    suspend fun reportText(relayChecks: List<RelayService.RelayCheck>?): String = withContext(Dispatchers.IO) {
        ProblemReports.text(
            context, this@AppModel, _state.value, relayChecks ?: checkRelays(),
            "Sent from Check setup", automatic = false, crash = ProblemReports.pendingCrash(context),
        )
    }

    /** Sends a report the user has read; true when a relay took it. */
    suspend fun sendReport(text: String): Boolean = reporting.withLock { publishReport(text, automatic = false) }

    private suspend fun reportAutomatically(reason: String) {
        if (reporting.isLocked) return
        reporting.withLock {
            val current = _state.value
            if (!current.reportsEnabled) return
            val last = current.lastReportAt
            if (last != null && System.currentTimeMillis() - last < ProtocolConstants.REPORT_INTERVAL_SECONDS * 1000) return
            val crash = ProblemReports.pendingCrash(context)
            val text = withContext(Dispatchers.IO) {
                ProblemReports.text(
                    context, this@AppModel, current, checkRelays(),
                    if (crash != null) "The app crashed" else reason, automatic = true, crash = crash,
                )
            }
            publishReport(text, automatic = true)
        }
    }

    private suspend fun publishReport(text: String, automatic: Boolean): Boolean {
        val wrap = runCatching { ProblemReport.wrap(text, ProblemReport.reportsKey, System.currentTimeMillis() / 1000) }
            .onFailure { log("Problem report failed: ${it.message}") }
            .getOrNull() ?: return false
        val result = relays.publish(wrap, ProtocolConstants.REPORT_RELAYS)
        if (result.succeeded.isEmpty()) {
            log("Problem report: no relay accepted it, ${result.summary}")
            return false
        }
        if (automatic) update { it.copy(lastReportAt = System.currentTimeMillis()) }
        ProblemReports.forgetCrash(context)
        log("Problem report: accepted by ${result.succeeded.size} of ${ProtocolConstants.REPORT_RELAYS.size} relays")
        return true
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

    /**
     * Spec section 4.5: asks each contact this phone shares with to add it back, once, unless
     * the contact already shares with it. Runs after each refresh, which tells which do.
     */
    private suspend fun sendAddBackRequests() {
        if (requesting.isLocked) return
        requesting.withLock {
            val current = _state.value
            if (!current.sharingEnabled) return
            val invite = ownInvite()
            for (contact in current.contacts.filter { it.share && !it.addBackDone }) {
                if (contact.sharing == Contact.SharingStatus.SHARING) {
                    addBackDone(contact.id)
                    continue
                }
                val receiver = contact.publicKey() ?: continue
                val wrap = runCatching { AddBackRequest.wrap(invite, current.ownName, keys, receiver, System.currentTimeMillis() / 1000) }
                    .onFailure { log("Add-back request to ${contact.name} failed: ${it.message}") }
                    .getOrNull() ?: continue
                val relayUrls = contact.queryRelays(current.relays).map { it.toString() }
                val result = relays.publish(wrap, relayUrls)
                if (result.succeeded.isEmpty()) {
                    log("Add-back request to ${contact.name}: no relay accepted it, ${result.summary}")
                } else {
                    addBackDone(contact.id)
                    log("Add-back request to ${contact.name}: accepted by ${result.succeeded.size} of ${relayUrls.size} relays")
                }
            }
        }
    }

    private fun addBackDone(id: String) {
        update { state -> state.copy(contacts = state.contacts.map { if (it.id == id) it.copy(addBackDone = true) else it }) }
    }

    // Reading

    suspend fun refresh() {
        if (refreshing.isLocked) return
        refreshing.withLock {
            coroutineScope {
                launch { fetchRequests() }
                if (_state.value.contacts.isNotEmpty()) fetchLocations()
            }
        }
        sendAddBackRequests()
    }

    /**
     * Spec section 4.5: the add-back requests addressed to this phone, on connections of their
     * own, so that no relay sees this phone's key beside the authors it asks about.
     */
    private suspend fun fetchRequests() {
        val filter = Filter()
            .kinds(listOf(Kind(ProtocolConstants.GIFT_WRAP_KIND)))
            .pubkey(keys.publicKey())
            .limit(ProtocolConstants.ADD_BACK_FETCH_LIMIT.toULong())
        val targets = _state.value.relays.mapNotNull { runCatching { RelayUrl.parse(it) }.getOrNull() }.associateWith { listOf(filter) }
        if (targets.isEmpty()) return
        val now = System.currentTimeMillis() / 1000
        val fetched = relays.fetch(targets).mapNotNull { runCatching { AddBackRequest.read(it, keys, now) }.getOrNull() }
        update { state ->
            val contacts = state.contacts.map { it.id }.toSet()
            val requests = AddBackRequest.newestPerSender(state.requests.mapNotNull { it.request() } + fetched).filter {
                val id = it.sender.toHex()
                id !in contacts && id !in state.dismissedRequests && it.createdAt > now - ProtocolConstants.ADD_BACK_EXPIRATION_SECONDS
            }
            state.copy(requests = requests.map { StoredRequest.of(it) })
        }
        log("Add-back requests: ${fetched.size} read, ${_state.value.requests.size} waiting")
    }

    private suspend fun fetchLocations() {
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

    /** Shows [text] in the app's language and logs it in English, as problem reports want it. */
    private fun tell(detail: String? = null, text: (Context) -> String) {
        val logged = text(english)
        log(if (detail == null) logged else "$logged: $detail")
        _notices.tryEmit(text(context))
    }

    private fun log(text: String) {
        Log.i("Mismeet", text)
        ProblemReports.record(text)
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
