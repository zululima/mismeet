package app.mismeet.android.checks

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.core.location.LocationManagerCompat
import app.mismeet.android.AppModel
import app.mismeet.android.location.LocationProvider
import app.mismeet.android.location.LocationService
import app.mismeet.android.nostr.RelayService
import app.mismeet.android.store.Contact
import app.mismeet.android.store.PersistedState
import app.mismeet.android.ui.status
import app.mismeet.protocol.ProtocolConstants

enum class Outcome { OK, WARNING, PROBLEM, INFO }

data class Check(val title: String, val outcome: Outcome, val detail: String, val fix: Pair<String, () -> Unit>? = null)

fun phoneChecks(context: Context, model: AppModel, state: PersistedState, backToMe: () -> Unit): List<Check> {
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

fun relayCheck(check: RelayService.RelayCheck, state: PersistedState): Check {
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

fun contactCheck(contact: Contact): Check = when (contact.sharing) {
    Contact.SharingStatus.SHARING -> Check(contact.name, Outcome.OK, "Shares with you. ${status(contact, null)}")
    Contact.SharingStatus.NOT_SHARING -> Check(contact.name, Outcome.WARNING, "Has not added you, or does not share with you")
    Contact.SharingStatus.UNKNOWN -> Check(contact.name, Outcome.WARNING, "No location found on their relays yet")
}

fun ago(millis: Long): CharSequence =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
