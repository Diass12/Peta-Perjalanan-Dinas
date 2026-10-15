package id.tub.dinas.ui.map

import android.graphics.Color as AColor
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

enum class PinKind(val argb: Int, val res: Int) {
    DOK(0xFFE9C23B.toInt(), id.tub.dinas.R.drawable.pin_dok),
    LAP(0xFF14532D.toInt(), id.tub.dinas.R.drawable.pin_lap),
    PICK(0xFFB3261E.toInt(), id.tub.dinas.R.drawable.pin_saya),
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

private fun pinIcon(m: MapView, res: Int, h: Int): android.graphics.drawable.Drawable {
    val src = android.graphics.BitmapFactory.decodeResource(m.resources, res)
    val w = (src.width * h.toFloat() / src.height).toInt()
    return android.graphics.drawable.BitmapDrawable(m.resources, android.graphics.Bitmap.createScaledBitmap(src, w, h, true))
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
            mk.icon = pinIcon(m, pin.kind.res, h)
            mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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
