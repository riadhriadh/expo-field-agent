package expo.modules.fieldagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    private fun fix(lat: Double, lon: Double, timeMs: Long, accuracy: Double = 10.0) =
        Geo.Fix(lat, lon, timeMs, accuracy)

    // Tunis: avenue Habib Bourguiba -> Bab Bhar, roughly 500 m apart.
    private val tunis = fix(36.7992, 10.1806, 0)

    @Test
    fun `distance matches a known pair within one percent`() {
        val measured = Geo.distanceMeters(36.8065, 10.1815, 36.7992, 10.1806)
        assertEquals(813.0, measured, 20.0)
    }

    @Test
    fun `identical coordinates are zero metres apart`() {
        assertEquals(0.0, Geo.distanceMeters(36.8, 10.18, 36.8, 10.18), 0.0001)
    }

    @Test
    fun `first fix is always accepted`() {
        assertTrue(Geo.isPlausible(null, tunis))
    }

    @Test
    fun `a fix with absurd accuracy is rejected`() {
        val vague = fix(36.8, 10.18, 1_000, accuracy = 2_000.0)
        assertEquals(Geo.Verdict.REJECT_ACCURACY, Geo.judge(null, vague))
    }

    @Test
    fun `an unreported accuracy is not treated as a bad one`() {
        assertTrue(Geo.isPlausible(null, fix(36.8, 10.18, 0, accuracy = -1.0)))
    }

    @Test
    fun `a fix older than the previous one is rejected`() {
        val older = fix(36.7992, 10.1806, -1_000)
        assertEquals(Geo.Verdict.REJECT_OUT_OF_ORDER, Geo.judge(tunis, older))
    }

    @Test
    fun `teleporting across the country in ten seconds is rejected`() {
        // Tunis -> Sfax, ~235 km, in 10 s.
        val sfax = fix(34.7406, 10.7603, 10_000)
        assertEquals(Geo.Verdict.REJECT_JUMP, Geo.judge(tunis, sfax))
    }

    @Test
    fun `a plausible scooter move is accepted`() {
        // ~200 m in 15 s is 48 km/h.
        val next = fix(36.8010, 10.1806, 15_000)
        assertTrue(Geo.isPlausible(tunis, next))
    }

    @Test
    fun `the first fix after a tunnel is accepted even though it is far`() {
        val sfax = fix(34.7406, 10.7603, Geo.TUNNEL_GAP_MS)
        assertEquals(Geo.Verdict.ACCEPT, Geo.judge(tunis, sfax))
    }

    @Test
    fun `a jump one millisecond before the tunnel threshold is still rejected`() {
        val sfax = fix(34.7406, 10.7603, Geo.TUNNEL_GAP_MS - 1)
        assertEquals(Geo.Verdict.REJECT_JUMP, Geo.judge(tunis, sfax))
    }

    @Test
    fun `coordinates outside the sphere are rejected`() {
        assertEquals(Geo.Verdict.REJECT_COORDINATES, Geo.judge(null, fix(91.0, 10.0, 0)))
        assertEquals(Geo.Verdict.REJECT_COORDINATES, Geo.judge(null, fix(36.0, 181.0, 0)))
    }

    @Test
    fun `duplicated timestamps only pass when the position did not move`() {
        assertTrue(Geo.isPlausible(tunis, fix(36.7992, 10.1806, 0)))
        assertFalse(Geo.isPlausible(tunis, fix(34.7406, 10.7603, 0)))
    }

    @Test
    fun `the distance filter holds back a motionless agent`() {
        val still = fix(36.79921, 10.18061, 20_000)
        assertFalse(Geo.shouldSend(tunis, still, distanceFilterMeters = 15.0, heartbeatMs = 120_000))
    }

    @Test
    fun `the heartbeat overrides the distance filter`() {
        val still = fix(36.79921, 10.18061, 130_000)
        assertTrue(Geo.shouldSend(tunis, still, distanceFilterMeters = 15.0, heartbeatMs = 120_000))
    }

    @Test
    fun `a move past the filter is sent`() {
        val moved = fix(36.8010, 10.1806, 15_000)
        assertTrue(Geo.shouldSend(tunis, moved, distanceFilterMeters = 15.0, heartbeatMs = 120_000))
    }

    @Test
    fun `nothing sent yet means send`() {
        assertTrue(Geo.shouldSend(null, tunis, distanceFilterMeters = 15.0, heartbeatMs = 120_000))
    }
}
