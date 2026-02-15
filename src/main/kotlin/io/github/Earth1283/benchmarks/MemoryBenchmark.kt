package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class MemoryBenchmark(private val plugin: HardwareAudit) {
    private val mm = MiniMessage.miniMessage()

    fun runMemoryTest(durationSeconds: Int = 15): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        val threads = Runtime.getRuntime().availableProcessors()
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val workerThreads = ArrayList<Thread>()
            val throughputPasses = AtomicLong(0)
            val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
            
            // Dynamic Block Size Calculation
            // Target: 512MB per thread to bust L3 cache and saturate controller
            val maxMemory = Runtime.getRuntime().maxMemory()
            val safeMemory = (maxMemory * 0.85).toLong() // Use up to 85% of heap for maximum abuse
            val safePerThread = safeMemory / threads / 2 // 2 arrays per thread (src, dst)
            val targetBlockSize = 512L * 1024 * 1024 // 512MB
            
            // Ensure at least 1MB, max 512MB
            var blockSizeLong = min(targetBlockSize, safePerThread)
            if (blockSizeLong < 1024 * 1024) blockSizeLong = 1024 * 1024 
            val blockSize = blockSizeLong.toInt()
            
            // 1. THROUGHPUT TEST (Sequential Copy - Violent Bandwidth Saturation)
            // We'll also use multiple arrays per thread to ensure we're really pushing the allocator and memory bus
            for (i in 0 until threads) {
                val t = Thread {
                    try {
                        val src = ByteArray(blockSize)
                        val dst = ByteArray(blockSize)
                        java.util.Random().nextBytes(src)
                        
                        while (System.currentTimeMillis() < endTime) {
                            System.arraycopy(src, 0, dst, 0, blockSize)
                            // Occasionally "leak" or touch more memory if possible to increase pressure
                            throughputPasses.addAndGet(1)
                        }
                    } catch (e: OutOfMemoryError) {
                    }
                }
                workerThreads.add(t)
                t.start()
            }
            
            for (t in workerThreads) t.join()
            
            // 2. LATENCY TEST (Random Access / Pointer Chasing)
            // Increased to 16M ints (64MB) to ensure we hit main memory latency
            val latencyStart = System.nanoTime()
            val listSize = 16_000_000 
            val array = IntArray(listSize) { it }
            array.shuffle() // Randomize indices
            
            var current = 0
            // Traverse 10M times
            for (i in 0 until 10_000_000) {
                current = array[current]
            }
            val latencyEnd = System.nanoTime()
            val latencyNs = (latencyEnd - latencyStart) / 10_000_000.0

            // Calc results
            val totalBytes = throughputPasses.get() * blockSize.toLong()
            val mbPerSec = (totalBytes / 1024.0 / 1024.0) / durationSeconds
            val scoreStr = "%.2f".format(mbPerSec)
            val remark = io.github.Earth1283.utils.Judgement.getMemoryRemark(mbPerSec)
            
            val details = mm.deserialize("""
                <gradient:#00ff00:#00aaaa><bold>Rigorous RAM Benchmark Finished!</bold></gradient>
                <gray>Block Size:</gray> <white>${blockSize / 1024 / 1024} MB</white>
                <gray>Throughput:</gray> <#00bfff>${scoreStr} MB/s</#00bfff>
                <gray>Est. Latency:</gray> <#ff8c00>%.2f ns</#ff8c00>
                <hover:show_text:'<gray>Violent Throughput: ${blockSize / 1024 / 1024}MB blocks per thread (Main Memory Saturation). Latency: 64MB Random Walk.</gray>'>[?]</hover>
            """.trimIndent().format(latencyNs))
            
            future.complete(BenchmarkResult("RAM", "$scoreStr MB/s", remark, details))
        })
        
        return future
    }
}

// Extension to shuffle IntArray
fun IntArray.shuffle() {
    val rnd = java.util.Random()
    for (i in size - 1 downTo 1) {
        val index = rnd.nextInt(i + 1)
        val a = this[index]
        this[index] = this[i]
        this[i] = a
    }
}
