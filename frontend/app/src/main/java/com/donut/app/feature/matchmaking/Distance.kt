package com.donut.app.feature.matchmaking

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0
private const val METERS_PER_KM = 1000
// Below 1 km the distance is shown in metres, rounded to 50 m
private const val METER_STEP = 50

/**
 * The straight-line (great-circle) distance between two points, by the haversine formula
 * @return kilometres
 */
fun distanceKm(fromLatitude: Double, fromLongitude: Double, toLatitude: Double, toLongitude: Double): Double {
    val dLat = Math.toRadians(toLatitude - fromLatitude)
    val dLon = Math.toRadians(toLongitude - fromLongitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(fromLatitude)) * cos(Math.toRadians(toLatitude)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
}

/**
 * @return e.g. "2.1 km", or "850 m" under 1 km
 */
fun distanceText(km: Double): String =
    if (km < 1) {
        "${((km * METERS_PER_KM / METER_STEP).roundToInt() * METER_STEP).coerceAtLeast(METER_STEP)} m"
    } else {
        "%.1f km".format(km)
    }
