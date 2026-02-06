package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File
import java.util.concurrent.CompletableFuture

class DiskBenchmark(private val plugin: HardwareAudit) {

    private val mm = MiniMessage.miniMessage()

    fun runDiskTest(): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val tempFile = File(plugin.dataFolder, "disk_test.tmp")
            // Create data folder if not exists
            if (!plugin.dataFolder.exists()) {
                plugin.dataFolder.mkdirs()
            }

            // 2GB File for aggressive testing
            val sizeBytes = 2L * 1024 * 1024 * 1024 // 2GB
            val bufferSize = 8192 // 8KB buffer
            val buffer = ByteArray(bufferSize)
            
            // WRITE TEST
            val writeStart = System.currentTimeMillis()
            try {
                java.io.FileOutputStream(tempFile).use { fos ->
                    var written = 0L
                    while (written < sizeBytes) {
                        fos.write(buffer)
                        written += bufferSize
                    }
                }
            } catch (e: Exception) {
                future.completeExceptionally(e)
                return@Runnable
            }
            val writeEnd = System.currentTimeMillis()
            val writeTimeSec = (writeEnd - writeStart) / 1000.0
            val writeSpeed = (sizeBytes / 1024.0 / 1024.0) / writeTimeSec

            // READ TEST
            val readStart = System.currentTimeMillis()
            try {
                java.io.FileInputStream(tempFile).use { fis ->
                    var read = 0
                    while (read != -1) {
                         read = fis.read(buffer)
                    }
                }
            } catch (e: Exception) {
               // ignore read error
            }
            val readEnd = System.currentTimeMillis()
            val readTimeSec = (readEnd - readStart) / 1000.0
            val readSpeed = (sizeBytes / 1024.0 / 1024.0) / readTimeSec

            // Cleanup
            tempFile.delete()

            // Format results
            val wScore = "%.2f".format(writeSpeed)
            val rScore = "%.2f".format(readSpeed)
            
            // Judgement based on write speed primarily
            val remark = io.github.Earth1283.utils.Judgement.getDiskRemark(writeSpeed)
            
            val details = mm.deserialize("""
                <gradient:#00ff00:#00aaaa><bold>Disk I/O Benchmark Finished!</bold></gradient>
                <gray>Write:</gray> <#ff4500>${wScore} MB/s</#ff4500> <gray>Read:</gray> <#32cd32>${rScore} MB/s</#32cd32>
                <hover:show_text:'<gray>Sequential Write/Read speed of a 2GB file.</gray>'>[?]</hover>
            """.trimIndent())
            
            future.complete(BenchmarkResult("Disk", "$wScore MB/s (Write)", remark, details))
        })
        
        return future
    }
}
