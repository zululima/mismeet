package app.mismeet.android.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.PowerManager
import androidx.core.location.LocationManagerCompat
import app.mismeet.protocol.LocationFix
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** A fix and where it came from: `gps`, `network`, or `last known` and either. */
data class ProvidedFix(val fix: LocationFix, val source: String)

/** Fixes from the platform location manager, never from a fused provider. */
object LocationProvider {
    private const val FIX_TIMEOUT_MILLIS = 45_000L

    fun hasForegroundPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Stock Android stops a foreground service it considers wasteful unless the app is exempted. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    /**
     * A fresh GPS fix, or where GPS finds nothing in time, as indoors, a fresh one from the network
     * provider, asked for at the same time; failing both, the newest fix either last gave.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentFix(context: Context): ProvidedFix? {
        if (!hasForegroundPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }
        if (providers.isEmpty()) return null
        val fresh = coroutineScope {
            val requests = providers.map { provider -> provider to async { request(context, manager, provider) } }
            val found = requests.firstNotNullOfOrNull { (provider, request) ->
                request.await()?.let(::toFix)?.let { ProvidedFix(it, provider) }
            }
            requests.forEach { it.second.cancel() }
            found
        }
        return fresh ?: providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let(::toFix)?.let { ProvidedFix(it, "last known $provider") }
        }.maxByOrNull { it.fix.timeMillis }
    }

    @SuppressLint("MissingPermission")
    private suspend fun request(context: Context, manager: LocationManager, provider: String): Location? =
        withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, context.mainExecutor) { continuation.resume(it) }
                } catch (e: SecurityException) {
                    continuation.resume(null)
                }
            }
        }

    fun toFix(location: Location): LocationFix? {
        if (!location.hasAccuracy()) return null
        return LocationFix(
            timeMillis = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracy = location.accuracy.toDouble(),
            ellipsoidalAltitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null,
            course = if (location.hasBearing()) location.bearing.toDouble() else null,
        )
    }
}
