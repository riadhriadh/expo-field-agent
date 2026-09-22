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

    /**
     * @param elapsedRealtimeNanos Device uptime at acquisition. 0 means
     * "unknown" — a fix built before this field existed, or a provider that
     * does not report it — and the wall clock is used instead.
     */
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val timeMs: Long,
        val accuracyMeters: Double,
        val elapsedRealtimeNanos: Long = 0L,
        val isMock: Boolean = false
    )

    enum class Verdict { ACCEPT, REJECT_ACCURACY, REJECT_OUT_OF_ORDER, REJECT_JUMP, REJECT_COORDINATES, REJECT_MOCK }

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

    /**
     * @param realElapsedMsSinceAccepted Wall-clock time this process itself has
     * measured since [previous] was accepted (0 when there is no previous fix,
     * where it is unused anyway). [Fix.timeMs] comes from the location
     * provider and, under mock-location route playback, can freeze, replay, or
     * run backward while lat/lng keeps changing — starving the tunnel
     * exemption below and locking an agent frozen until process death. This
     * clock is stamped by the app itself and cannot be fooled the same way, so
     * it overrides both the out-of-order guard and the jump guard, not just
     * one of them.
     *
     * Mock detection comes first: an operator told "bad coordinates" about a
     * device that is openly spoofing would chase the wrong problem.
     */
    fun judge(
        previous: Fix?,
        next: Fix,
        realElapsedMsSinceAccepted: Long = 0L,
        maxAccuracyMeters: Double = MAX_ACCURACY_METERS,
        maxSpeedMps: Double = MAX_SPEED_MPS,
        rejectMock: Boolean = false
    ): Verdict {
        if (rejectMock && next.isMock) return Verdict.REJECT_MOCK
        if (next.latitude !in -90.0..90.0 || next.longitude !in -180.0..180.0) return Verdict.REJECT_COORDINATES
        if (next.latitude.isNaN() || next.longitude.isNaN()) return Verdict.REJECT_COORDINATES
        // A non-positive accuracy means "not reported", which is not the same as "bad".
        if (next.accuracyMeters > maxAccuracyMeters) return Verdict.REJECT_ACCURACY
        if (previous == null) return Verdict.ACCEPT
        if (realElapsedMsSinceAccepted >= TUNNEL_GAP_MS) return Verdict.ACCEPT

        // Device uptime cannot be moved by the user, by NTP or by a timezone
        // change, so when both fixes carry it a forward wall-clock jump can no
        // longer buy the tunnel exemption and smuggle a teleport through.
        val elapsedMs = if (previous.elapsedRealtimeNanos != 0L && next.elapsedRealtimeNanos != 0L) {
            (next.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000L
        } else {
            next.timeMs - previous.timeMs
        }
        if (elapsedMs < 0) return Verdict.REJECT_OUT_OF_ORDER
        if (elapsedMs >= TUNNEL_GAP_MS) return Verdict.ACCEPT

        val distance = distanceMeters(previous, next)
        // Two fixes with the same stamp: only a zero-distance pair is coherent.
        if (elapsedMs == 0L) return if (distance <= previous.accuracyMeters.coerceAtLeast(0.0)) Verdict.ACCEPT else Verdict.REJECT_JUMP

        val speed = distance / (elapsedMs / 1000.0)
        return if (speed > maxSpeedMps) Verdict.REJECT_JUMP else Verdict.ACCEPT
    }

    /**
     * Distance to add to the odometer, 0 when the step is not trustworthy.
     *
     * A step shorter than the worse of the two accuracies is indistinguishable
     * from GPS noise: counting it makes a scooter parked overnight bill tens of
     * kilometres by morning.
     */
    fun odometerStep(previous: Fix?, next: Fix, maxAccuracyMeters: Double = MAX_ACCURACY_METERS): Double {
        if (previous == null) return 0.0
        if (previous.accuracyMeters > maxAccuracyMeters || next.accuracyMeters > maxAccuracyMeters) return 0.0
        val distance = distanceMeters(previous, next)
        return if (distance < previous.accuracyMeters.coerceAtLeast(next.accuracyMeters)) 0.0 else distance
    }

    fun isPlausible(previous: Fix?, next: Fix): Boolean = judge(previous, next) == Verdict.ACCEPT

    /**
     * Send policy. The heartbeat wins over the distance filter on purpose: a
     * motionless agent that stops reporting is indistinguishable, server-side,
     * from an agent whose phone died.
     */
    /**
     * Whether the heartbeat must stay silent rather than re-send the last known
     * position under a fresh timestamp.
     *
     * The heartbeat exists so a motionless rider is not mistaken for a dead
     * phone, and it deliberately restamps an old fix to say so. Unbounded, that
     * same behaviour republishes where a phone used to be as where it is: a
     * rider who lost GPS in an underground car park forty minutes ago keeps
     * appearing on the map at the entrance. That is not a stale point, it is a
     * fabricated one, and a dispatcher routing on it sends someone to an empty
     * street. Past the bound, saying nothing is the honest answer.
     *
     * @param acceptedAtMs when this process accepted the fix, never the fix's own
     * clock — a provider timestamp is exactly what cannot be trusted here.
     */
    fun heartbeatIsStale(acceptedAtMs: Long, nowMs: Long, heartbeatMs: Long): Boolean {
        // Never accepted anything yet: there is nothing to call stale.
        if (acceptedAtMs <= 0L) return false
        val limit = maxOf(heartbeatMs * 4, MIN_HEARTBEAT_STALENESS_MS)
        return nowMs - acceptedAtMs > limit
    }

    /** Floor for the staleness bound, so a fast heartbeat does not make it trigger-happy. */
    const val MIN_HEARTBEAT_STALENESS_MS = 300_000L

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
