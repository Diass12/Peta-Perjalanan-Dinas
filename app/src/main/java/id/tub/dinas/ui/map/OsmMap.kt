package id.tub.dinas.ui.map

import android.graphics.Color as AColor
import id.tub.dinas.ui.theme.ThemeState
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

data class MapPin(val id: String, val geo: GeoPoint, val title: String, val kind: PinKind)

private val esri = object : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Tiles © Esri",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

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
    onTap: ((GeoPoint) -> Unit)? = null,
    onPinClick: ((String) -> Unit)? = null,
    fitKey: Any = pins.size,
) {
    val ctx = LocalContext.current
    val map = remember {
        MapView(ctx).apply {
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(5.0)
            controller.setCenter(OsmPoint(1.5, 127.8)) // Maluku Utara sebagai titik awal
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
        m.setTileSource(if (satellite) esri else TileSourceFactory.MAPNIK)
        m.overlays.clear()
        if (onTap != null) {
            m.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmPoint): Boolean { onTap(GeoPoint(p.latitude, p.longitude, null, "peta")); return true }
                override fun longPressHelper(p: OsmPoint): Boolean = false
            }))
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
