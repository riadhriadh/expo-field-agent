package expo.modules.fieldagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    private fun fix(
        lat: Double,
        lon: Double,
        timeMs: Long,
        accuracy: Double = 10.0,
        elapsedNanos: Long = 0L,
        isMock: Boolean = false
    ) = Geo.Fix(lat, lon, timeMs, accuracy, elapsedNanos, isMock)

    private fun Long.secondsAsNanos(): Long = this * 1_000_000_000L

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
    fun `consecutive rejects for longer than the real elapsed time eventually accept`() {
        // Mock-location stall: next.timeMs is frozen at the previous fix's stamp,
        // so the provider-clock tunnel exemption never fires on its own — but the
        // app's own clock says two real minutes passed, which must still win.
        val frozen = fix(34.7406, 10.7603, tunis.timeMs)
        assertEquals(
            Geo.Verdict.ACCEPT,
            Geo.judge(tunis, frozen, realElapsedMsSinceAccepted = Geo.TUNNEL_GAP_MS)
        )
    }

    @Test
    fun `a provider clock running backward no longer locks out the exemption`() {
        // next.timeMs before previous.timeMs (NTP correction, provider handoff)
        // used to hit REJECT_OUT_OF_ORDER before the tunnel exemption ever ran.
        val backward = fix(34.7406, 10.7603, tunis.timeMs - 5_000)
        assertEquals(
            Geo.Verdict.ACCEPT,
            Geo.judge(tunis, backward, realElapsedMsSinceAccepted = Geo.TUNNEL_GAP_MS)
        )
    }

    @Test
    fun `a spoofed jump inside a real 120s window is still rejected`() {
        // The app's own clock has not actually reached 120s yet, so the
        // exemption must not fire early - otherwise MAX_SPEED_MPS is pointless.
        val sfax = fix(34.7406, 10.7603, 10_000)
        assertEquals(
            Geo.Verdict.REJECT_JUMP,
            Geo.judge(tunis, sfax, realElapsedMsSinceAccepted = Geo.TUNNEL_GAP_MS - 1)
        )
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

    @Test
    fun `a mock fix is accepted when the host did not ask to reject them`() {
        val spoofed = fix(36.8, 10.18, 1_000, isMock = true)
        assertEquals(Geo.Verdict.ACCEPT, Geo.judge(null, spoofed))
    }

    @Test
    fun `a mock fix is rejected when the host asked for it`() {
        val spoofed = fix(36.8, 10.18, 1_000, isMock = true)
        assertEquals(Geo.Verdict.REJECT_MOCK, Geo.judge(null, spoofed, rejectMock = true))
    }

    @Test
    fun `the mock verdict wins over an impossible coordinate`() {
        // Ordering matters: the operator must be told the device is spoofing,
        // not that one of the spoofed values happened to be out of range.
        val spoofed = fix(91.0, 10.18, 1_000, isMock = true)
        assertEquals(Geo.Verdict.REJECT_MOCK, Geo.judge(null, spoofed, rejectMock = true))
    }

    @Test
    fun `a stricter accuracy ceiling rejects what the default accepts`() {
        val vague = fix(36.8, 10.18, 1_000, accuracy = 50.0)
        assertEquals(Geo.Verdict.ACCEPT, Geo.judge(null, vague))
        assertEquals(Geo.Verdict.REJECT_ACCURACY, Geo.judge(null, vague, maxAccuracyMeters = 30.0))
    }

    @Test
    fun `a wider speed ceiling accepts what the default rejects`() {
        // ~654 m in 10 s is ~65 m/s: above the stock 60, below a configured 70.
        val fast = fix(36.805079, 10.1806, 10_000)
        assertEquals(Geo.Verdict.REJECT_JUMP, Geo.judge(tunis, fast))
        assertEquals(Geo.Verdict.ACCEPT, Geo.judge(tunis, fast, maxSpeedMps = 70.0))
    }

    @Test
    fun `a forward wall clock jump cannot buy the tunnel exemption`() {
        // The whole point of the monotonic clock: "Settings > set date to next
        // hour" used to turn any teleport into a legitimate post-tunnel fix.
        val here = fix(36.7992, 10.1806, 0, elapsedNanos = 1L.secondsAsNanos())
        val sfax = fix(34.7406, 10.7603, 300_000, elapsedNanos = 11L.secondsAsNanos())
        assertEquals(Geo.Verdict.REJECT_JUMP, Geo.judge(here, sfax))
    }

    @Test
    fun `a backward wall clock is not out of order when the monotonic clock moved forward`() {
        // NTP correction during a shift: timeMs goes back 5 s while the device
        // uptime kept counting. Dropping this fix would freeze the agent.
        val here = fix(36.7992, 10.1806, 100_000, elapsedNanos = 5L.secondsAsNanos())
        val moved = fix(36.8010, 10.1806, 95_000, elapsedNanos = 20L.secondsAsNanos())
        assertEquals(Geo.Verdict.ACCEPT, Geo.judge(here, moved))
    }

    @Test
    fun `an unknown elapsed realtime on either side falls back to the wall clock`() {
        val here = fix(36.7992, 10.1806, 0, elapsedNanos = 1L.secondsAsNanos())
        val sfax = fix(34.7406, 10.7603, 300_000, elapsedNanos = 11L.secondsAsNanos())
        assertEquals(Geo.Verdict.ACCEPT, Geo.judge(here.copy(elapsedRealtimeNanos = 0L), sfax))
        assertEquals(Geo.Verdict.ACCEPT, Geo.judge(here, sfax.copy(elapsedRealtimeNanos = 0L)))
    }

    @Test
    fun `the odometer ignores the first fix`() {
        assertEquals(0.0, Geo.odometerStep(null, tunis), 0.0001)
    }

    @Test
    fun `the odometer ignores a step buried in the accuracy noise`() {
        // A parked scooter must not bill kilometres overnight.
        val jitter = fix(36.79925, 10.18063, 20_000, accuracy = 20.0)
        assertEquals(0.0, Geo.odometerStep(tunis, jitter), 0.0001)
    }

    @Test
    fun `the odometer ignores a fix above the accuracy ceiling`() {
        val vague = fix(36.8010, 10.1806, 20_000, accuracy = 150.0)
        assertEquals(0.0, Geo.odometerStep(tunis, vague), 0.0001)
    }

    @Test
    fun `the odometer counts a real hundred metre step`() {
        val moved = fix(36.80010, 10.1806, 20_000)
        assertEquals(100.0, Geo.odometerStep(tunis, moved), 5.0)
    }

    @Test
    fun `nothing has been accepted yet, so nothing is stale`() {
        // First minutes of a shift: the heartbeat has no position to hold back,
        // and calling that stale would silence the very first fix.
        assertFalse(Geo.heartbeatIsStale(0L, 1_000_000L, 120_000L))
    }

    @Test
    fun `a position exactly on the bound is still worth sending`() {
        val bound = Geo.MIN_HEARTBEAT_STALENESS_MS
        assertFalse(Geo.heartbeatIsStale(1_000_000L, 1_000_000L + bound, 60_000L))
        assertTrue(Geo.heartbeatIsStale(1_000_000L, 1_000_000L + bound + 1L, 60_000L))
    }

    @Test
    fun `a fast heartbeat does not shorten the bound below five minutes`() {
        // 4 x 30 s is 2 min. Cutting off there would mute a rider stopped at a
        // red light, which is exactly the case the heartbeat exists for.
        assertFalse(Geo.heartbeatIsStale(1L, 1L + 299_000L, 30_000L))
        assertTrue(Geo.heartbeatIsStale(1L, 1L + 301_000L, 30_000L))
    }

    @Test
    fun `a slow heartbeat stretches the bound to four of its own periods`() {
        // 10 min between beats: the floor would declare a position stale before
        // the next one was even due.
        val heartbeatMs = 600_000L
        assertFalse(Geo.heartbeatIsStale(1L, 1L + 4 * heartbeatMs, heartbeatMs))
        assertTrue(Geo.heartbeatIsStale(1L, 1L + 4 * heartbeatMs + 1L, heartbeatMs))
    }
}
