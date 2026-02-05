package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

class CpuBenchmark(private val plugin: HardwareAudit) {

    fun runCpuTest(durationSeconds: Int): CompletableFuture<Double> {
        val future = CompletableFuture<Double>()
        
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
                
                // Keep the CPU busy counting (optional, but ensures we touched the memory)
                // for (i in 2..size) { if (flags.get(i)) count++ }
                
                passes++
            }
            
            // Passes per second
            val score = passes / durationSeconds.toDouble()
            future.complete(score)
        })
        
        return future
    }
}
