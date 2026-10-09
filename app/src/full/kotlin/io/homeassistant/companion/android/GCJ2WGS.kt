package io.homeassistant.companion.android

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Converts GCJ-02 coordinates, as used by Chinese mapping providers such as AMap,
 * into WGS-84 GPS coordinates.
 */
object GCJ2WGS {
    private const val PI = 3.14159265358979324

    /**
     * Transforms a GCJ-02 coordinate into a WGS-84 coordinate.
     *
     * @param lat latitude in GCJ-02
     * @param lon longitude in GCJ-02
     * @return map with the transformed `lat` and `lon` values
     */
    fun delta(lat: Double, lon: Double): HashMap<String, Double> {
        val a = 6378245.0 // Krasovsky ellipsoid semi-major axis
        val ee = 0.00669342162296594323 // Krasovsky ellipsoid first eccentricity squared
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / (a * (1 - ee) / (magic * sqrtMagic) * PI)
        dLon = dLon * 180.0 / (a / sqrtMagic * cos(radLat) * PI)
        val hm = HashMap<String, Double>()
        hm["lat"] = lat - dLat
        hm["lon"] = lon - dLon
        return hm
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret =
            -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }
}
