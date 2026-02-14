package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

class MemoryBenchmark(private val plugin: HardwareAudit) {
    private val mm = MiniMessage.miniMessage()

    fun runMemoryTest(): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        val threads = Runtime.getRuntime().availableProcessors()
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val workerThreads = ArrayList<Thread>()
            val throughputPasses = AtomicLong(0)
            val durationSeconds = 15
            val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
            val blockSize = 32 * 1024 * 1024 // 32MB
            
            // 1. THROUGHPUT TEST (Sequential Copy)
            for (i in 0 until threads) {
                val t = Thread {
                    try {
                        val src = ByteArray(blockSize)
                        val dst = ByteArray(blockSize)
                        java.util.Random().nextBytes(src)
                        
                        while (System.currentTimeMillis() < endTime) {
                            System.arraycopy(src, 0, dst, 0, blockSize)
                            throughputPasses.addAndGet(1)
                        }
                    } catch (e: OutOfMemoryError) {}
                }
                workerThreads.add(t)
                t.start()
            }
            
            for (t in workerThreads) t.join()
            
            // 2. LATENCY TEST (Random Access)
            val latencyStart = System.nanoTime()
            val listSize = 1_000_000
            val array = IntArray(listSize) { it }
            array.shuffle() // Randomize indices for a "linked list" like traversal
            
            var current = 0
            for (i in 0 until 10_000_000) {
                current = array[current]
            }
            val latencyEnd = System.nanoTime()
            val latencyNs = (latencyEnd - latencyStart) / 10_000_000.0

            val totalBytes = throughputPasses.get() * blockSize
            val mbPerSec = (totalBytes / 1024.0 / 1024.0) / durationSeconds
            val scoreStr = "%.2f".format(mbPerSec)
            val remark = io.github.Earth1283.utils.Judgement.getMemoryRemark(mbPerSec)
            
            val details = mm.deserialize("""
                <gradient:#00ff00:#00aaaa><bold>Rigorous RAM Benchmark Finished!</bold></gradient>
                <gray>Throughput:</gray> <#00bfff>${scoreStr} MB/s</#00bfff>
                <gray>Est. Latency:</gray> <#ff8c00>%.2f ns</#ff8c00>
                <hover:show_text:'<gray>Throughput: Multi-threaded memory copy speed (All cores). Latency: Average time to access a random memory address (Pointer chasing).</gray>'>[?]</hover>
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
