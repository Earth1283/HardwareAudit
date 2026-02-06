package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import kotlin.math.pow
import kotlin.math.sqrt

import io.github.Earth1283.utils.BenchmarkResult
import java.util.concurrent.CompletableFuture

class MsptBenchmark(private val plugin: HardwareAudit) {

    private val mm = MiniMessage.miniMessage()

    fun monitorMspt(durationSeconds: Int): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        val samples = ArrayList<Double>()
        var ticks = 0
        val expectedTicks = durationSeconds * 20
        var lastTickTime = System.nanoTime()

        var taskId = -1

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            val now = System.nanoTime()
            val mspt = (now - lastTickTime) / 1_000_000.0
            
            // Skip the very first delta as it includes the scheduling delay of the first run
            if (ticks > 0) {
                samples.add(mspt)
            }
            lastTickTime = now
            ticks++

            if (ticks > expectedTicks + 1) { // +1 for the warm up tick
                Bukkit.getScheduler().cancelTask(taskId)
                
                // Calculate Stats
                if (samples.isEmpty()) {
                   future.completeExceptionally(RuntimeException("No samples collected"))
                } else {
                    val min = samples.minOrNull() ?: 0.0
                    val max = samples.maxOrNull() ?: 0.0
                    val avg = samples.average()
                    val variance = samples.sumOf { (it - avg).pow(2) } / samples.size
                    val stdDev = sqrt(variance)
                    
                    val minStr = "%.2f".format(min)
                    val maxStr = "%.2f".format(max)
                    val avgStr = "%.2f".format(avg)
                    val stdDevStr = "%.2f".format(stdDev)
                    val remark = io.github.Earth1283.utils.Judgement.getMsptRemark(stdDev)
                    
                    val stdDevColor = if (stdDev < 5.0) "<green>" else if (stdDev < 15.0) "<yellow>" else "<red>"

                    val details = mm.deserialize("""
                        <gradient:#00ff00:#00aaaa><bold>MSPT Analysis Finished!</bold></gradient>
                        <gray>Avg:</gray> <yellow>${avgStr}ms</yellow> <gray>Max:</gray> <red>${maxStr}ms</red> <gray>StdDev:</gray> $stdDevColor${stdDevStr}ms</${if(stdDev<15.0) "yellow" else "red"}>
                        <hover:show_text:'<gray>Standard Deviation measures tick stability. High values = Lag Spikes.</gray>'>[?]</hover>
                    """.trimIndent())
                    
                    future.complete(BenchmarkResult("MSPT", "${avgStr}ms (SD: ${stdDevStr})", remark, details))
                }
            }
        }, 0L, 1L)
        
        return future
    }
}
