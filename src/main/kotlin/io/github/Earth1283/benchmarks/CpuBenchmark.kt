package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

class CpuBenchmark(private val plugin: HardwareAudit) {
    private val mm = MiniMessage.miniMessage()

    fun runCpuTest(durationSeconds: Int): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
            var passes = 0L
            val size = 100_000 // Calculate primes up to 100k
            
            // Prime Sieve Loop
            while (System.currentTimeMillis() < endTime) {
                val flags = java.util.BitSet(size + 1)
                flags.set(0, size + 1) // Set all to true
                flags.set(0, false)
                flags.set(1, false)
                
                var count = 0
                for (i in 2..Math.sqrt(size.toDouble()).toInt()) {
                    if (flags.get(i)) {
                        for (k in i * i..size step i) {
                            flags.set(k, false)
                        }
                    }
                }
                passes++
            }
            
            // Passes per second
            val scoreVal = passes / durationSeconds.toDouble()
            val scoreStr = "%.2f".format(scoreVal)
            val remark = io.github.Earth1283.utils.Judgement.getCpuRemark(scoreVal)
            
            val details = mm.deserialize("""
                <gradient:#00ff00:#00aaaa><bold>CPU Benchmark Finished!</bold></gradient>
                <gray>Score:</gray> <#ffd700>${scoreStr} ops/sec</#ffd700>
                <hover:show_text:'<gray>Higher is better. Based on prime number calculation speed.</gray>'>[?]</hover>
            """.trimIndent())

            future.complete(BenchmarkResult("CPU", "$scoreStr ops/s", remark, details))
        })
        
        return future
    }
}
