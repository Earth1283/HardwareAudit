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
import kotlin.math.tan

class CpuBenchmark(private val plugin: HardwareAudit) {
    private val mm = MiniMessage.miniMessage()

    fun runCpuTest(durationSeconds: Int): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
            var passes = 0L
            val size = 1_000_000 // Violent: Primes up to 1M
            
            // Prime Sieve Loop + FPU Stress
            while (System.currentTimeMillis() < endTime) {
                runViolentOp(size)
                passes++
            }
            
            // Passes per second
            val scoreVal = passes / durationSeconds.toDouble()
            val scoreStr = "%.2f".format(scoreVal)
            val remark = Judgement.getCpuRemark(scoreVal)
            
            val details = mm.deserialize("""
                <gradient:#00ff00:#00aaaa><bold>Single-Threaded Violent CPU Benchmark</bold></gradient>
                <gray>Score:</gray> <#ffd700>${scoreStr} HeavyOps/s</#ffd700>
                <hover:show_text:'<gray>Measures single-core performance using a 1M Prime Sieve + FPU stress (Math.tan/fma). Heavily stresses cache and pipeline.</gray>'>[?]</hover>
            """.trimIndent())

            future.complete(BenchmarkResult("CPU (ST)", "$scoreStr HeavyOps/s", remark, details))
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
            val size = 1_000_000 // Violent: Primes up to 1M

            for (i in 0 until cores) {
                executor.submit {
                    while (System.currentTimeMillis() < endTime) {
                        runViolentOp(size)
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
                <gradient:#ff5555:#ffaa00><bold>Multi-Threaded Violent CPU Benchmark</bold></gradient>
                <gray>Total Score:</gray> <#ffd700>${scoreStr} HeavyOps/s</#ffd700>
                <gray>Cores utilized:</gray> <white>$cores</white>
                <gray>Per-core avg:</gray> <white>%.2f HeavyOps/s</white>
                <hover:show_text:'<gray>Saturates all $cores logical cores with violent FPU/Cache workload. Tests thermal throttling and max power draw.</gray>'>[?]</hover>
            """.trimIndent().format(perCoreScore))

            future.complete(BenchmarkResult("CPU (MT)", "$scoreStr HeavyOps/s", remark, details))
        })
        
        return future
    }

    private fun runViolentOp(size: Int) {
        // 1. Large Prime Sieve (Memory/Cache Intensive)
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

        // 2. FPU/AVX Stress (Compute Intensive)
        // Math.fma (Fused Multiply Add) is often intrinsic and uses AVX units
        var acc = 0.0
        for (j in 0 until 1000) {
            acc = Math.fma(j.toDouble(), tan(j.toDouble()), acc)
        }
        // Consume result to avoid dead code elimination (though volatile or return might be better, this is usually enough in random loops)
        if (acc.isNaN()) {
            // Unlikely, just to use the value
            print("NaN")
        }
    }
}
