package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class RateLimitInterceptor(
    private val permits: Int,
    private val period: Long = 1,
    private val unit: TimeUnit = TimeUnit.SECONDS
) : Interceptor {

    private val requestTimes = DoubleArray(permits)
    private val periodNanos = unit.toNanos(period)

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val now = System.nanoTime()
        val boundary = now - periodNanos

        // Find the oldest request time
        var oldestIndex = 0
        for (i in 1 until permits) {
            if (requestTimes[i] < requestTimes[oldestIndex]) {
                oldestIndex = i
            }
        }

        val oldestTime = requestTimes[oldestIndex]
        if (oldestTime > boundary) {
            val sleepTime = (oldestTime + periodNanos - now).toLong()
            if (sleepTime > 0) {
                TimeUnit.NANOSECONDS.sleep(sleepTime)
            }
        }

        val response = chain.proceed(chain.request())
        requestTimes[oldestIndex] = System.nanoTime().toDouble()
        return response
    }
}
