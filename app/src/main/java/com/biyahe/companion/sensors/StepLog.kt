package com.biyahe.companion.sensors

/**
 * Remembers recent step-counter readings so the app can answer
 * "how many steps did I walk between time A and time B?".
 *
 * The hardware counter is a running total since the phone rebooted, so a
 * single reading means nothing on its own. Section 6 uses this to fill
 * Trip.stepsBefore (the minutes before Start Trip) and Trip.stepsAfter
 * (the minutes after End Trip).
 */
class StepLog(private val maxAgeMs: Long = 6 * 60 * 60 * 1000L) {

    private data class Entry(val timeMs: Long, val total: Long)

    private val entries = ArrayDeque<Entry>()
    private var rebootOffset = 0L
    private var lastRaw = -1f

    @Synchronized
    fun record(timeMs: Long, rawCumulative: Float) {
        // Counter went backwards = phone rebooted; keep our running total continuous.
        if (lastRaw >= 0f && rawCumulative < lastRaw) rebootOffset += lastRaw.toLong()
        lastRaw = rawCumulative
        entries.addLast(Entry(timeMs, rebootOffset + rawCumulative.toLong()))
        while (entries.size > 1 && timeMs - entries.first().timeMs > maxAgeMs) entries.removeFirst()
    }

    /** Running total at [timeMs]. Before the first reading we only know the first reading itself. */
    @Synchronized
    private fun totalAt(timeMs: Long): Long? =
        entries.lastOrNull { it.timeMs <= timeMs }?.total ?: entries.firstOrNull()?.total

    /** Steps walked between two moments (0 if no readings, or if [toMs] is before [fromMs]). */
    @Synchronized
    fun stepsBetween(fromMs: Long, toMs: Long): Int {
        if (toMs <= fromMs) return 0
        val start = totalAt(fromMs) ?: return 0
        val end = totalAt(toMs) ?: return 0
        return (end - start).coerceAtLeast(0L).toInt()
    }

    @Synchronized
    fun hasData(): Boolean = entries.isNotEmpty()
}
