package expo.modules.fieldagent

import org.junit.Assert.assertEquals
import org.junit.Test

class ImagesTest {

    @Test
    fun `an image already smaller than the slot is not downscaled`() {
        assertEquals(1, Images.sampleSize(width = 48, height = 48, targetPx = 72))
        assertEquals(1, Images.sampleSize(width = 72, height = 72, targetPx = 72))
    }

    @Test
    fun `a phone photo is cut down to something a 24dp slot can hold`() {
        // 4032x3024 into a 72px slot: 4032/32 = 126, halving again would put the
        // short side at 47, under the target.
        assertEquals(32, Images.sampleSize(width = 4032, height = 3024, targetPx = 72))
    }

    @Test
    fun `it stops on the short side, not the long one`() {
        // A banner: halving past 2 would leave the height at 40, below target.
        assertEquals(2, Images.sampleSize(width = 2000, height = 160, targetPx = 72))
    }

    @Test
    fun `the result is always a power of two`() {
        for (width in listOf(100, 333, 1001, 1920, 5000)) {
            val sample = Images.sampleSize(width, width, 72)
            assertEquals(0, sample and (sample - 1))
        }
    }

    @Test
    fun `unreadable bounds never produce a divide by zero`() {
        // BitmapFactory reports -1 for a file it could not parse.
        assertEquals(1, Images.sampleSize(width = -1, height = -1, targetPx = 72))
        assertEquals(1, Images.sampleSize(width = 0, height = 0, targetPx = 72))
    }

    @Test
    fun `a nonsense target is refused rather than looped on`() {
        assertEquals(1, Images.sampleSize(width = 4000, height = 4000, targetPx = 0))
        assertEquals(1, Images.sampleSize(width = 4000, height = 4000, targetPx = -10))
    }
}
