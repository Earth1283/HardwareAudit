package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import io.github.Earth1283.utils.Judgement
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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
                runSieve(size)
                passes++
            }
            
            // Passes per second
            val scoreVal = passes / durationSeconds.toDouble()
            val scoreStr = "%.2f".format(scoreVal)
            val remark = Judgement.getCpuRemark(scoreVal)
            
            val details = mm.deserialize("""
                <gradient:#00ff00:#00aaaa><bold>Single-Threaded CPU Benchmark</bold></gradient>
                <gray>Score:</gray> <#ffd700>${scoreStr} ops/sec</#ffd700>
                <hover:show_text:'<gray>Measures single-core performance using a Prime Sieve (100k). Critical for Minecraft main thread performance.</gray>'>[?]</hover>
            """.trimIndent())

            future.complete(BenchmarkResult("CPU (ST)", "$scoreStr ops/s", remark, details))
        })
        
        return future
    }

    fun runMultiCpuTest(durationSeconds: Int): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        val cores = Runtime.getRuntime().availableProcessors()
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val executor = Executors.newFixedThreadPool(cores)
            val totalPasses = AtomicLong(0)
            val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
            val size = 100_000

            for (i in 0 until cores) {
                executor.submit {
                    while (System.currentTimeMillis() < endTime) {
                        runSieve(size)
                        totalPasses.incrementAndGet()
                    }
                }
            }

            executor.shutdown()
            try {
                executor.awaitTermination(durationSeconds + 5L, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                executor.shutdownNow()
            }

            val scoreVal = totalPasses.get() / durationSeconds.toDouble()
            val scoreStr = "%.2f".format(scoreVal)
            val perCoreScore = scoreVal / cores
            val remark = Judgement.getMultiCpuRemark(scoreVal, cores)
            
            val details = mm.deserialize("""
                <gradient:#ff5555:#ffaa00><bold>Multi-Threaded CPU Benchmark</bold></gradient>
                <gray>Total Score:</gray> <#ffd700>${scoreStr} ops/sec</#ffd700>
                <gray>Cores utilized:</gray> <white>$cores</white>
                <gray>Per-core avg:</gray> <white>%.2f ops/sec</white>
                <hover:show_text:'<gray>Saturates all $cores logical cores. Tests multi-threaded performance, thermal stability, and host core-claims.</gray>'>[?]</hover>
            """.trimIndent().format(perCoreScore))

            future.complete(BenchmarkResult("CPU (MT)", "$scoreStr ops/s", remark, details))
        })
        
        return future
    }

    private fun runSieve(size: Int) {
        val flags = java.util.BitSet(size + 1)
        flags.set(0, size + 1) // Set all to true
        flags.set(0, false)
        flags.set(1, false)
        
        for (i in 2..sqrt(size.toDouble()).toInt()) {
            if (flags.get(i)) {
                for (k in i * i..size step i) {
                    flags.set(k, false)
                }
            }
        }
    }
}
