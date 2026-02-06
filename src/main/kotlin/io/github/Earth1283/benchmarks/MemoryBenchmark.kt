package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CompletableFuture

class MemoryBenchmark(private val plugin: HardwareAudit) {
    private val mm = MiniMessage.miniMessage()

    fun runMemoryTest(): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        val threads = Runtime.getRuntime().availableProcessors()
        
        // Aggressive: Try to allocate larger chunks if possible, but keep it safe-ish.
        // 1GB is a lot for some heap sizes. Let's aim for a total working set that stresses the bus.
        // We'll stick to 32MB chunks per thread but run intensely.
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val workerThreads = ArrayList<Thread>()
            val results = AtomicLong(0)
            val durationSeconds = 5
            val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
            val blockSize = 32 * 1024 * 1024 // 32MB
            
            for (i in 0 until threads) {
                val t = Thread {
                    try {
                        val src = ByteArray(blockSize)
                        val dst = ByteArray(blockSize)
                        var localCopies = 0L
                        
                        while (System.currentTimeMillis() < endTime) {
                            System.arraycopy(src, 0, dst, 0, blockSize)
                            localCopies++
                        }
                        results.addAndGet(localCopies)
                    } catch (e: OutOfMemoryError) {
                        // Ignore, just stop this thread
                    }
                }
                workerThreads.add(t)
                t.start()
            }
            
            for (t in workerThreads) {
                t.join()
            }
            
            val totalBytes = results.get() * blockSize
            val mbPerSec = (totalBytes / 1024.0 / 1024.0) / durationSeconds
            val scoreStr = "%.2f".format(mbPerSec)
            val remark = io.github.Earth1283.utils.Judgement.getMemoryRemark(mbPerSec)
            
            val details = mm.deserialize("""
                <gradient:#00ff00:#00aaaa><bold>RAM Benchmark Finished!</bold></gradient>
                <gray>Throughput:</gray> <#00bfff>${scoreStr} MB/s</#00bfff>
                <hover:show_text:'<gray>Multi-threaded memory copy speed (All Cores).</gray>'>[?]</hover>
            """.trimIndent())
            
            future.complete(BenchmarkResult("RAM", "$scoreStr MB/s", remark, details))
        })
        
        return future
    }
}
