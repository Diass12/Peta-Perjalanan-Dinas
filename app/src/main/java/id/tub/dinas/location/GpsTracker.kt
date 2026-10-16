package id.tub.dinas.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import id.tub.dinas.domain.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GpsState(
    val running: Boolean = false,
    val fix: GeoPoint? = null,
    val message: String? = null,
)

/** Pelacak GPS: Fused Location bila ada Google Play Services, kalau tidak LocationManager. */
class GpsTracker(context: Context) {
    private val ctx = context.applicationContext
    private val _state = MutableStateFlow(GpsState())
    val state: StateFlow<GpsState> = _state

    private var fusedCb: LocationCallback? = null
    private var lmListener: LocationListener? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun publish(l: Location) {
        val acc = if (l.hasAccuracy()) Math.round(l.accuracy) else null
        _state.value = GpsState(true, GeoPoint(l.latitude, l.longitude, acc, "gps"), null)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (fusedCb != null || lmListener != null) return
        if (!hasPermission()) { _state.value = GpsState(false, null, "Izin lokasi belum diberikan"); return }
        _state.value = GpsState(true, null, "Mencari sinyal GPS…")
        val high = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("gpsHigh", true)
        val usePlay = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
        if (usePlay) {
            val cb = object : LocationCallback() {
                override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { publish(it) } }
            }
            fusedCb = cb
            LocationServices.getFusedLocationProviderClient(ctx)
                .requestLocationUpdates(LocationRequest.Builder(if (high) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, if (high) 1000L else 4000L).build(), cb, Looper.getMainLooper())
                .addOnFailureListener { _state.value = GpsState(false, null, "GPS tidak dapat dimulai") }
        } else {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val l = object : LocationListener {
                override fun onLocationChanged(location: Location) { publish(location) }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) { _state.value = GpsState(true, _state.value.fix, "GPS perangkat mati") }
                @Deprecated("Deprecated in Java")
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            lmListener = l
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, if (high) 1000L else 4000L, 0f, l, Looper.getMainLooper()) }
            catch (e: Exception) { _state.value = GpsState(false, null, "GPS tidak tersedia di perangkat ini") }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        fusedCb?.let { LocationServices.getFusedLocationProviderClient(ctx).removeLocationUpdates(it) }
        fusedCb = null
        lmListener?.let { (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(it) }
        lmListener = null
        _state.value = _state.value.copy(running = false)
    }
}
