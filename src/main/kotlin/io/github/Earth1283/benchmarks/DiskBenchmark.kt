package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Random

class DiskBenchmark(private val plugin: HardwareAudit) {

    private val mm = MiniMessage.miniMessage()

    fun runDiskTest(sender: CommandSender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            sender.sendMessage(mm.deserialize("<yellow>Running Disk I/O Benchmark (Approx 512MB)...</yellow>"))

            if (!plugin.dataFolder.exists()) {
                plugin.dataFolder.mkdirs()
            }
            val file = File(plugin.dataFolder, "disk_test.tmp")
            val sizeBytes = 512 * 1024 * 1024 // 512 MB
            val bufferSize = 64 * 1024 // 64 KB buffer

            // Generate some random data first to avoid FS compression cheating if possible
            // Actually filling 512MB array is heavy on RAM. Let's reuse a small buffer.
            val buffer = ByteArray(bufferSize)
            Random().nextBytes(buffer)

            // --- WRITE TEST ---
            val startWrite = System.nanoTime()
            try {
                FileOutputStream(file).use { fos ->
                    var written = 0
                    while (written < sizeBytes) {
                        fos.write(buffer)
                        written += bufferSize
                    }
                    fos.fd.sync() // Force flush to disk
                }
            } catch (e: Exception) {
                sender.sendMessage(mm.deserialize("<red>Write failed: ${e.message}</red>"))
                return@Runnable
            }
            val endWrite = System.nanoTime()
            val writeSeconds = (endWrite - startWrite) / 1_000_000_000.0
            val writeSpeedMb = (sizeBytes / 1024.0 / 1024.0) / writeSeconds

            // --- READ TEST ---
            // Try to drop cache if possible? Java can't easily force OS to drop page cache.
            // We just read it back.
            
            val startRead = System.nanoTime()
            try {
                FileInputStream(file).use { fis ->
                    val readBuffer = ByteArray(bufferSize)
                    while (fis.read(readBuffer) != -1) {
                        // consume
                    }
                }
            } catch (e: Exception) {
                sender.sendMessage(mm.deserialize("<red>Read failed: ${e.message}</red>"))
                file.delete()
                return@Runnable
            }
            val endRead = System.nanoTime()
            val readSeconds = (endRead - startRead) / 1_000_000_000.0
            val readSpeedMb = (sizeBytes / 1024.0 / 1024.0) / readSeconds

            // Cleanup
            file.delete()

            // Report
            sender.sendMessage(mm.deserialize("<green>Disk Benchmark Finished!</green>"))
            sender.sendMessage(mm.deserialize("<gray>Write Speed:</gray> <gold>${"%.2f".format(writeSpeedMb)} MB/s</gold>"))
            sender.sendMessage(mm.deserialize("<gray>Read Speed:</gray> <gold>${"%.2f".format(readSpeedMb)} MB/s</gold>"))
            
            if (writeSpeedMb > 1000) {
                 sender.sendMessage(mm.deserialize("<gray>Verdict:</gray> <green>Likely NVMe SSD</green>"))
            } else if (writeSpeedMb > 400) {
                 sender.sendMessage(mm.deserialize("<gray>Verdict:</gray> <yellow>Likely SATA SSD</yellow>"))
            } else {
                 sender.sendMessage(mm.deserialize("<gray>Verdict:</gray> <red>Likely HDD or Slow Storage</red>"))
            }
            
            sender.sendMessage(mm.deserialize(io.github.Earth1283.utils.Judgement.getDiskRemark(writeSpeedMb)))
        })
    }
}
