package app.mismeet.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.mismeet.android.AppModel
import app.mismeet.android.BuildConfig
import app.mismeet.android.location.LocationProvider
import app.mismeet.android.location.LocationService
import app.mismeet.android.nostr.RelayService
import app.mismeet.android.store.Contact
import app.mismeet.android.store.PersistedState
import app.mismeet.protocol.ProtocolConstants

private enum class Outcome { OK, WARNING, PROBLEM, INFO }

private data class Check(val title: String, val outcome: Outcome, val detail: String, val fix: Pair<String, () -> Unit>? = null)

/**
 * What has to hold for sharing to work, checked one by one as Slack's connection test does, each
 * with a way to fix it where Android offers one. Location permissions are asked on the Me screen,
 * right after the disclosure Play demands, so their checks lead back there.
 */
@Composable
fun SetupChecksScreen(model: AppModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onDone)
    val state by model.state.collectAsState()
    val context = LocalContext.current
    var round by remember { mutableIntStateOf(0) }
    var relayChecks by remember { mutableStateOf<List<RelayService.RelayCheck>?>(null) }
    // Settings changed in another app show when the user comes back.
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        onPauseOrDispose {}
    }
    LaunchedEffect(round) {
        relayChecks = null
        relayChecks = model.checkRelays()
    }
    val phone = remember(state, resumed) { phoneChecks(context, model, state, onDone) }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Check setup", style = MaterialTheme.typography.titleLarge)
            Text("This phone", style = MaterialTheme.typography.titleMedium)
            phone.forEach { CheckRow(it) }
            Text("Relays", style = MaterialTheme.typography.titleMedium)
            relayChecks?.forEach { CheckRow(relayCheck(it, state)) } ?: Text("Asking each relay...", style = MaterialTheme.typography.bodyMedium)
            Text("Contacts", style = MaterialTheme.typography.titleMedium)
            if (state.contacts.isEmpty()) CheckRow(Check("No contacts", Outcome.WARNING, "Add someone from the invite they sent you."))
            state.contacts.forEach { CheckRow(contactCheck(it)) }
            Text("App", style = MaterialTheme.typography.titleMedium)
            CheckRow(
                Check(
                    "Version",
                    Outcome.INFO,
                    "Mismeet ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.RELEASE}",
                ),
            )
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
            Button(onClick = { round++ }, enabled = relayChecks != null, modifier = Modifier.weight(1f)) { Text("Run again") }
        }
    }
}

@Composable
private fun CheckRow(check: Check) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val (icon, tint) = when (check.outcome) {
            Outcome.OK -> Icons.Filled.CheckCircle to Color(0xFF2E7D32)
            Outcome.WARNING -> Icons.Filled.Warning to Color(0xFFEF6C00)
            Outcome.PROBLEM -> Icons.Filled.Close to Color(0xFFC62828)
            Outcome.INFO -> Icons.Filled.Info to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = check.outcome.name.lowercase(), tint = tint, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(check.title, style = MaterialTheme.typography.titleSmall)
            Text(check.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            check.fix?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
        }
    }
}

private fun phoneChecks(context: Context, model: AppModel, state: PersistedState, backToMe: () -> Unit): List<Check> {
    if (!state.sharingEnabled) {
        return listOf(
            Check(
                "Share my location",
                Outcome.WARNING,
                "Off: this phone only watches its contacts and shares nothing.",
                "Turn on" to { model.setSharingEnabled(true) },
            ),
        )
    }
    val checks = mutableListOf(Check("Share my location", Outcome.OK, "On"))
    val openSettings = { action: String, withPackage: Boolean ->
        val intent = Intent(action)
        if (withPackage) intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager != null && !LocationManagerCompat.isLocationEnabled(manager)) {
        checks += Check(
            "Location",
            Outcome.PROBLEM,
            "Off for the whole phone.",
            "Open settings" to { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS, false) },
        )
    } else if (manager != null && !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        checks += Check("GPS", Outcome.PROBLEM, "Off: Mismeet takes its positions from GPS.")
    }
    val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    checks += when {
        LocationProvider.hasForegroundPermission(context) -> Check("Location permission", Outcome.OK, "Precise")
        coarse -> Check(
            "Location permission",
            Outcome.PROBLEM,
            "Approximate: contacts see a point that can be kilometres away.",
            "Back to Me" to backToMe,
        )
        else -> Check("Location permission", Outcome.PROBLEM, "Not allowed. Allow it on the Me screen.", "Back to Me" to backToMe)
    }
    if (LocationProvider.hasForegroundPermission(context)) {
        checks += if (LocationProvider.hasBackgroundPermission(context)) {
            Check("In the background", Outcome.OK, "Allowed all the time")
        } else {
            Check(
                "In the background",
                Outcome.PROBLEM,
                "Only while the app is open: sharing stops once Mismeet is in the background.",
                "Back to Me" to backToMe,
            )
        }
        checks += if (LocationService.running) {
            Check("Sharing service", Outcome.OK, "Running, shares every ${ProtocolConstants.ANDROID_PUBLISH_INTERVAL_SECONDS / 60} minutes")
        } else {
            Check("Sharing service", Outcome.PROBLEM, "Not running.", "Start it" to { model.permissionsChanged() })
        }
    }
    if (!LocationProvider.hasNotificationPermission(context)) {
        checks += Check("Notifications", Outcome.WARNING, "Off: the notification that sharing is on stays hidden.", "Back to Me" to backToMe)
    }
    if (!LocationProvider.isIgnoringBatteryOptimizations(context)) {
        checks += Check(
            "Battery",
            Outcome.PROBLEM,
            "Optimized: Android can stop sharing in the background.",
            "Allow unrestricted" to { openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true) },
        )
    }
    if (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) {
        checks += Check("Battery Saver", Outcome.WARNING, "On: Android cuts background work, which can delay sharing.")
    }
    if (context.getSystemService(ConnectivityManager::class.java)?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
        checks += Check(
            "Data Saver",
            Outcome.WARNING,
            "On, and Mismeet is not exempt: Android can cut its network in the background.",
            "Open settings" to { openSettings(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, true) },
        )
    }
    val sharingWith = state.contacts.count { it.share }
    checks += if (sharingWith == 0) {
        Check("Sharing with", Outcome.WARNING, "Nobody: add a contact, or turn sharing on for one.")
    } else {
        Check("Sharing with", Outcome.OK, if (sharingWith == 1) "1 contact" else "$sharingWith contacts")
    }
    val failure = state.lastShareFailure
    val lastShared = state.lastPublishedAt
    checks += when {
        failure != null && failure.at > (lastShared ?: 0) -> Check("Last share", Outcome.PROBLEM, "Failed ${ago(failure.at)}: ${failure.reason}")
        lastShared != null -> Check("Last share", Outcome.OK, ago(lastShared).toString())
        else -> Check("Last share", Outcome.WARNING, "Not yet")
    }
    state.lastFix?.let { fix ->
        checks += Check("Last position", Outcome.INFO, "${ago(fix.timeMillis)}, within ${fix.horizontalAccuracy.toInt()} m")
    }
    return checks
}

private fun relayCheck(check: RelayService.RelayCheck, state: PersistedState): Check {
    val name = runCatching { Uri.parse(check.url).host }.getOrNull() ?: check.url
    check.problem?.let { return Check(name, Outcome.PROBLEM, "Unreachable: $it") }
    val at = check.ownLocationAt
    if (at == null) {
        val shared = state.sharingEnabled && state.lastPublishedCreatedAt != null
        return Check(name, if (shared) Outcome.WARNING else Outcome.OK, if (shared) "Answers, but holds no location of yours" else "Answers")
    }
    val last = state.lastPublishedCreatedAt
    if (last != null && at < last) return Check(name, Outcome.WARNING, "Holds an older location of yours, shared ${ago(at * 1000)}")
    return Check(name, Outcome.OK, "Holds your location shared ${ago(at * 1000)}")
}

private fun contactCheck(contact: Contact): Check = when (contact.sharing) {
    Contact.SharingStatus.SHARING -> Check(contact.name, Outcome.OK, "Shares with you. ${status(contact, null)}")
    Contact.SharingStatus.NOT_SHARING -> Check(contact.name, Outcome.WARNING, "Has not added you, or does not share with you")
    Contact.SharingStatus.UNKNOWN -> Check(contact.name, Outcome.WARNING, "No location found on their relays yet")
}

private fun ago(millis: Long): CharSequence =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
