package com.pwa.offline

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class ActionLocationMeta(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null
)

object ActionLocationCapture {
    private const val REQUEST_TIMEOUT_MS = 4000L
    private const val STALE_LOCATION_MS = 120000L

    @SuppressLint("MissingPermission")
    suspend fun captureBestEffort(context: Context): ActionLocationMeta? = withContext(Dispatchers.Main) {
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = enabledProviders(locationManager)
        if (providers.isEmpty()) return@withContext null

        val lastKnownLocation = providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull(Location::getTime)

        if (lastKnownLocation != null && System.currentTimeMillis() - lastKnownLocation.time <= STALE_LOCATION_MS) {
            return@withContext lastKnownLocation.toMeta()
        }

        val provider = when {
            providers.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            providers.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> providers.first()
        }

        val currentLocation = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            suspendCoroutine<Location?> { continuation ->
                val executor = ContextCompat.getMainExecutor(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.getCurrentLocation(
                        provider,
                        CancellationSignal(),
                        executor
                    ) { location ->
                        continuation.resume(location)
                    }
                } else {
                    getCurrentLocationCompat(
                        context = context,
                        locationManager = locationManager,
                        provider = provider,
                        onLocation = { location -> continuation.resume(location) }
                    )
                }
            }
        }

        (currentLocation ?: lastKnownLocation)?.toMeta()
    }

    private fun Location.toMeta(): ActionLocationMeta {
        return ActionLocationMeta(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null
        )
    }

    fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return false
        return enabledProviders(locationManager).isNotEmpty()
    }

    private fun enabledProviders(locationManager: LocationManager): List<String> {
        return buildList {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                add(LocationManager.PASSIVE_PROVIDER)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentLocationCompat(
        context: Context,
        locationManager: LocationManager,
        provider: String,
        onLocation: (Location?) -> Unit
    ) {
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            CancellationSignal(),
            ContextCompat.getMainExecutor(context),
            onLocation
        )
    }
}
