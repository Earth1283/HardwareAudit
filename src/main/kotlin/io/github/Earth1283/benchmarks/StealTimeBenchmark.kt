package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import java.io.File
import java.io.InputStreamReader
import java.util.Scanner
import java.util.concurrent.CompletableFuture
import org.bukkit.Bukkit
import kotlin.math.abs

import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage

class StealTimeBenchmark(private val plugin: HardwareAudit) {

    private val si = SystemInfo()
    private val hal = si.hardware
    private val mm = MiniMessage.miniMessage()

    fun measureStealTime(durationSeconds: Int): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            // 1. Initial measures
            val prevTicks = hal.processor.systemCpuLoadTicks
            
            // Jitter Test
            var jitterAccumulator = 0.0
            val startTime = System.currentTimeMillis()
            var iterations = 0
            while (System.currentTimeMillis() < startTime + (durationSeconds * 1000)) {
                val startSleep = System.nanoTime()
                try {
                    Thread.sleep(50)
                } catch (e: InterruptedException) {
                    // ignore
                }
                val endSleep = System.nanoTime()
                val durationMs = (endSleep - startSleep) / 1_000_000.0
                val drift = durationMs - 50.0
                if (drift > 0) {
                    jitterAccumulator += drift
                }
                iterations++
            }
            
            val avgJitter = if (iterations > 0) jitterAccumulator / iterations else 0.0

            // OSHI Final
            val currentTicks = hal.processor.systemCpuLoadTicks
            var totalDiff = 0L
            val diffs = LongArray(currentTicks.size)
            for (i in currentTicks.indices) {
                diffs[i] = currentTicks[i] - prevTicks[i]
                totalDiff += diffs[i]
            }
            
            val stealDiff = diffs[CentralProcessor.TickType.STEAL.ordinal]
            val oshiSteal = if (totalDiff > 0) (stealDiff.toDouble() / totalDiff) * 100.0 else 0.0

            // Formatting
            val stealStr = "%.2f".format(oshiSteal)
            val jitterStr = "%.2f".format(avgJitter)
            val remark = io.github.Earth1283.utils.Judgement.getStealRemark(oshiSteal)
            
            val details = mm.deserialize("""
                <gradient:#00ff00:#00aaaa><bold>Steal Time Analysis Finished!</bold></gradient>
                <gray>Steal:</gray> <#ff4500>${stealStr}%</#ff4500> <gray>Jitter:</gray> <#ffd700>${jitterStr}ms</#ffd700>
                <hover:show_text:'<gray>High steal means "noisy neighbors". High jitter means laggy thread scheduling.</gray>'>[?]</hover>
            """.trimIndent())

            future.complete(BenchmarkResult("Steal Time", "$stealStr% (Jitter: ${jitterStr}ms)", remark, details))
        })

        return future
    }

}
