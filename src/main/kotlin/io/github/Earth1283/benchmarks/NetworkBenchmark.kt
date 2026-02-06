package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.io.BufferedInputStream
import java.net.URL
import java.util.concurrent.CompletableFuture

class NetworkBenchmark(private val plugin: HardwareAudit) {

    private val mm = MiniMessage.miniMessage()
    // Switching to Tele2 as Cloudflare is blocking Java/User-Agent requests with 403.
    private val TEST_URL = "http://speedtest.tele2.net/100MB.zip" 
    
    fun runNetworkTest(): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val start = System.currentTimeMillis()
                val url = URL(TEST_URL)
                val conn = url.openConnection()
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                conn.connectTimeout = 5000
                conn.readTimeout = 20000
                
                var bytesRead = 0L
                BufferedInputStream(conn.getInputStream()).use { input ->
                    val buffer = ByteArray(8192)
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                         bytesRead += n
                    }
                }
                
                val end = System.currentTimeMillis()
                val durationMs = end - start
                
                val durationSec = durationMs / 1000.0
                val mbSize = bytesRead / 1024.0 / 1024.0
                val mbps = (mbSize * 8) / durationSec
                
                val scoreStr = "%.2f".format(mbps)
                val remark = io.github.Earth1283.utils.Judgement.getNetworkRemark(mbps)
                
                // Colors
                val color = if (mbps > 100) "<green>" else if (mbps > 20) "<yellow>" else "<red>"
                
                val details = mm.deserialize("""
                    <gradient:#00ff00:#00aaaa><bold>Network Benchmark Finished!</bold></gradient>
                    <gray>Download Speed:</gray> $color${scoreStr} Mbps</reset>
                    <gray>Downloaded:</gray> <white>${"%.1f".format(mbSize)} MB</white> <gray>in</gray> <white>${"%.1f".format(durationSec)}s</white>
                    <hover:show_text:'<gray>Measured using Cloudflare Speed Test (100MB).</gray>'>[?]</hover>
                """.trimIndent())
                
                future.complete(BenchmarkResult("Network", "$scoreStr Mbps", remark, details))
                
            } catch (e: Exception) {
                // If it fails, return error result
                val details = mm.deserialize("<red>X Network Test Failed:</red> <gray>${e.message}</gray>")
                future.complete(BenchmarkResult("Network", "Error", "<red>Connection Failed.</red>", details))
            }
        })

        return future
    }
}
