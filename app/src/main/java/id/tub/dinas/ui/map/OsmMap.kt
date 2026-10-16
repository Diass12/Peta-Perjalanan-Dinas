package id.tub.dinas.ui.map

import android.graphics.Color as AColor
import id.tub.dinas.ui.theme.ThemeState
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.tub.dinas.domain.GeoPoint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint as OsmPoint

enum class PinKind(val argb: Int, val res: Int, val glow: Int) {
    DOK(0xFFE9C23B.toInt(), id.tub.dinas.R.drawable.pin_dok, 0xFFFFD54A.toInt()),
    LAP(0xFF14532D.toInt(), id.tub.dinas.R.drawable.pin_lap, 0xFF39FF88.toInt()),
    PICK(0xFFB3261E.toInt(), id.tub.dinas.R.drawable.pin_saya, 0xFFFF5A4A.toInt()),
}

/** Kendaraan yang berjalan di garis rute. facesRight = gambar aslinya menghadap ke kanan. */
class VehicleSkin(val key: String, val res: Int, val facesRight: Boolean, val onRoad: Boolean)
val VEHICLES = listOf(
    VehicleSkin("mobil", id.tub.dinas.R.drawable.kend_mobil, true, true),
    VehicleSkin("motor", id.tub.dinas.R.drawable.kend_motor, true, true),
    VehicleSkin("pesawat", id.tub.dinas.R.drawable.kend_pesawat, false, false),
    VehicleSkin("boat", id.tub.dinas.R.drawable.kend_boat, true, false),
    VehicleSkin("kapal", id.tub.dinas.R.drawable.kend_kapal, true, false),
)

data class MapPin(val id: String, val geo: GeoPoint, val title: String, val kind: PinKind)

enum class MapStyle(val label: String) {
    JALAN("Jalan"), OSM("OSM"), SATELIT("Satelit"), HIBRIDA("Satelit+Label"), TOPO("Topo");
    companion object { fun from(s: String?) = values().firstOrNull { it.name == s } ?: JALAN }
}

private fun esriSource(name: String, service: String, ext: String, maxZoom: Int) = object : OnlineTileSourceBase(
    name, 0, maxZoom, 256, ext, arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/$service/MapServer/tile/"), "Tiles © Esri",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}
private val esri = esriSource("EsriWorldImagery", "World_Imagery", ".jpg", 19)
private val esriStreet = esriSource("EsriWorldStreet", "World_Street_Map", ".jpg", 19)
private val esriTopo = esriSource("EsriWorldTopo", "World_Topo_Map", ".jpg", 19)
private val esriLabels = esriSource("EsriLabels", "Reference/World_Boundaries_and_Places", ".png", 19)

private fun pinIcon(m: MapView, res: Int, h: Int, glow: Int?): android.graphics.drawable.Drawable {
    val src = android.graphics.BitmapFactory.decodeResource(m.resources, res)
    val w = (src.width * h.toFloat() / src.height).toInt()
    val pin = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
    if (glow == null) return android.graphics.drawable.BitmapDrawable(m.resources, pin)
    val pad = (h * 0.35f).toInt()
    val out = android.graphics.Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(out)
    val blur = android.graphics.Paint().apply { maskFilter = android.graphics.BlurMaskFilter(pad * 0.7f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
    val off = IntArray(2)
    val alpha = pin.extractAlpha(blur, off)
    val gp = android.graphics.Paint().apply { color = glow; colorFilter = android.graphics.PorterDuffColorFilter(glow, android.graphics.PorterDuff.Mode.SRC_IN) }
    repeat(2) { cv.drawBitmap(alpha, (pad + off[0]).toFloat(), (pad + off[1]).toFloat(), gp) }
    cv.drawBitmap(pin, pad.toFloat(), pad.toFloat(), null)
    return android.graphics.drawable.BitmapDrawable(m.resources, out)
}

@Composable
fun OsmMap(
    pins: List<MapPin>,
    satellite: Boolean,
    modifier: Modifier = Modifier,
    style: MapStyle? = null,
    onTap: ((GeoPoint) -> Unit)? = null,
    onPinClick: ((String) -> Unit)? = null,
    fitKey: Any = pins.size,
    route: List<OsmPoint> = emptyList(),
    vehicle: VehicleSkin? = null,
) {
    val ctx = LocalContext.current
    val map = remember {
        MapView(ctx).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(5.0)
            controller.setCenter(OsmPoint(1.5, 127.8)) // Maluku Utara sebagai titik awal
        }
    }
    val labelOverlay = remember {
        org.osmdroid.views.overlay.TilesOverlay(org.osmdroid.tileprovider.MapTileProviderBasic(ctx, esriLabels), ctx).apply {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT; loadingLineColor = android.graphics.Color.TRANSPARENT
        }
    }
    val vMarker = remember { Marker(map).apply { setInfoWindow(null); setOnMarkerClickListener { _, _ -> true }; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) } }
    LaunchedEffect(route, vehicle) {
        val skin = vehicle
        if (skin == null || route.size < 2) return@LaunchedEffect
        val hpx = (map.resources.displayMetrics.density * 34).toInt()
        val src = android.graphics.BitmapFactory.decodeResource(map.resources, skin.res)
        val w = (src.width * hpx.toFloat() / src.height).toInt()
        val normal = android.graphics.Bitmap.createScaledBitmap(src, w, hpx, true)
        val mir = android.graphics.Bitmap.createBitmap(normal, 0, 0, w, hpx, android.graphics.Matrix().apply { preScale(-1f, 1f) }, true)
        val dN = android.graphics.drawable.BitmapDrawable(map.resources, normal); val dM = android.graphics.drawable.BitmapDrawable(map.resources, mir)
        val cum = DoubleArray(route.size)
        for (i in 1 until route.size) cum[i] = cum[i - 1] + route[i - 1].distanceToAsDouble(route[i])
        val total = cum.last(); if (total <= 0.0) return@LaunchedEffect
        val dur = 18_000L
        var east = true; vMarker.icon = if (skin.facesRight) dN else dM
        val start = withFrameNanos { it }; var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (now - last < 45_000_000L) continue
            last = now
            val d = (((now - start) / 1_000_000L) % dur).toDouble() / dur * total
            var i = java.util.Arrays.binarySearch(cum, d); if (i < 0) i = -i - 2
            i = i.coerceIn(0, route.size - 2)
            val seg = cum[i + 1] - cum[i]; val f = if (seg > 0) (d - cum[i]) / seg else 0.0
            val a = route[i]; val b = route[i + 1]
            vMarker.position = OsmPoint(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f)
            val goingEast = b.longitude >= a.longitude
            if (goingEast != east) { east = goingEast; vMarker.icon = if (goingEast == skin.facesRight) dN else dM }
            map.invalidate()
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, map) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) { Lifecycle.Event.ON_RESUME -> map.onResume(); Lifecycle.Event.ON_PAUSE -> map.onPause(); else -> {} }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); map.onPause(); map.onDetach() }
    }
    AndroidView(modifier = modifier, factory = { map }, update = { m ->
        val st = style ?: if (satellite) MapStyle.SATELIT else MapStyle.OSM
        m.setTileSource(when (st) { MapStyle.JALAN -> esriStreet; MapStyle.OSM -> TileSourceFactory.MAPNIK; MapStyle.TOPO -> esriTopo; else -> esri })
        m.overlays.clear()
        if (st == MapStyle.HIBRIDA) m.overlays.add(labelOverlay)
        if (onTap != null) {
            m.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmPoint): Boolean { onTap(GeoPoint(p.latitude, p.longitude, null, "peta")); return true }
                override fun longPressHelper(p: OsmPoint): Boolean = false
            }))
        }
        if (route.size >= 2) {
            val pl = org.osmdroid.views.overlay.Polyline(m)
            pl.setPoints(route)
            pl.outlinePaint.apply {
                color = 0xFF5CD6FF.toInt(); strokeWidth = m.resources.displayMetrics.density * 4f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(28f, 18f), 0f); strokeCap = android.graphics.Paint.Cap.ROUND
            }
            m.overlays.add(pl)
        }
        val h = (m.resources.displayMetrics.density * 40).toInt()
        pins.forEach { pin ->
            val mk = Marker(m)
            mk.position = OsmPoint(pin.geo.lat, pin.geo.lng)
            mk.title = pin.title
            mk.icon = pinIcon(m, pin.kind.res, h, if (ThemeState.fx) pin.kind.glow else null)
            mk.setAnchor(Marker.ANCHOR_CENTER, if (ThemeState.fx) 0.794f else Marker.ANCHOR_BOTTOM)
            mk.setOnMarkerClickListener { marker, _ -> if (onPinClick != null) { onPinClick(pin.id); true } else { marker.showInfoWindow(); true } }
            m.overlays.add(mk)
        }
        if (vehicle != null && route.size >= 2) m.overlays.add(vMarker)
        if (m.tag != fitKey) {
            m.tag = fitKey
            val pts = pins.map { OsmPoint(it.geo.lat, it.geo.lng) }
            m.post {
                when {
                    pts.size == 1 -> { m.controller.setZoom(16.0); m.controller.setCenter(pts[0]) }
                    pts.size > 1 -> m.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), false, 96)
                }
            }
        }
        m.invalidate()
    })
}
