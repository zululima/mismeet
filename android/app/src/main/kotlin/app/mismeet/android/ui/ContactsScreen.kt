package app.mismeet.android.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.icu.util.MeasureUnit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.mismeet.android.AppModel
import app.mismeet.android.R
import app.mismeet.android.ago
import app.mismeet.android.measure
import app.mismeet.android.store.Contact
import app.mismeet.android.store.PersistedState
import app.mismeet.android.store.StoredRequest
import app.mismeet.protocol.LocationPayload
import app.mismeet.protocol.ProtocolConstants
import app.mismeet.protocol.TravelMode
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
            Text(stringResource(R.string.contacts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { adding = Adding("") }) { Text(stringResource(R.string.add)) }
        }
        if (state.contacts.isEmpty() && state.requests.isEmpty()) {
            Text(
                stringResource(R.string.contacts_empty),
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
                    distance = distance(context, contact, state),
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
            title = { Text(stringResource(R.string.photo_title, contact.name)) },
            text = { Text(stringResource(R.string.photo_text)) },
            confirmButton = {
                TextButton(onClick = {
                    photoFor = null
                    choosePhoto(contact.id)
                }) { Text(stringResource(R.string.photo_choose_another)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    photoFor = null
                    model.removePhoto(contact.id)
                }) { Text(stringResource(R.string.photo_remove)) }
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

    // The name question comes first, so that the add-back request a share sends carries it.
    val shareQuestion by model.shareQuestion.collectAsState()
    if (adding == null && openedInvite == null) {
        if (model.asksName(state)) {
            NameQuestion(state.ownName, onAnswer = { model.answerName(it) })
        } else {
            shareQuestion?.let { contact -> ShareQuestion(contact, onAnswer = { model.answerShareQuestion(it) }) }
        }
    }
}

@Composable
private fun NameQuestion(current: String, onAnswer: (String?) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = { onAnswer(null) },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.name_question_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.name_question_text))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ProtocolConstants.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.your_name)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { onAnswer(null) }) { Text(stringResource(R.string.skip)) } },
    )
}

@Composable
private fun ShareQuestion(contact: Contact, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(stringResource(R.string.share_question_title, contact.name)) },
        text = { Text(stringResource(R.string.share_question_text, contact.name)) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.share)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.not_now)) } },
    )
}

/** An Add contact dialog to open: an invite to fill in, or one from an add-back request, which stays hidden. */
private data class Adding(val invite: String, val name: String = "", val fromRequest: Boolean = false)

@Composable
private fun RequestRow(request: StoredRequest, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val sender = request.name ?: stringResource(R.string.unknown)
            Text(stringResource(R.string.request_added_you, sender), style = MaterialTheme.typography.titleMedium)
            val age = ago(LocalContext.current, request.createdAt * 1000)
            Text(stringResource(R.string.request_text, age), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text(stringResource(R.string.add_back)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
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
                    Text(status(LocalContext.current, contact, place), style = MaterialTheme.typography.bodySmall)
                }
                if (distance != null) Text(distance, style = MaterialTheme.typography.bodyMedium)
                if (onShare != null) Switch(checked = contact.share, onCheckedChange = onShare)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (contact.lastPayload != null) TextButton(onClick = onOpenMap) { Text(stringResource(R.string.open_in_map)) }
                TextButton(onClick = onPhoto) { Text(stringResource(R.string.photo)) }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.remove)) }
            }
        }
    }
}

/** Where and when a contact was last seen, such as "Kumpula, seen 2 minutes ago". */
fun status(context: Context, contact: Contact, place: String?): String {
    val payload = contact.lastPayload
    if (payload != null) {
        val age = ago(context, payload.fixTime * 1000)
        val stopped = contact.sharing == Contact.SharingStatus.NOT_SHARING
        return when {
            place != null && stopped -> context.getString(R.string.status_place_seen_stopped, place, age)
            place != null -> context.getString(R.string.status_place_seen, place, age)
            stopped -> context.getString(R.string.status_seen_stopped, age)
            else -> context.getString(R.string.status_seen, age)
        }
    }
    return when (contact.sharing) {
        Contact.SharingStatus.NOT_SHARING -> context.getString(R.string.status_not_sharing)
        else -> context.getString(R.string.status_no_location)
    }
}

/**
 * How far this phone's last fix is from a contact's, such as "850 m", "1.4 km" or "18 km", while
 * sharing keeps that fix current.
 */
fun distance(context: Context, contact: Contact, state: PersistedState): String? {
    val payload = contact.lastPayload ?: return null
    val own = state.lastFix?.takeIf { state.sharingEnabled } ?: return null
    val result = FloatArray(1)
    Location.distanceBetween(own.latitude, own.longitude, payload.latitude, payload.longitude, result)
    val rounded = (result[0] / 10).roundToInt() * 10
    if (rounded < 1_000) return measure(context, rounded, MeasureUnit.METER)
    val km = result[0] / 1_000
    return if (km < 10) measure(context, km, MeasureUnit.KILOMETER, 1) else measure(context, km.roundToInt(), MeasureUnit.KILOMETER)
}

/** How a contact was moving at the fix, such as "by bike at 18 km/h", or null when it was not. */
fun movement(context: Context, payload: LocationPayload): String? {
    val speed = payload.speed?.takeIf { it >= ProtocolConstants.ESTIMATE_MIN_SPEED } ?: return null
    val mode = when (TravelMode.of(speed)) {
        TravelMode.FOOT -> R.string.moving_on_foot
        TravelMode.BIKE -> R.string.moving_by_bike
        TravelMode.CAR -> R.string.moving_by_car
    }
    return context.getString(mode, measure(context, (speed * 3.6).roundToInt(), MeasureUnit.KILOMETER_PER_HOUR))
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
    val notValid = stringResource(R.string.invite_not_valid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (start.fromRequest) R.string.add_back else R.string.add_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!start.fromRequest) InviteField(invite, onChange = { invite = it })
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    placeholder = { Text(stringResource(R.string.name_placeholder)) },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching { onAdd(invite, name) }
                        .onSuccess { onDismiss() }
                        .onFailure { error = it.message ?: notValid }
                },
                enabled = invite.isNotBlank() && name.isNotBlank(),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun InviteField(invite: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val prompt = stringResource(R.string.scan_prompt)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onChange)
    }
    OutlinedTextField(
        value = invite,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.invite)) },
        placeholder = { Text(stringResource(R.string.invite_link)) },
        minLines = 3,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            onChange(
                context.getSystemService(ClipboardManager::class.java)
                    ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty(),
            )
        }) { Text(stringResource(R.string.paste)) }
        TextButton(onClick = {
            scanner.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(prompt)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        }) { Text(stringResource(R.string.scan_qr_code)) }
    }
}
