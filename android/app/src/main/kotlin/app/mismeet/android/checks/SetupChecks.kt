package app.mismeet.android.checks

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.MeasureUnit
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.location.LocationManagerCompat
import app.mismeet.android.AppModel
import app.mismeet.android.R
import app.mismeet.android.ago
import app.mismeet.android.location.LocationProvider
import app.mismeet.android.location.LocationService
import app.mismeet.android.locale
import app.mismeet.android.measure
import app.mismeet.android.nostr.RelayService
import app.mismeet.android.store.Contact
import app.mismeet.android.store.PersistedState
import app.mismeet.android.store.ShareFailure
import app.mismeet.android.ui.status
import app.mismeet.protocol.ProtocolConstants

enum class Outcome { OK, WARNING, PROBLEM, INFO }

data class Check(val title: String, val outcome: Outcome, val detail: String, val fix: Pair<String, () -> Unit>? = null)

// Each check is in the language of the context it gets: the app's on screen, English in problem reports.

fun phoneChecks(context: Context, model: AppModel, state: PersistedState, backToMe: () -> Unit): List<Check> {
    fun t(id: Int, vararg args: Any) = context.getString(id, *args)
    if (!state.sharingEnabled) {
        return listOf(
            Check(
                t(R.string.share_my_location),
                Outcome.WARNING,
                t(R.string.check_sharing_off),
                t(R.string.check_turn_on) to { model.setSharingEnabled(true) },
            ),
        )
    }
    val checks = mutableListOf(Check(t(R.string.share_my_location), Outcome.OK, t(R.string.check_on)))
    val openSettings = { action: String, withPackage: Boolean ->
        val intent = Intent(action)
        if (withPackage) intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }
    val backToMeFix = t(R.string.check_back_to_me) to backToMe
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager != null && !LocationManagerCompat.isLocationEnabled(manager)) {
        checks += Check(
            t(R.string.check_location),
            Outcome.PROBLEM,
            t(R.string.check_location_off),
            t(R.string.check_open_settings) to { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS, false) },
        )
    } else if (manager != null) {
        val locationSettings = t(R.string.check_open_settings) to { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS, false) }
        val gps = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val gpsTitle = t(R.string.check_gps)
        checks += when {
            gps -> Check(gpsTitle, Outcome.OK, t(R.string.check_on))
            network -> Check(gpsTitle, Outcome.WARNING, t(R.string.check_gps_network), locationSettings)
            else -> Check(gpsTitle, Outcome.PROBLEM, t(R.string.check_gps_none), locationSettings)
        }
        if (gps) {
            val networkTitle = t(R.string.check_network_location)
            checks += when {
                network -> Check(networkTitle, Outcome.OK, t(R.string.check_network_on))
                LocationManager.NETWORK_PROVIDER in manager.allProviders ->
                    Check(networkTitle, Outcome.INFO, t(R.string.check_network_off), locationSettings)
                else -> Check(networkTitle, Outcome.INFO, t(R.string.check_network_none))
            }
        }
    }
    val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val permissionTitle = t(R.string.check_location_permission)
    checks += when {
        LocationProvider.hasForegroundPermission(context) -> Check(permissionTitle, Outcome.OK, t(R.string.check_precise))
        coarse -> Check(permissionTitle, Outcome.PROBLEM, t(R.string.check_approximate), backToMeFix)
        else -> Check(permissionTitle, Outcome.PROBLEM, t(R.string.check_not_allowed), backToMeFix)
    }
    if (LocationProvider.hasForegroundPermission(context)) {
        checks += if (LocationProvider.hasBackgroundPermission(context)) {
            Check(t(R.string.check_background), Outcome.OK, t(R.string.check_background_ok))
        } else {
            Check(t(R.string.check_background), Outcome.PROBLEM, t(R.string.check_background_problem), backToMeFix)
        }
        checks += if (LocationService.running) {
            val minutes = (ProtocolConstants.ANDROID_PUBLISH_INTERVAL_SECONDS / 60).toInt()
            Check(
                t(R.string.check_service),
                Outcome.OK,
                context.resources.getQuantityString(R.plurals.check_service_running, minutes, minutes),
            )
        } else {
            Check(
                t(R.string.check_service),
                Outcome.PROBLEM,
                t(R.string.check_service_not_running),
                t(R.string.check_start_it) to { model.permissionsChanged() },
            )
        }
    }
    if (!LocationProvider.hasNotificationPermission(context)) {
        checks += Check(t(R.string.check_notifications), Outcome.WARNING, t(R.string.check_notifications_off), backToMeFix)
    }
    if (!LocationProvider.isIgnoringBatteryOptimizations(context)) {
        checks += Check(
            t(R.string.check_battery),
            Outcome.PROBLEM,
            t(R.string.check_battery_optimized),
            t(R.string.check_allow_unrestricted) to { openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true) },
        )
    }
    if (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) {
        checks += Check(t(R.string.check_battery_saver), Outcome.WARNING, t(R.string.check_battery_saver_on))
    }
    if (context.getSystemService(ConnectivityManager::class.java)?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
        checks += Check(
            t(R.string.check_data_saver),
            Outcome.WARNING,
            t(R.string.check_data_saver_on),
            t(R.string.check_open_settings) to { openSettings(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, true) },
        )
    }
    val now = System.currentTimeMillis()
    val sharingWith = state.contacts.count { it.sharesAt(now) }
    checks += if (sharingWith == 0) {
        Check(t(R.string.check_sharing_with), Outcome.WARNING, t(R.string.check_sharing_with_nobody))
    } else {
        Check(
            t(R.string.check_sharing_with),
            Outcome.OK,
            context.resources.getQuantityString(R.plurals.check_sharing_with_count, sharingWith, sharingWith),
        )
    }
    val failure = state.lastShareFailure
    val lastShared = state.lastPublishedAt
    val lastShare = t(R.string.check_last_share)
    checks += when {
        failure != null && failure.at > (lastShared ?: 0) ->
            Check(lastShare, Outcome.PROBLEM, t(R.string.check_last_share_failed, ago(context, failure.at), failureText(context, failure)))
        lastShared != null -> Check(lastShare, Outcome.OK, capitalized(context, ago(context, lastShared)))
        else -> Check(lastShare, Outcome.WARNING, t(R.string.check_not_yet))
    }
    state.lastFix?.let { fix ->
        val within = measure(context, fix.horizontalAccuracy.toInt(), MeasureUnit.METER)
        checks += Check(
            t(R.string.check_last_position),
            Outcome.INFO,
            t(R.string.check_last_position_detail, capitalized(context, ago(context, fix.timeMillis)), within),
        )
    }
    return checks
}

fun relayCheck(context: Context, check: RelayService.RelayCheck, state: PersistedState): Check {
    val name = runCatching { Uri.parse(check.url).host }.getOrNull() ?: check.url
    check.problem?.let { problem ->
        val reason = when (problem) {
            RelayService.Problem.NotAnAddress -> context.getString(R.string.check_relay_not_an_address)
            RelayService.Problem.NoAnswer -> {
                val seconds = ProtocolConstants.CONNECT_TIMEOUT_SECONDS.toInt()
                context.resources.getQuantityString(R.plurals.check_relay_no_answer, seconds, seconds)
            }
            is RelayService.Problem.Failed -> problem.message
        }
        return Check(name, Outcome.PROBLEM, context.getString(R.string.check_relay_unreachable, reason))
    }
    val at = check.ownLocationAt
    if (at == null) {
        val shared = state.sharingEnabled && state.lastPublishedCreatedAt != null
        return if (shared) {
            Check(name, Outcome.WARNING, context.getString(R.string.check_relay_answers_empty))
        } else {
            Check(name, Outcome.OK, context.getString(R.string.check_relay_answers))
        }
    }
    val last = state.lastPublishedCreatedAt
    if (last != null && at < last) return Check(name, Outcome.WARNING, context.getString(R.string.check_relay_older, ago(context, at * 1000)))
    return Check(name, Outcome.OK, context.getString(R.string.check_relay_current, ago(context, at * 1000)))
}

fun contactCheck(context: Context, contact: Contact): Check = when (contact.sharing) {
    Contact.SharingStatus.SHARING ->
        Check(contact.name, Outcome.OK, context.getString(R.string.check_contact_shares, status(context, contact, null)))
    Contact.SharingStatus.NOT_SHARING -> Check(contact.name, Outcome.WARNING, context.getString(R.string.check_contact_not_sharing))
    Contact.SharingStatus.UNKNOWN -> Check(contact.name, Outcome.WARNING, context.getString(R.string.check_contact_unknown))
}

/** Why a share failed; a failure stored before kinds existed has only its English reason. */
fun failureText(context: Context, failure: ShareFailure): String =
    failure.kind?.let { failureText(context, it, failure.detail) } ?: failure.reason

fun failureText(context: Context, kind: ShareFailure.Kind, detail: String?): String = when (kind) {
    ShareFailure.Kind.NO_POSITION -> context.getString(R.string.failure_no_position)
    ShareFailure.Kind.OLD_POSITION -> context.getString(R.string.failure_old_position)
    ShareFailure.Kind.NOT_ACCEPTED -> context.getString(R.string.failure_not_accepted, detail.orEmpty())
    ShareFailure.Kind.NOT_BUILT -> context.getString(R.string.failure_not_built)
}

private fun capitalized(context: Context, text: String) = text.replaceFirstChar { it.titlecase(context.locale()) }
