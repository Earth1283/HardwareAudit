package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

class MemoryBenchmark(private val plugin: HardwareAudit) {

    private val mm = MiniMessage.miniMessage()

    fun runMemoryTest(sender: CommandSender) {
        val threads = Runtime.getRuntime().availableProcessors()
        val durationSeconds = 5
        
        sender.sendMessage(mm.deserialize("<yellow>Running Multi-Threaded Memory Benchmark ($threads threads, 5 seconds)...</yellow>"))

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val workerThreads = ArrayList<Thread>()
            val results = java.util.concurrent.atomic.AtomicLong(0)
            val blockSize = 32 * 1024 * 1024 // 32 MB chunks
            
            val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
            
            for (i in 0 until threads) {
                val t = Thread {
                    val src = ByteArray(blockSize)
                    val dst = ByteArray(blockSize)
                    var localCopies = 0L
                    
                    while (System.currentTimeMillis() < endTime) {
                        System.arraycopy(src, 0, dst, 0, blockSize)
                        localCopies++
                    }
                    results.addAndGet(localCopies)
                }
                workerThreads.add(t)
                t.start()
            }
            
            // Wait for all to finish
            for (t in workerThreads) {
                try { t.join() } catch (e: InterruptedException) { e.printStackTrace() }
            }
            
            val totalCopies = results.get()
            val totalMb = totalCopies * 32.0 // 32 MB per copy
            val speedMbS = totalMb / durationSeconds

            sender.sendMessage(mm.deserialize("<green>Memory Benchmark Finished!</green>"))
            sender.sendMessage(mm.deserialize("<gray>Total Throughput:</gray> <gold>${"%.2f".format(speedMbS)} MB/s</gold>"))
            sender.sendMessage(mm.deserialize(io.github.Earth1283.utils.Judgement.getMemoryRemark(speedMbS)))
            sender.sendMessage(mm.deserialize("<gray>Note:</gray> <italic>Aggressive multi-threaded test (Saturating $threads cores).</italic>"))
        })
    }
}
