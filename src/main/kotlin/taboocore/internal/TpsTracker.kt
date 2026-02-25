package taboocore.internal

/**
 * TPS (Ticks Per Second) 跟踪器
 *
 * 每个 tick 调用 [record] 记录时间戳，
 * 通过 [getTps] 计算指定时间窗口内的平均 TPS。
 */
object TpsTracker {

    // 存储最近 1200 个 tick 的时间戳（20 TPS × 60s = 1200 ticks 刚好 1 分钟）
    private val timestamps = LongArray(1200) { 0L }
    private var cursor = 0
    private var recorded = 0
    private var startTime = 0L

    /**
     * 记录当前 tick，每个 tick 调用一次
     */
    fun record() {
        val now = System.currentTimeMillis()
        if (startTime == 0L) startTime = now
        timestamps[cursor] = now
        cursor = (cursor + 1) % timestamps.size
        if (recorded < timestamps.size) recorded++
    }

    /**
     * 计算过去 [seconds] 秒内的平均 TPS（最大值 20.0）
     *
     * 开服初期运行时间不足 [seconds] 秒时，以实际运行时长为分母，
     * 避免显示偏低的异常数值。
     *
     * @param seconds 时间窗口（秒），推荐 1、5、60
     */
    fun getTps(seconds: Int): Double {
        if (seconds <= 0 || recorded == 0) return 20.0
        val now = System.currentTimeMillis()
        val cutoff = now - seconds * 1000L
        var count = 0
        val size = minOf(recorded, timestamps.size)
        for (i in 0 until size) {
            if (timestamps[i] > cutoff) count++
        }
        // 用实际运行时长与请求窗口的较小值做除数，防止开服初期数值偏低
        val effectiveSeconds = minOf(seconds.toDouble(), (now - startTime) / 1000.0)
        if (effectiveSeconds <= 0) return 20.0
        return minOf(20.0, count.toDouble() / effectiveSeconds)
    }
}
