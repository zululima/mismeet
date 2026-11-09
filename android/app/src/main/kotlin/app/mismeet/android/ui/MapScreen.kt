package app.mismeet.android.ui

import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import app.mismeet.android.AppModel
import app.mismeet.android.R
import app.mismeet.android.location.Countries
import app.mismeet.android.shareUntil
import app.mismeet.android.store.Contact
import app.mismeet.protocol.EstimateResult
import app.mismeet.protocol.LatLon
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * OpenStreetMap tiles through osmdroid: no Google dependency, and the tile policy wants a user
 * agent. Opens framed around the people in this phone's country, like Find My, again each time the
 * app comes back, and follows them as they move until the map is moved by hand; the people button
 * at the bottom right returns there, a second tap widens to everyone, abroad included, and the
 * arrow below it centres on this phone, as the two buttons of Find My do. The contact list is
 * a sheet over the map, peeking at about a third of the screen as Find My's does, and the
 * [focused] contact, tapped in it or on the map, has its card in the sheet until it is closed and
 * is framed while its position is known.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(model: AppModel, focused: String?, onFocus: (String) -> Unit, onUnfocus: () -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val placeNames by model.placeNames.collectAsState()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        true
    }
    var estimates by remember { mutableStateOf<Map<String, EstimateResult.Estimate>>(emptyMap()) }
    val countries by produceState<Countries?>(null) { value = withContext(Dispatchers.Default) { model.countries } }

    val located = state.contacts.filter { it.lastPayload != null }
    // The last fix stops updating once sharing is off, so it would pull the framing to an old place.
    val own = state.lastFix?.takeIf { state.sharingEnabled }?.let { LatLon(it.latitude, it.longitude) }
    val home = countries?.home(own, located.map { it.position() })
    val inHome = { point: LatLon -> home == null || countries?.code(point) == home }
    val everyone = points(located, own, estimates) { true }
    val nearby = points(located, own, estimates, inHome).ifEmpty { everyone }
    val someoneAbroad = nearby.size != everyone.size
    val focusedContact = state.contacts.firstOrNull { it.id == focused }
    val focusKey = focusedContact?.lastPayload?.let { "${focusedContact.id}:${it.latitude},${it.longitude},${it.accuracy}" }
    val peek = (LocalConfiguration.current.screenHeightDp * PEEK_FRACTION).dp
    val sheet = rememberStandardBottomSheetState(
        initialValue = if (state.contacts.isEmpty()) SheetValue.Expanded else SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    var handlePixels by remember { mutableIntStateOf(0) }
    var listPixels by remember { mutableIntStateOf(0) }
    // Pulled up, the list covers the lower part of the map, so the framing fits above it, as on iOS.
    val listUp = focusedContact == null && sheet.targetValue == SheetValue.Expanded
    val sheetPixels = if (listUp) handlePixels + listPixels else with(LocalDensity.current) { peek.roundToPx() }

    var framing by remember { mutableStateOf(Framing.NEARBY) }
    var framedAt by remember { mutableStateOf<Pair<GeoPoint, Double>?>(null) }
    var moved by remember { mutableStateOf(false) }
    val frameKey = focusKey
        ?: (located.joinToString(",") { it.id } + ":" + estimates.keys.sorted().joinToString(",") + ":" + (own != null) + ":" + home)
    val framedKey = remember { arrayOf("") }
    val framedListUp = remember { booleanArrayOf(false) }
    // Positions stored at launch are replaced by the first fetch a moment later.
    val positionKey = located.joinToString(",") { "${it.id}:${it.lastEventId}" } + ":" + own
    val framedPositions = remember { arrayOf("") }
    val map = remember { arrayOfNulls<MapView>(1) }
    val show by rememberUpdatedState { view: MapView, target: Framing ->
        framing = target
        frame(view, if (target == Framing.NEARBY) nearby else everyone, sheetPixels)
        framedAt = GeoPoint(view.mapCenter.latitude, view.mapCenter.longitude) to view.zoomLevelDouble
        moved = false
        if (target == Framing.NEARBY) framedKey[0] = frameKey
    }
    val reframe by rememberUpdatedState { view: MapView ->
        val payload = focusedContact?.lastPayload
        if (payload == null) {
            show(view, Framing.NEARBY)
        } else {
            frameAround(view, GeoPoint(payload.latitude, payload.longitude), payload.accuracy, sheetPixels)
            framedAt = GeoPoint(view.mapCenter.latitude, view.mapCenter.longitude) to view.zoomLevelDouble
            moved = false
            framedKey[0] = frameKey
        }
    }
    LaunchedEffect(state.contacts) {
        while (true) {
            estimates = estimates.filterKeys { id -> state.contacts.any { it.id == id } }
            state.contacts.forEach { contact ->
                val estimate = contact.lastPayload?.let { model.roads.estimate(it, System.currentTimeMillis() / 1000) }
                estimates = if (estimate == null) estimates - contact.id else estimates + (contact.id to estimate)
            }
            delay(ESTIMATE_REFRESH_MILLIS)
        }
    }
    val markerSize = with(LocalDensity.current) { 44.dp.roundToPx() }
    val focusedMarkerSize = with(LocalDensity.current) { 56.dp.roundToPx() }
    LaunchedEffect(focused) {
        if (focused != null) sheet.partialExpand()
    }
    LifecycleStartEffect(Unit) {
        map[0]?.takeIf { it.width > 0 }?.let { reframe(it) }
        onStopOrDispose {}
    }

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = rememberBottomSheetScaffoldState(sheet),
        sheetPeekHeight = peek,
        sheetDragHandle = { BottomSheetDefaults.DragHandle(Modifier.onSizeChanged { handlePixels = it.height }) },
        sheetContent = {
            val contact = focusedContact
            if (contact == null) {
                ContactList(model, onFocus, Modifier.fillMaxHeight(LIST_HEIGHT).onSizeChanged { listPixels = it.height })
            } else {
                var askingShare by remember(contact.id) { mutableStateOf(false) }
                val sharing = state.sharingEnabled && contact.share
                Column(
                    Modifier.fillMaxWidth().height(peek).padding(start = 16.dp, end = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(contact, model.photo(contact), 48.dp, Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.titleLarge)
                            Text(status(context, contact, placeNames[contact.id]), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = onUnfocus) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                    }
                    if (contact.lastPayload != null) {
                        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { openMap(context, contact) }) { Text(stringResource(R.string.directions)) }
                            distance(context, contact, state)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.share_my_location), style = MaterialTheme.typography.bodyLarge)
                            contact.shareEndsAt?.takeIf { sharing }?.let {
                                Text(shareUntil(context, it), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Switch(checked = sharing, onCheckedChange = { on -> if (on) askingShare = true else model.setShare(contact.id, false) })
                    }
                }
                if (askingShare) {
                    ShareQuestion(contact, onAnswer = { duration ->
                        askingShare = false
                        duration?.let { model.shareWith(contact.id, it) }
                    })
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            // osmdroid draws the partial tiles at its edges outside its bounds; Compose does not clip a hosted view.
            AndroidView(
                modifier = Modifier.fillMaxSize().clipToBounds(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        // At 256 px a tile's labels are unreadably small on a dense screen.
                        isTilesScaledToDpi = true
                        setMultiTouchControls(true)
                        controller.setZoom(12.0)
                        controller.setCenter(nearby.firstOrNull() ?: GeoPoint(60.17, 24.94))
                        addOnFirstLayoutListener { _, _, _, _, _ -> reframe(this) }
                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?) = noteMove(this@apply)
                            override fun onZoom(event: ZoomEvent?) = noteMove(this@apply)

                            private fun noteMove(view: MapView): Boolean {
                                val (center, zoom) = framedAt ?: return false
                                moved = abs(view.mapCenter.latitude - center.latitude) > 1e-5 ||
                                    abs(view.mapCenter.longitude - center.longitude) > 1e-5 ||
                                    abs(view.zoomLevelDouble - zoom) > 1e-3
                                return false
                            }
                        })
                        onResume()
                        map[0] = this
                    }
                },
                update = { view ->
                    view.overlayManager.tilesOverlay.setColorFilter(if (dark) DARK_TILES else null)
                    view.overlayManager.tilesOverlay.loadingBackgroundColor = if (dark) DARK_LOADING else LIGHT_LOADING
                    view.overlays.clear()
                    focusedContact?.lastPayload?.let { payload ->
                        view.overlays += Polygon(view).apply {
                            setPoints(Polygon.pointsAsCircle(GeoPoint(payload.latitude, payload.longitude), payload.accuracy.toDouble()))
                            fillPaint.color = FOCUS_FILL
                            outlinePaint.color = Color.WHITE
                            outlinePaint.strokeWidth = markerSize / 14f
                            infoWindow = null
                        }
                    }
                    located.forEach { contact ->
                        val payload = contact.lastPayload ?: return@forEach
                        val avatar = avatarBitmap(contact, model.photo(contact), markerSize)
                        estimates[contact.id]?.let { estimate ->
                            view.overlays += Polyline(view).apply {
                                setPoints(estimate.path.map { GeoPoint(it.latitude, it.longitude) })
                                outlinePaint.color = ESTIMATE_COLOUR
                                outlinePaint.strokeWidth = markerSize / 8f
                                outlinePaint.pathEffect = DashPathEffect(floatArrayOf(markerSize / 4f, markerSize / 5f), 0f)
                            }
                            view.overlays += Marker(view).apply {
                                position = GeoPoint(estimate.point.latitude, estimate.point.longitude)
                                icon = BitmapDrawable(view.resources, avatar)
                                alpha = 0.45f
                                title = context.getString(R.string.estimated_title, contact.name)
                                snippet = movement(context, payload)?.let { context.getString(R.string.estimated_snippet_moving, it) }
                                    ?: context.getString(R.string.estimated_snippet)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                setOnMarkerClickListener { _, _ ->
                                    onFocus(contact.id)
                                    true
                                }
                            }
                        }
                        val size = if (contact.id == focused) focusedMarkerSize else markerSize
                        view.overlays += Marker(view).apply {
                            position = GeoPoint(payload.latitude, payload.longitude)
                            icon = BitmapDrawable(view.resources, if (size == markerSize) avatar else avatarBitmap(contact, model.photo(contact), size))
                            title = contact.name
                            snippet = status(context, contact, placeNames[contact.id])
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setOnMarkerClickListener { _, _ ->
                                onFocus(contact.id)
                                true
                            }
                        }
                    }
                    own?.let { fix ->
                        view.overlays += Marker(view).apply {
                            position = GeoPoint(fix.latitude, fix.longitude)
                            icon = BitmapDrawable(view.resources, ownPositionBitmap(markerSize / 2))
                            title = context.getString(R.string.you)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                    }
                    view.invalidate()
                    if (framedKey[0] != frameKey && view.width > 0) {
                        reframe(view)
                    } else if ((framedListUp[0] != listUp || framedPositions[0] != positionKey) &&
                        focusedContact == null && !moved && view.width > 0
                    ) {
                        show(view, framing)
                    }
                    framedListUp[0] = listUp
                    framedPositions[0] = positionKey
                },
                onRelease = { view -> view.onDetach() },
            )
            val widen = focusedContact == null && someoneAbroad && framing == Framing.NEARBY && !moved
            val framingLabel = if (someoneAbroad && !widen) {
                countryName(home, LocalConfiguration.current.locales[0]) ?: stringResource(R.string.home)
            } else {
                stringResource(R.string.everyone)
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = with(LocalDensity.current) { sheetPixels.toDp() } + 12.dp),
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.width(44.dp)) {
                    IconButton(
                        onClick = { if (focusedContact != null) onUnfocus() else map[0]?.let { show(it, if (widen) Framing.EVERYONE else Framing.NEARBY) } },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(painterResource(if (widen) R.drawable.ic_globe else R.drawable.ic_people), contentDescription = framingLabel, tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                    IconButton(
                        onClick = {
                            if (focusedContact != null) onUnfocus()
                            own?.let { fix -> map[0]?.controller?.animateTo(GeoPoint(fix.latitude, fix.longitude), OWN_ZOOM, OWN_ANIMATION_MILLIS) }
                            framedAt = null
                            moved = true
                        },
                        enabled = own != null,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_near_me), contentDescription = stringResource(R.string.my_location), tint = if (own != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                }
            }
        }
    }
}

private enum class Framing { NEARBY, EVERYONE }

private const val ESTIMATE_REFRESH_MILLIS = 10_000L
private const val OWN_ZOOM = 16.0
private const val OWN_ANIMATION_MILLIS = 600L
private const val LIST_HEIGHT = 0.85f
private const val PEEK_FRACTION = 0.36f
private const val ESTIMATE_COLOUR = 0xCC5E35B1.toInt()
private const val FOCUS_FILL = 0x402979FF
private const val LIGHT_LOADING = 0xFFD8D0D0.toInt()
private const val DARK_LOADING = 0xFF202124.toInt()

/** Inverted tiles with the hue turned back 180 degrees: dark land, while water stays blue and parks green. */
private val DARK_TILES = ColorMatrixColorFilter(
    floatArrayOf(
        0.574f, -1.43f, -0.144f, 0f, 255f,
        -0.426f, -0.43f, -0.144f, 0f, 255f,
        -0.426f, -1.43f, 0.856f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

private fun Contact.position() = lastPayload!!.let { LatLon(it.latitude, it.longitude) }

/** The contacts and this phone that pass [keep], each contact with its estimated position too. */
private fun points(
    located: List<Contact>,
    own: LatLon?,
    estimates: Map<String, EstimateResult.Estimate>,
    keep: (LatLon) -> Boolean,
): List<GeoPoint> =
    (located.filter { keep(it.position()) }.flatMap { listOfNotNull(it.position(), estimates[it.id]?.point) } + listOfNotNull(own?.takeIf(keep)))
        .map { GeoPoint(it.latitude, it.longitude) }

private fun countryName(code: String?, language: Locale): String? =
    code?.let { runCatching { Locale.Builder().setRegion(it).build().getDisplayCountry(language) }.getOrNull() }?.ifBlank { null }

/** Frames a focused contact with the circle of its accuracy, and at least 350 m across, as close as Find My. */
private fun frameAround(map: MapView, point: GeoPoint, accuracy: Int, sheetPixels: Int) {
    val half = maxOf(accuracy * 1.5, 175.0)
    val dLat = half / 110_574.0
    val dLon = half / (cos(Math.toRadians(point.latitude)) * 111_320.0)
    fit(map, BoundingBox(point.latitude + dLat, point.longitude + dLon, point.latitude - dLat, point.longitude - dLon), 0, sheetPixels)
}

private fun frame(map: MapView, points: List<GeoPoint>, sheetPixels: Int) {
    when (points.size) {
        0 -> return
        1 -> {
            map.controller.setZoom(14.0)
            map.controller.setCenter(points.first())
            liftAbove(map, sheetPixels)
        }
        else -> fit(map, BoundingBox.fromGeoPoints(points), 96, sheetPixels)
    }
}

/**
 * Zooms [box] into the part of the map above the sheet, [border] pixels in from its edges.
 * zoomToBoundingBox puts its border on every side, which leaves no width once the sheet is up.
 */
private fun fit(map: MapView, box: BoundingBox, border: Int, sheetPixels: Int) {
    val width = (map.width - 2 * border).coerceAtLeast(1)
    val height = (map.height - sheetPixels - 2 * border).coerceAtLeast(1)
    val zoom = MapView.getTileSystem().getBoundingBoxZoom(box, width, height)
    map.controller.setZoom(if (zoom == Double.MIN_VALUE) map.maxZoomLevel else zoom.coerceIn(map.minZoomLevel, map.maxZoomLevel))
    map.controller.setCenter(GeoPoint(mercatorMiddle(box.latNorth, box.latSouth), box.centerWithDateLine.longitude))
    liftAbove(map, sheetPixels)
}

/** The latitude halfway between two others on the map, which is not their average. */
private fun mercatorMiddle(north: Double, south: Double): Double {
    fun y(latitude: Double) = ln(tan(Math.PI / 4 + Math.toRadians(latitude) / 2))
    return Math.toDegrees(atan(sinh((y(north) + y(south)) / 2)))
}

/** Moves what is centred into the middle of the part of the map above the sheet. */
private fun liftAbove(map: MapView, sheetPixels: Int) {
    map.controller.setCenter(map.projection.fromPixels(map.width / 2, map.height / 2 + sheetPixels / 2))
}
