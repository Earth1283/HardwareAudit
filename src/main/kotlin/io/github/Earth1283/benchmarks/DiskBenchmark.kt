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
            if (!plugin.dataFolder.exists()) {
                plugin.dataFolder.mkdirs()
            }

            // 4GB File for rigorous testing
            val sizeBytes = 4L * 1024 * 1024 * 1024 // 4GB
            val bufferSize = 64 * 1024 // 64KB buffer for faster sequential write
            val buffer = ByteArray(bufferSize)
            java.util.Random().nextBytes(buffer) // Fill with random data to avoid compression cheating
            
            // 1. SEQUENTIAL WRITE TEST
            val writeStart = System.currentTimeMillis()
            try {
                java.io.FileOutputStream(tempFile).use { fos ->
                    var written = 0L
                    while (written < sizeBytes) {
                        fos.write(buffer)
                        written += bufferSize
                    }
                    fos.fd.sync() // Ensure it's on disk
                }
            } catch (e: Exception) {
                future.completeExceptionally(e)
                return@Runnable
            }
            val writeEnd = System.currentTimeMillis()
            val writeTimeSec = (writeEnd - writeStart) / 1000.0
            val writeSpeed = (sizeBytes / 1024.0 / 1024.0) / writeTimeSec

            // 2. RANDOM I/O TEST (Simulate database/plugin load)
            val randomIoStart = System.currentTimeMillis()
            var randomOps = 0
            try {
                java.io.RandomAccessFile(tempFile, "rw").use { raf ->
                    val smallBuffer = ByteArray(4096) // 4KB blocks
                    for (i in 0 until 1000) {
                        val pos = (Math.random() * (sizeBytes - 4096)).toLong()
                        raf.seek(pos)
                        raf.read(smallBuffer)
                        raf.seek(pos)
                        raf.write(smallBuffer)
                        randomOps++
                    }
                    raf.getFD().sync()
                }
            } catch (e: Exception) {}
            val randomIoEnd = System.currentTimeMillis()
            val randomIoTimeMs = (randomIoEnd - randomIoStart).toDouble()
            val iops = (randomOps / (randomIoTimeMs / 1000.0))

            // 3. SEQUENTIAL READ TEST
            val readStart = System.currentTimeMillis()
            try {
                java.io.FileInputStream(tempFile).use { fis ->
                    val readBuffer = ByteArray(bufferSize)
                    while (fis.read(readBuffer) != -1) { }
                }
            } catch (e: Exception) { }
            val readEnd = System.currentTimeMillis()
            val readTimeSec = (readEnd - readStart) / 1000.0
            val readSpeed = (sizeBytes / 1024.0 / 1024.0) / readTimeSec

            // Cleanup
            tempFile.delete()

            val wScore = "%.2f".format(writeSpeed)
            val rScore = "%.2f".format(readSpeed)
            val iopsScore = "%.0f".format(iops)
            
            val remark = io.github.Earth1283.utils.Judgement.getDiskRemark(writeSpeed)
            
            val details = mm.deserialize("""
                <gradient:#00ff00:#00aaaa><bold>Rigorous Disk Benchmark Finished!</bold></gradient>
                <gray>Seq Write:</gray> <#ff4500>${wScore} MB/s</#ff4500>
                <gray>Seq Read:</gray> <#32cd32>${rScore} MB/s</#32cd32>
                <gray>Random I/O:</gray> <#ffd700>${iopsScore} IOPS (4KB RW)</#ffd700>
                <hover:show_text:'<gray>Sequential 4GB Read/Write + 1000 random 4KB operations. IOPS indicates how well your host handles databases/plugins.</gray>'>[?]</hover>
            """.trimIndent())
            
            future.complete(BenchmarkResult("Disk", "$wScore MB/s (W)", remark, details))
        })
        
        return future
    }
}
