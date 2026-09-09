package expo.modules.fieldagent

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPS plausibility filter and send policy.
 *
 * Deliberately free of any android.* import so it runs — and is tested — on a
 * plain JVM. This is the piece that decides whether an agent appears frozen on
 * the map, so it is the piece that must never be "probably fine".
 */
object Geo {
    /** Beyond this the fix is a cell-tower guess, not a position. */
    const val MAX_ACCURACY_METERS = 100.0

    /** ~216 km/h. Above that on a scooter it is a GPS jump, not a rider. */
    const val MAX_SPEED_MPS = 60.0

    /**
     * After a gap this long (tunnel, dead zone, killed process) the first fix
     * back is necessarily "far away". Rejecting it is what freezes an agent on
     * the map for the rest of the shift.
     */
    const val TUNNEL_GAP_MS = 120_000L

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val DEG_TO_RAD = Math.PI / 180.0

    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val timeMs: Long,
        val accuracyMeters: Double
    )

    enum class Verdict { ACCEPT, REJECT_ACCURACY, REJECT_OUT_OF_ORDER, REJECT_JUMP, REJECT_COORDINATES }

    /** Haversine. Great-circle is plenty at street scale and has no edge cases at the poles. */
    fun distanceMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val dLat = (toLat - fromLat) * DEG_TO_RAD
        val dLon = (toLon - fromLon) * DEG_TO_RAD
        val a = sin(dLat / 2).let { it * it } +
            cos(fromLat * DEG_TO_RAD) * cos(toLat * DEG_TO_RAD) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    fun distanceMeters(from: Fix, to: Fix): Double =
        distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude)

    fun judge(previous: Fix?, next: Fix): Verdict {
        if (next.latitude !in -90.0..90.0 || next.longitude !in -180.0..180.0) return Verdict.REJECT_COORDINATES
        if (next.latitude.isNaN() || next.longitude.isNaN()) return Verdict.REJECT_COORDINATES
        // A non-positive accuracy means "not reported", which is not the same as "bad".
        if (next.accuracyMeters > MAX_ACCURACY_METERS) return Verdict.REJECT_ACCURACY
        if (previous == null) return Verdict.ACCEPT

        val elapsedMs = next.timeMs - previous.timeMs
        if (elapsedMs < 0) return Verdict.REJECT_OUT_OF_ORDER
        if (elapsedMs >= TUNNEL_GAP_MS) return Verdict.ACCEPT

        val distance = distanceMeters(previous, next)
        // Two fixes with the same stamp: only a zero-distance pair is coherent.
        if (elapsedMs == 0L) return if (distance <= previous.accuracyMeters.coerceAtLeast(0.0)) Verdict.ACCEPT else Verdict.REJECT_JUMP

        val speed = distance / (elapsedMs / 1000.0)
        return if (speed > MAX_SPEED_MPS) Verdict.REJECT_JUMP else Verdict.ACCEPT
    }

    fun isPlausible(previous: Fix?, next: Fix): Boolean = judge(previous, next) == Verdict.ACCEPT

    /**
     * Send policy. The heartbeat wins over the distance filter on purpose: a
     * motionless agent that stops reporting is indistinguishable, server-side,
     * from an agent whose phone died.
     */
    fun shouldSend(
        lastSent: Fix?,
        next: Fix,
        distanceFilterMeters: Double,
        heartbeatMs: Long
    ): Boolean {
        if (lastSent == null) return true
        if (next.timeMs - lastSent.timeMs >= heartbeatMs) return true
        return distanceMeters(lastSent, next) >= distanceFilterMeters
    }
}
