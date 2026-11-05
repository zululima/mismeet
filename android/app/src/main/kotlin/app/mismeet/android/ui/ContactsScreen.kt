package app.mismeet.android.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.unit.dp
import app.mismeet.android.AppModel
import app.mismeet.android.store.Contact
import app.mismeet.android.store.PersistedState
import app.mismeet.android.store.StoredRequest
import app.mismeet.protocol.LocationPayload
import app.mismeet.protocol.ProtocolConstants
import app.mismeet.protocol.TravelMode
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The contacts in the map's sheet, like Find My's people: the header stays in view, and pulling it up shows the list. */
@Composable
fun ContactList(model: AppModel, onFocus: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val placeNames by model.placeNames.collectAsState()
    // What the Add contact dialog opens with; null while it is closed.
    var adding by remember { mutableStateOf<Adding?>(null) }
    val openedInvite by model.openedInvite.collectAsState()
    LaunchedEffect(openedInvite) {
        openedInvite?.let {
            adding = Adding(it)
            model.inviteTaken()
        }
    }
    val listState = rememberLazyListState()
    // A list keeps its first visible row in place, which would leave a new request above the view.
    LaunchedEffect(state.requests.firstOrNull()?.id) {
        if (state.requests.isNotEmpty()) listState.animateScrollToItem(0)
    }
    var photoFor by remember { mutableStateOf<Contact?>(null) }
    var choosingFor by remember { mutableStateOf<String?>(null) }
    var cropping by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = choosingFor
        choosingFor = null
        if (uri != null && id != null) scope.launch { model.loadPhoto(uri)?.let { cropping = id to it } }
    }
    val choosePhoto = { id: String ->
        choosingFor = id
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Contacts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { model.refreshNow() }) { Text("Refresh") }
            Button(onClick = { adding = Adding("") }) { Text("Add") }
        }
        if (state.contacts.isEmpty() && state.requests.isEmpty()) {
            Text(
                "Add a contact from the invite they sent you.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.requests, key = { "request-${it.id}" }) { request ->
                RequestRow(
                    request = request,
                    onAdd = { adding = Adding(request.invite, request.name.orEmpty(), fromRequest = true) },
                    onDismiss = { model.dismissRequest(request.id) },
                )
            }
            items(state.contacts, key = { it.id }) { contact ->
                ContactRow(
                    contact = contact,
                    place = placeNames[contact.id],
                    distance = distance(contact, state),
                    photo = model.photo(contact),
                    onFocus = { onFocus(contact.id) },
                    onPhoto = { if (contact.photoSetAt == null) choosePhoto(contact.id) else photoFor = contact },
                    onShare = if (state.sharingEnabled) ({ model.setShare(contact.id, it) }) else null,
                    onRemove = { model.removeContact(contact.id) },
                    onOpenMap = { openMap(context, contact) },
                )
            }
        }
    }

    photoFor?.let { contact ->
        AlertDialog(
            onDismissRequest = { photoFor = null },
            title = { Text("Photo of ${contact.name}") },
            text = { Text("The photo stays on this phone and shows on the map in place of the initial.") },
            confirmButton = {
                TextButton(onClick = {
                    photoFor = null
                    choosePhoto(contact.id)
                }) { Text("Choose another") }
            },
            dismissButton = {
                TextButton(onClick = {
                    photoFor = null
                    model.removePhoto(contact.id)
                }) { Text("Remove photo") }
            },
        )
    }

    cropping?.let { (id, bitmap) ->
        PhotoCropDialog(bitmap, onDismiss = { cropping = null }, onChoose = { square ->
            cropping = null
            model.setPhoto(id, square)
        })
    }

    adding?.let { start ->
        AddContactDialog(
            start = start,
            onDismiss = { adding = null },
            onAdd = { invite, name -> model.addContact(invite, name) },
        )
    }
}

/** An Add contact dialog to open: an invite to fill in, or one from an add-back request, which stays hidden. */
private data class Adding(val invite: String, val name: String = "", val fromRequest: Boolean = false)

@Composable
private fun RequestRow(request: StoredRequest, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${request.name ?: "Someone"} added you", style = MaterialTheme.typography.titleMedium)
            val now = System.currentTimeMillis()
            val age = if (now - request.createdAt * 1000 < DateUtils.MINUTE_IN_MILLIS) "just now"
            else DateUtils.getRelativeTimeSpanString(request.createdAt * 1000, now, DateUtils.MINUTE_IN_MILLIS)
            Text("Add them back to see each other. Sent $age.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text("Add back") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    place: String?,
    distance: String?,
    photo: Bitmap?,
    onFocus: () -> Unit,
    onPhoto: () -> Unit,
    onShare: ((Boolean) -> Unit)?,
    onRemove: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clip(CardDefaults.shape).clickable(enabled = contact.lastPayload != null, onClick = onFocus)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact, photo, 48.dp, Modifier.size(48.dp).clickable(onClick = onPhoto))
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                    Text(status(contact, place), style = MaterialTheme.typography.bodySmall)
                }
                if (distance != null) Text(distance, style = MaterialTheme.typography.bodyMedium)
                if (onShare != null) Switch(checked = contact.share, onCheckedChange = onShare)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (contact.lastPayload != null) TextButton(onClick = onOpenMap) { Text("Open in map") }
                TextButton(onClick = onPhoto) { Text("Photo") }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

/** Where and when a contact was last seen, such as "Kumpula, seen 2 minutes ago". */
fun status(contact: Contact, place: String?): String {
    val payload = contact.lastPayload
    if (payload != null) {
        val age = DateUtils.getRelativeTimeSpanString(payload.fixTime * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        val stale = if (contact.sharing == Contact.SharingStatus.NOT_SHARING) ", no longer sharing" else ""
        return if (place != null) "$place, seen $age$stale" else "Seen $age$stale"
    }
    return when (contact.sharing) {
        Contact.SharingStatus.NOT_SHARING -> "Not sharing with you"
        else -> "No location yet"
    }
}

/**
 * How far this phone's last fix is from a contact's, such as "850 m", "1.4 km" or "18 km", while
 * sharing keeps that fix current.
 */
fun distance(contact: Contact, state: PersistedState): String? {
    val payload = contact.lastPayload ?: return null
    val own = state.lastFix?.takeIf { state.sharingEnabled } ?: return null
    val result = FloatArray(1)
    Location.distanceBetween(own.latitude, own.longitude, payload.latitude, payload.longitude, result)
    val rounded = (result[0] / 10).roundToInt() * 10
    if (rounded < 1_000) return "$rounded m"
    val km = result[0] / 1_000
    return if (km < 10) String.format(Locale.getDefault(), "%.1f km", km) else "${km.roundToInt()} km"
}

/** How a contact was moving at the fix, such as "by bike at 18 km/h", or null when it was not. */
fun movement(payload: LocationPayload): String? {
    val speed = payload.speed?.takeIf { it >= ProtocolConstants.ESTIMATE_MIN_SPEED } ?: return null
    val mode = when (TravelMode.of(speed)) {
        TravelMode.FOOT -> "on foot"
        TravelMode.BIKE -> "by bike"
        TravelMode.CAR -> "by car"
    }
    return "$mode at ${(speed * 3.6).roundToInt()} km/h"
}

fun openMap(context: Context, contact: Contact) {
    val payload = contact.lastPayload ?: return
    val point = "${payload.latitude},${payload.longitude}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point(${Uri.encode(contact.name)})"))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No map app installed; the coordinates stay visible in the row.
    }
}

@Composable
private fun AddContactDialog(start: Adding, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var invite by remember(start) { mutableStateOf(start.invite) }
    var name by remember(start) { mutableStateOf(start.name) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.fromRequest) "Add back" else "Add contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!start.fromRequest) InviteField(invite, onChange = { invite = it })
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching { onAdd(invite, name) }
                        .onSuccess { onDismiss() }
                        .onFailure { error = it.message ?: "Not a valid invite" }
                },
                enabled = invite.isNotBlank() && name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InviteField(invite: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onChange)
    }
    OutlinedTextField(
        value = invite,
        onValueChange = onChange,
        label = { Text("Invite") },
        placeholder = { Text("Invite link") },
        minLines = 3,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            onChange(
                context.getSystemService(ClipboardManager::class.java)
                    ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty(),
            )
        }) { Text("Paste") }
        TextButton(onClick = {
            scanner.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Scan the invite")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        }) { Text("Scan QR code") }
    }
}
