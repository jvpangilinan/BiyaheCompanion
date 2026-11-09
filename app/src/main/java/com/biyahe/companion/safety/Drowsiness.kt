package com.biyahe.companion.safety

// Eye watch rules. Pure Kotlin (no Android classes), so they are unit-tested.
//
// "Openness" is 0..1 per camera frame (ML Kit's eye-open probability, both eyes averaged),
// or null when no face / no eyes are visible in that frame.

/** What the eye watch noticed. */
enum class EyeEvent {
    /** Eyes shut for too long in one go: alarm. */
    MICROSLEEP,
    /** Eyes mostly closed or drooping too often over the last minute: spoken warning. */
    DROWSY,
    /** Eyes open again after the alarm: stop the alarm. */
    AWAKE,
    /** No face in view for a while: the phone may have moved. */
    FACE_LOST
}

/** How strict the eye watch is (Safety tab chips). */
enum class EyeSensitivity(val label: String, val microsleepMs: Long, val perclosLimit: Double, val droopLimit: Double) {
    GENTLE("Gentle", 2_000L, 0.20, 0.60),
    NORMAL("Normal", 1_500L, 0.15, 0.50),
    STRICT("Strict", 1_000L, 0.10, 0.40);

    companion object {
        fun fromLevel(level: Int) = entries.getOrElse(level - 1) { NORMAL }
    }
}

/** Openness from the two eyes; either can be missing. */
fun eyeOpenness(left: Float?, right: Float?): Float? = when {
    left != null && right != null -> (left + right) / 2f
    else -> left ?: right
}

/**
 * Learns how open YOUR eyes normally are (everyone's differ, and ML Kit rarely says 1.0).
 * Feed it a few seconds of frames while the driver looks at the road as usual.
 */
class EyeCalibration(private val minSamples: Int = 20) {
    private val samples = mutableListOf<Float>()

    fun add(openness: Float?) { if (openness != null) samples += openness }

    val count: Int get() = samples.size

    /**
     * The normal open level (median, so blinks don't pull it down), or null if there is not
     * enough data or the eyes never looked clearly open (sunglasses, too dark).
     */
    fun result(): Float? {
        if (samples.size < minSamples) return null
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        return if (median < MIN_BASELINE) null else median
    }

    fun reset() = samples.clear()

    companion object {
        /** Below this the camera can't tell open from closed reliably. */
        const val MIN_BASELINE = 0.3f
    }
}

/**
 * Drowsiness from eye openness, relative to the calibrated [baseline]:
 *
 *  - Closed  = under [closedRatio] of normal. Drooping = under [droopRatio] of normal.
 *  - MICROSLEEP: closed without a break for [EyeSensitivity.microsleepMs]. A blink (0.1-0.4 s)
 *    never gets there. The alarm stays on until the eyes are open again for [awakeMs].
 *  - DROWSY (PERCLOS, the standard drowsiness measure): over the last [windowMs], closed more
 *    than [EyeSensitivity.perclosLimit] of the time, OR drooping more than
 *    [EyeSensitivity.droopLimit] of the time. Needs [minWindowMs] of data first; at most every
 *    [drowsyCooldownMs].
 *  - FACE_LOST: no face for [faceLostMs] (glancing at mirrors is much shorter). At most every
 *    [faceLostCooldownMs]. Frames without a face are left out of the PERCLOS numbers.
 */
class DrowsinessDetector(
    baseline: Float,
    private val sensitivity: EyeSensitivity = EyeSensitivity.NORMAL,
    closedRatio: Float = 0.35f,
    droopRatio: Float = 0.6f,
    private val awakeMs: Long = 1_000L,
    private val windowMs: Long = 60_000L,
    private val minWindowMs: Long = 20_000L,
    private val minSamples: Int = 30,
    private val drowsyCooldownMs: Long = 2 * 60_000L,
    private val faceLostMs: Long = 20_000L,
    private val faceLostCooldownMs: Long = 5 * 60_000L
) {
    val baseline: Float = baseline.coerceIn(EyeCalibration.MIN_BASELINE, 1f)
    private val closedBelow = this.baseline * closedRatio
    private val droopBelow = this.baseline * droopRatio

    private data class Sample(val t: Long, val closed: Boolean, val droopy: Boolean)
    private val window = ArrayDeque<Sample>()

    private var closedSince: Long? = null
    private var lastClosedAt: Long? = null
    private var openSince: Long? = null
    private var lastFaceAt: Long? = null
    private var firstFrameAt: Long? = null
    private var lastDrowsyAt: Long? = null
    private var lastFaceLostAt: Long? = null

    /** True from MICROSLEEP until AWAKE (or [dismissAlarm]). */
    var alarmOn = false
        private set

    /** Last frame's openness, for the screen. */
    var lastOpenness: Float? = null
        private set

    /** Share of the last minute with eyes closed (0..1), for the screen. */
    val perclos: Double get() = if (window.isEmpty()) 0.0 else window.count { it.closed }.toDouble() / window.size

    /** Share of the last minute with eyes drooping (includes closed). */
    val droop: Double get() = if (window.isEmpty()) 0.0 else window.count { it.droopy }.toDouble() / window.size

    fun faceVisible(t: Long, withinMs: Long = 2_000L): Boolean = lastFaceAt?.let { t - it <= withinMs } == true

    /** The driver tapped the alarm away. */
    fun dismissAlarm() { alarmOn = false; closedSince = null; openSince = null }

    fun update(t: Long, openness: Float?): EyeEvent? {
        if (firstFrameAt == null) firstFrameAt = t
        lastOpenness = openness
        while (window.isNotEmpty() && t - window.first().t > windowMs) window.removeFirst()

        if (openness == null) {
            // A head turn hides the eyes for a moment; only forget a closure after a real gap.
            if (lastClosedAt?.let { t - it > GAP_MS } != false) closedSince = null
            openSince = null
            val since = lastFaceAt ?: firstFrameAt!!
            if (t - since >= faceLostMs && (lastFaceLostAt == null || t - lastFaceLostAt!! >= faceLostCooldownMs)) {
                lastFaceLostAt = t
                return EyeEvent.FACE_LOST
            }
            return null
        }

        lastFaceAt = t
        val closed = openness < closedBelow
        val droopy = openness < droopBelow
        window.addLast(Sample(t, closed, droopy))

        if (closed) {
            if (closedSince == null || lastClosedAt?.let { t - it > GAP_MS } == true) closedSince = t
            lastClosedAt = t
            openSince = null
        } else {
            closedSince = null
            if (!droopy) { if (openSince == null) openSince = t } else openSince = null
        }

        if (alarmOn) {
            val open = openSince
            if (open != null && t - open >= awakeMs) {
                alarmOn = false
                openSince = null
                return EyeEvent.AWAKE
            }
            return null
        }

        val cs = closedSince
        if (cs != null && t - cs >= sensitivity.microsleepMs) {
            alarmOn = true
            closedSince = null
            lastDrowsyAt = t      // no extra "drowsy" warning straight after the alarm
            return EyeEvent.MICROSLEEP
        }

        val enoughData = window.size >= minSamples && t - window.first().t >= minWindowMs
        val cooled = lastDrowsyAt?.let { t - it >= drowsyCooldownMs } != false
        if (enoughData && cooled && (perclos >= sensitivity.perclosLimit || droop >= sensitivity.droopLimit)) {
            lastDrowsyAt = t
            return EyeEvent.DROWSY
        }
        return null
    }

    private companion object {
        const val GAP_MS = 600L
    }
}
