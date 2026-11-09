package app.mismeet.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.mismeet.android.AppModel
import app.mismeet.android.R
import app.mismeet.android.location.LocationProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * One screen without scrolling at the default font size: the invite, the name, the switch for
 * sharing with what it needs, and the way to the setup checks, which hold everything else.
 */
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
            title = { Text(stringResource(R.string.disclosure_title)) },
            text = { Text(stringResource(R.string.disclosure_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingLocationRequest = null
                    permissions.launch(request)
                }) { Text(stringResource(R.string.continue_button)) }
            },
            dismissButton = { TextButton(onClick = { pendingLocationRequest = null }) { Text(stringResource(R.string.not_now)) } },
        )
    }

    val shareText = stringResource(R.string.share_invite_text, inviteLink)
    val shareTitle = stringResource(R.string.share_invite)
    val clipLabel = stringResource(R.string.invite_clip_label)
    // Scrolls only when the content does not fit, as with a very large font.
    val scroll = rememberScrollState()
    Column(
        modifier.fillMaxSize().verticalScroll(scroll, enabled = scroll.maxValue > 0).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.invite), style = MaterialTheme.typography.titleMedium)
        Image(
            qr.asImageBitmap(),
            contentDescription = stringResource(R.string.invite_qr_code),
            modifier = Modifier.size(168.dp).align(Alignment.CenterHorizontally),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                context.startActivity(Intent.createChooser(send, shareTitle))
            }) { Text(shareTitle) }
            OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(clipLabel, inviteLink))
            }) { Text(stringResource(R.string.copy_invite_link)) }
        }
        Text(stringResource(R.string.invite_footer), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = state.ownName,
            onValueChange = { model.setOwnName(it) },
            label = { Text(stringResource(R.string.your_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            stringResource(R.string.sharing_from_this_phone),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.share_my_location), modifier = Modifier.weight(1f))
            Switch(checked = state.sharingEnabled, onCheckedChange = { model.setSharingEnabled(it) })
        }
        if (!state.sharingEnabled) {
            Text(stringResource(R.string.sharing_off_footer), style = MaterialTheme.typography.bodySmall)
        } else {
            if (!foreground) {
                Button(onClick = { pendingLocationRequest = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                    Text(stringResource(R.string.allow_location))
                }
            } else if (!background) {
                Button(onClick = { pendingLocationRequest = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) {
                    Text(stringResource(R.string.allow_location_always))
                }
            }
            if (!notifications) {
                Button(onClick = { permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }) {
                    Text(stringResource(R.string.allow_notifications))
                }
            }
            if (!unrestrictedBattery) {
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text(stringResource(R.string.allow_battery)) }
            }
        }
        OutlinedButton(onClick = { checking = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.check_setup))
        }
    }
}

private fun qrBitmap(text: String, size: Int = 480): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { index -> if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
}
