package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DiskBenchmark(private val plugin: HardwareAudit) {

    private val mm = MiniMessage.miniMessage()
    private var nativeBinary: File? = null

    init {
        setupNative()
    }

    private fun setupNative() {
        try {
            val binDir = File(plugin.dataFolder, "bin")
            if (!binDir.exists()) binDir.mkdirs()
            
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            
            // Determine resource name for pre-compiled binary
            val resourceName = when {
                os.contains("win") -> "bin/disk_party_windows.exe"
                os.contains("mac") || os.contains("darwin") -> "bin/disk_party_darwin"
                os.contains("linux") && (arch.contains("amd64") || arch.contains("x86_64")) -> "bin/disk_party_linux_x64"
                os.contains("linux") && arch.contains("aarch64") -> "bin/disk_party_linux_arm64"
                else -> null
            }

            val binaryFile = File(binDir, if (os.contains("win")) "disk_party.exe" else "disk_party")

            // 1. Try to extract pre-compiled binary
            if (resourceName != null && plugin.getResource(resourceName) != null) {
                plugin.getResource(resourceName)?.use { input ->
                    binaryFile.outputStream().use { output -> input.copyTo(output) }
                }
                binaryFile.setExecutable(true)
                nativeBinary = binaryFile
                plugin.logger.info("Using pre-compiled Disk Destroyer for $os/$arch. Ready to party!")
                return
            }

            // 2. Fallback: Compile from source if gcc is present
            val cFile = File(binDir, "disk_party.c")
            plugin.getResource("disk_party.c")?.use { input ->
                cFile.outputStream().use { output -> input.copyTo(output) }
            }

            val process = Runtime.getRuntime().exec(arrayOf("gcc", cFile.absolutePath, "-o", binaryFile.absolutePath, "-lpthread"))
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
                binaryFile.setExecutable(true)
                nativeBinary = binaryFile
                plugin.logger.info("Native Disk Destroyer compiled from source! Ready to party.")
            } else {
                plugin.logger.warning("No pre-compiled binary found and compilation failed. Falling back to boring Kotlin.")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error during native setup: ${e.message}")
        }
    }

    fun runDiskTest(sizeGb: Int = 4): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        val threads = minOf(Runtime.getRuntime().availableProcessors(), 32)

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            if (nativeBinary != null) {
                runNative(sizeGb, 0, threads, future)
            } else {
                runKotlin(sizeGb, threads, future)
            }
        })
        
        return future
    }

    private fun runNative(sizeGb: Int, duration: Int, threads: Int, future: CompletableFuture<BenchmarkResult>) {
        try {
            val tempFolder = File(plugin.dataFolder, "disk_party_temp")
            if (!tempFolder.exists()) tempFolder.mkdirs()

            val process = Runtime.getRuntime().exec(arrayOf(
                nativeBinary!!.absolutePath,
                tempFolder.absolutePath,
                sizeGb.toString(),
                duration.toString(),
                threads.toString()
            ))

            val reader = process.inputStream.bufferedReader()
            var totalBytes = 0L
            
            reader.forEachLine { line ->
                if (line.startsWith("PARTY_RESULT:")) {
                    totalBytes = line.substringAfter(":").toLong()
                } else if (line.isNotEmpty()) {
                    plugin.logger.info("[DiskParty] $line")
                }
            }

            process.waitFor()
            tempFolder.deleteRecursively()

            val effectiveDuration = if (duration > 0) duration.toDouble() else 15.0 // Estimation for ST
            val mbPerSec = (totalBytes / 1024.0 / 1024.0) / effectiveDuration
            val scoreStr = "%.2f".format(mbPerSec)
            val remark = io.github.Earth1283.utils.Judgement.getDiskRemark(mbPerSec)

            val details = mm.deserialize("""
                <gradient:#ff00ff:#00ffff><bold>NATIVE DISK DESTRUCTION COMPLETE!</bold></gradient>
                <gray>Mode:</gray> <white>C-Daemon (Pre-compiled/Native)</white>
                <gray>Throughput:</gray> <#ff4500>${scoreStr} MB/s</#ff4500>
                <gray>Threads:</gray> <white>$threads</white>
                <hover:show_text:'<gray>Native C stresser utilizing O_SYNC and parallel pthreads. Maximum hardware abuse achieved.</gray>'>[?]</hover>
            """.trimIndent())

            future.complete(BenchmarkResult("Disk (Native)", "$scoreStr MB/s", remark, details))
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }
    }

    private fun runKotlin(sizeGb: Int, threads: Int, future: CompletableFuture<BenchmarkResult>) {
        val tempFolder = File(plugin.dataFolder, "disk_bench")
        if (!tempFolder.exists()) tempFolder.mkdirs()

        val sizeBytes = sizeGb.toLong() * 1024 * 1024 * 1024
        val bytesPerThread = sizeBytes / threads
        val bufferSize = 128 * 1024
        val buffer = ByteArray(bufferSize)
        java.util.Random().nextBytes(buffer)

        val executor = Executors.newFixedThreadPool(threads)
        val writeStart = System.currentTimeMillis()
        val tasks = (0 until threads).map { i ->
            executor.submit {
                val file = File(tempFolder, "write_$i.tmp")
                file.outputStream().use { fos ->
                    var written = 0L
                    while (written < bytesPerThread) {
                        fos.write(buffer)
                        written += bufferSize
                    }
                    fos.flush()
                    fos.fd.sync()
                }
            }
        }
        tasks.forEach { it.get() }
        val writeEnd = System.currentTimeMillis()
        val writeTimeSec = (writeEnd - writeStart) / 1000.0
        val writeSpeed = (sizeBytes / 1024.0 / 1024.0) / writeTimeSec

        tempFolder.deleteRecursively()
        executor.shutdown()

        val scoreStr = "%.2f".format(writeSpeed)
        val remark = io.github.Earth1283.utils.Judgement.getDiskRemark(writeSpeed)
        
        val details = mm.deserialize("""
            <gradient:#00ff00:#00aaaa><bold>Kotlin Disk Benchmark Finished</bold></gradient>
            <gray>Throughput:</gray> <#ff4500>${scoreStr} MB/s</#ff4500>
            <gray>Note:</gray> <white>Native stresser unavailable, using JVM fallback.</white>
        """.trimIndent())
        
        future.complete(BenchmarkResult("Disk", "$scoreStr MB/s", remark, details))
    }

    fun runSustainedDiskTest(durationSeconds: Int): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        val threads = minOf(Runtime.getRuntime().availableProcessors(), 16)
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            if (nativeBinary != null) {
                runNative(1024, durationSeconds, threads, future)
            } else {
                val totalTransferred = AtomicLong(0)
                val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
                val executor = Executors.newFixedThreadPool(threads)
                (0 until threads).forEach { i ->
                    executor.submit {
                        val file = File(plugin.dataFolder, "nuke_$i.tmp")
                        val buf = ByteArray(128 * 1024)
                        while (System.currentTimeMillis() < endTime) {
                            try {
                                file.outputStream().use { it.write(buf); it.flush(); it.fd.sync() }
                                totalTransferred.addAndGet(buf.size.toLong())
                            } catch (e: Exception) {}
                        }
                        file.delete()
                    }
                }
                executor.shutdown()
                try { executor.awaitTermination(durationSeconds + 5L, TimeUnit.SECONDS) } catch (e: Exception) {}
                
                val avgSpeed = (totalTransferred.get() / 1024.0 / 1024.0) / durationSeconds
                future.complete(BenchmarkResult("Disk (Sustained)", "%.2f MB/s".format(avgSpeed), "Boring fallback", mm.deserialize("<gray>Kotlin fallback used.</gray>")))
            }
        })
        
        return future
    }
}
