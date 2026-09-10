package expo.modules.fieldagent

import kotlin.math.roundToInt

/**
 * What level the alarm stream should be pushed to for one alert.
 *
 * Pure, and deliberately separate from [Alerts]: this is the arithmetic that
 * decides how loud a sleeping agent's phone gets, and it is worth testing on a
 * JVM rather than discovering on a rider's handset at 3am.
 */
object Volume {

    /**
     * A floor, never a ceiling.
     *
     * `level` is the share of the device maximum the alert wants. If the user
     * already keeps their alarm louder than that, they keep it: an agent who set
     * their alarm to the top did it on purpose, and a "raise" that quietened
     * them would be the opposite of the feature.
     */
    fun target(current: Int, max: Int, level: Double): Int {
        // A stream with no range (or a manufacturer returning nonsense) is not
        // something to compute against.
        if (max <= 0) return current
        if (!level.isFinite()) return current

        val wanted = (max * level.coerceIn(0.0, 1.0)).roundToInt().coerceIn(0, max)
        return maxOf(current.coerceIn(0, max), wanted)
    }
}
