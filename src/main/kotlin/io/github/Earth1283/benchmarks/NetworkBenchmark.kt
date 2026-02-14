package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class NetworkBenchmark(private val plugin: HardwareAudit) {

    private val mm = MiniMessage.miniMessage()
    private val TEST_URL = "http://speedtest.tele2.net/100MB.zip" 
    
    fun runNetworkTest(): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val threadCount = 4
                val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
                val totalBytesRead = java.util.concurrent.atomic.AtomicLong(0)
                val start = System.currentTimeMillis()

                val tasks = (0 until threadCount).map {
                    executor.submit {
                        try {
                            val url = URL(TEST_URL)
                            val conn = url.openConnection()
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (HardwareAudit)")
                            conn.connectTimeout = 5000
                            conn.readTimeout = 15000
                            
                            conn.getInputStream().use { input ->
                                val buffer = ByteArray(16384)
                                var n: Int
                                while (input.read(buffer).also { n = it } != -1) {
                                    totalBytesRead.addAndGet(n.toLong())
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }

                for (task in tasks) task.get(30, java.util.concurrent.TimeUnit.SECONDS)
                executor.shutdown()
                
                val end = System.currentTimeMillis()
                val durationSec = (end - start) / 1000.0
                val mbSize = totalBytesRead.get() / 1024.0 / 1024.0
                val mbps = (mbSize * 8) / durationSec
                
                val scoreStr = "%.2f".format(mbps)
                val remark = io.github.Earth1283.utils.Judgement.getNetworkRemark(mbps)
                
                val color = if (mbps > 500) "<green>" else if (mbps > 100) "<yellow>" else "<red>"
                
                val details = mm.deserialize("""
                    <gradient:#00ff00:#00aaaa><bold>Rigorous Network Benchmark Finished!</bold></gradient>
                    <gray>Aggregated Speed:</gray> $color${scoreStr} Mbps</reset>
                    <gray>Downloaded:</gray> <white>${"%.1f".format(mbSize)} MB</white> <gray>in</gray> <white>${"%.1f".format(durationSec)}s</white>
                    <gray>Streams:</gray> <white>$threadCount</white>
                    <hover:show_text:'<gray>Uses $threadCount concurrent TCP streams to saturate high-bandwidth connections.</gray>'>[?]</hover>
                """.trimIndent())
                
                future.complete(BenchmarkResult("Network", "$scoreStr Mbps", remark, details))
                
            } catch (e: Exception) {
                val details = mm.deserialize("<red>X Network Test Failed:</red> <gray>${e.message}</gray>")
                future.complete(BenchmarkResult("Network", "Error", "<red>Connection Failed.</red>", details))
            }
        })

        return future
    }
}
