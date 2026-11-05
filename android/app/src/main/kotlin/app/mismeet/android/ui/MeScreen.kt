package app.mismeet.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Color
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.mismeet.android.AppModel
import app.mismeet.android.location.LocationProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun MeScreen(model: AppModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    if (checking) {
        SetupChecksScreen(model, onDone = { checking = false }, modifier)
        return
    }
    var permissionVersion by remember { mutableIntStateOf(0) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionVersion++
        model.permissionsChanged()
    }
    val foreground = remember(permissionVersion) { LocationProvider.hasForegroundPermission(context) }
    val background = remember(permissionVersion) { LocationProvider.hasBackgroundPermission(context) }
    val notifications = remember(permissionVersion) { LocationProvider.hasNotificationPermission(context) }
    val unrestrictedBattery = remember(permissionVersion) { LocationProvider.isIgnoringBatteryOptimizations(context) }
    LifecycleResumeEffect(Unit) {
        permissionVersion++
        onPauseOrDispose {}
    }
    val inviteLink = remember(state.relays) { model.inviteLink() }
    val qr = remember(inviteLink) { qrBitmap(inviteLink) }
    // Google Play requires this disclosure to come right before each location permission request.
    var pendingLocationRequest by remember { mutableStateOf<Array<String>?>(null) }

    pendingLocationRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingLocationRequest = null },
            title = { Text("Sharing your location") },
            text = { Text(LOCATION_DISCLOSURE) },
            confirmButton = {
                TextButton(onClick = {
                    pendingLocationRequest = null
                    permissions.launch(request)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { pendingLocationRequest = null }) { Text("Not now") } },
        )
    }

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Invite", style = MaterialTheme.typography.titleMedium)
        Image(qr.asImageBitmap(), contentDescription = "Invite QR code", modifier = Modifier.size(240.dp).align(Alignment.CenterHorizontally))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Add me on Mismeet: $inviteLink")
                context.startActivity(Intent.createChooser(send, "Share invite"))
            }) { Text("Share invite") }
            OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Mismeet invite", inviteLink))
            }) { Text("Copy link") }
        }
        Text(
            "Send the link, or let them scan the code. When they add you with sharing on, your contacts show a request to add them back.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = state.ownName,
            onValueChange = { model.setOwnName(it) },
            label = { Text("Your name") },
            supportingText = { Text("Sent to the people you add, so they know who asks to be added back.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Sharing from this phone", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Share my location")
            Switch(checked = state.sharingEnabled, onCheckedChange = { model.setSharingEnabled(it) })
        }
        if (!state.sharingEnabled) {
            Text(
                "Off: this phone only watches its contacts, shares nothing and needs no location permission.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            if (!foreground) {
                Button(onClick = { pendingLocationRequest = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                    Text("Allow location")
                }
            } else if (!background) {
                Button(onClick = { pendingLocationRequest = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) {
                    Text("Allow location all the time")
                }
            }
            if (!notifications) {
                Button(onClick = { permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }) { Text("Allow notifications") }
            }
            if (!unrestrictedBattery) {
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Allow unrestricted battery use") }
            }
            Button(onClick = { model.publishNow() }, enabled = foreground) { Text("Share now") }
            state.lastPublishedAt?.let {
                Text("Last shared with contacts ${DateFormat.getTimeFormat(context).format(it)}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Problem reports", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Send problem reports")
            Switch(checked = state.reportsEnabled, onCheckedChange = { model.setReportsEnabled(it) })
        }
        Text(REPORTS_FOOTER, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { checking = true }) { Text("Check setup") }
    }
}

private const val REPORTS_FOOTER =
    "When a share fails or the app crashes, sends the developer an encrypted report, at most once a day, from a key " +
        "made for that report alone. It holds the setup checks and the app's recent messages, never your location, " +
        "contacts or keys. Check setup shows a report before you send one."

private const val LOCATION_DISCLOSURE =
    "Mismeet collects location data to share your position with the contacts you choose, even when the app is closed " +
        "or not in use. Each position is encrypted for those contacts alone, sent to public Nostr relays and " +
        "expires after 24 hours. It is never used for ads."

private fun qrBitmap(text: String, size: Int = 480): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { index -> if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}
