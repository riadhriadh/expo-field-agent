package expo.modules.fieldagent

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeTest {

    @Test
    fun `full level pins the stream to the device maximum`() {
        assertEquals(15, Volume.target(current = 0, max = 15, level = 1.0))
        assertEquals(15, Volume.target(current = 7, max = 15, level = 1.0))
    }

    @Test
    fun `a partial level raises a silenced phone to that share`() {
        // Phone on silent, alarm at 0, host asked for 70 percent of 15.
        assertEquals(11, Volume.target(current = 0, max = 15, level = 0.7))
    }

    @Test
    fun `it never quietens someone who set their alarm louder`() {
        // The whole point: this is a floor. 70 percent of 15 is 11, and the
        // user is at 14 — they keep 14.
        assertEquals(14, Volume.target(current = 14, max = 15, level = 0.7))
    }

    @Test
    fun `level zero leaves the stream exactly where the user left it`() {
        assertEquals(3, Volume.target(current = 3, max = 15, level = 0.0))
        assertEquals(0, Volume.target(current = 0, max = 15, level = 0.0))
    }

    @Test
    fun `out of range levels are clamped rather than trusted`() {
        assertEquals(15, Volume.target(current = 2, max = 15, level = 4.2))
        assertEquals(2, Volume.target(current = 2, max = 15, level = -1.0))
    }

    @Test
    fun `a stream with no range is left alone`() {
        // Some manufacturer builds report 0 for streams they have disabled;
        // computing a share of zero would silently mute the alert.
        assertEquals(5, Volume.target(current = 5, max = 0, level = 1.0))
        assertEquals(5, Volume.target(current = 5, max = -3, level = 1.0))
    }

    @Test
    fun `a nonsense level is ignored instead of throwing`() {
        assertEquals(4, Volume.target(current = 4, max = 15, level = Double.NaN))
        assertEquals(4, Volume.target(current = 4, max = 15, level = Double.POSITIVE_INFINITY))
    }

    @Test
    fun `a current above the maximum is not trusted either`() {
        // Reported louder than the maximum happens on a few skins; the result
        // still has to be a level the platform will accept.
        assertEquals(15, Volume.target(current = 99, max = 15, level = 0.5))
    }
}
