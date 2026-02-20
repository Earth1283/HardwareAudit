package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import io.github.Earth1283.utils.BenchmarkResult
import java.util.concurrent.CompletableFuture
import kotlin.math.pow
import kotlin.math.sqrt

class MsptBenchmark(private val plugin: HardwareAudit) {

    private val mm = MiniMessage.miniMessage()

    fun monitorMspt(durationSeconds: Int): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        val samples = ArrayList<Double>()
        var ticks = 0
        val expectedTicks = durationSeconds * 20
        var lastTickTime = System.nanoTime()

        val task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            val now = System.nanoTime()
            val mspt = (now - lastTickTime) / 1_000_000.0
            
            if (ticks > 0) {
                samples.add(mspt)
            }
            lastTickTime = now
            ticks++

            if (ticks > expectedTicks) {
                // Done - results will be processed in a moment
            }
        }, 0L, 1L)

        // Schedule another task to finish and process results after duration
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            task.cancel()
            
            if (samples.isEmpty()) {
                future.completeExceptionally(RuntimeException("No samples collected"))
            } else {
                val avg = samples.average()
                val variance = samples.sumOf { (it - avg).pow(2) } / samples.size
                val stdDev = sqrt(variance)
                
                val max = samples.maxOrNull() ?: 0.0
                val avgStr = "%.2f".format(avg)
                val maxStr = "%.2f".format(max)
                val stdDevStr = "%.2f".format(stdDev)
                val remark = io.github.Earth1283.utils.Judgement.getMsptRemark(stdDev)
                
                val stdDevColor = if (stdDev < 5.0) "<green>" else if (stdDev < 15.0) "<yellow>" else "<red>"

                val details = mm.deserialize("""
                    <gradient:#00ff00:#00aaaa><bold>MSPT Analysis Finished!</bold></gradient>
                    <gray>Avg:</gray> <yellow>${avgStr}ms</yellow> <gray>Max:</gray> <red>${maxStr}ms</red> <gray>StdDev:</gray> $stdDevColor${stdDevStr}ms</${if(stdDev < 15.0) "yellow" else "red"}>
                    <hover:show_text:'<gray>Standard Deviation measures tick stability. High values = Lag Spikes.</gray>'>[?]</hover>
                """.trimIndent())
                
                future.complete(BenchmarkResult("MSPT", "${avgStr}ms (SD: ${stdDevStr})", remark, details))
            }
        }, (expectedTicks + 2).toLong())
        
        return future
    }
}
