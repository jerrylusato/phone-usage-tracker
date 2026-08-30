package com.jeremiah.phoneusage.data

/**
 * Rules for turning the platform's usage buckets into one day's per-app totals.
 *
 * Android keeps daily-resolution buckets for only about a week; beyond that it answers with
 * weekly, monthly and yearly buckets instead. Those coarser buckets report a whole week (or
 * month) of usage, and a naive per-day query happily records that as a single day — which is
 * how a week of 55 hours ends up looking like 260. Bucket span is the reliable discriminator.
 */
object DailyTotals {

    /** A daily bucket never spans much more than a day; weekly and monthly ones dwarf this. */
    const val MAX_BUCKET_SPAN_MS = 26L * 60 * 60 * 1000

    /** Bucket rotation does not land exactly on local midnight, so allow some drift. */
    const val START_SLACK_MS = 2L * 60 * 60 * 1000

    const val MAX_APP_MS = 24L * 60 * 60 * 1000

    /** Foreground time is exclusive, so a day cannot legitimately exceed this once summed. */
    const val MAX_DAY_MS = 26L * 60 * 60 * 1000

    fun acceptsBucket(
        bucketStart: Long,
        bucketEnd: Long,
        dayStart: Long,
        dayEnd: Long,
    ): Boolean {
        if (bucketEnd - bucketStart > MAX_BUCKET_SPAN_MS) return false
        return bucketStart >= dayStart - START_SLACK_MS && bucketStart < dayEnd
    }

    /** No app can be in the foreground for longer than the window being measured. */
    fun cap(ms: Long, windowMs: Long): Long = ms.coerceAtMost(minOf(windowMs, MAX_APP_MS))

    /** Detects days recorded by earlier versions from coarse buckets. */
    fun isImplausible(totals: Map<String, Long>): Boolean =
        totals.values.sum() > MAX_DAY_MS || totals.values.any { it > MAX_APP_MS }
}
