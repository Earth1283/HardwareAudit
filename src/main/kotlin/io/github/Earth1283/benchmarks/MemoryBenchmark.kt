package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.utils.BenchmarkResult
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class MemoryBenchmark(private val plugin: HardwareAudit) {
    private val mm = MiniMessage.miniMessage()
    private var memTestHandle: MethodHandle? = null

    init {
        setupNative()
    }

    private fun setupNative() {
        try {
            val binDir = File(plugin.dataFolder, "bin")
            if (!binDir.exists()) binDir.mkdirs()

            val osName = System.getProperty("os.name").lowercase()
            val libName = when {
                osName.contains("win") -> "memory_party.dll"
                osName.contains("mac") -> "libmemory_party.dylib"
                else -> "libmemory_party.so"
            }

            val libFile = File(binDir, libName)
            val cFile = File(binDir, "memory_party.c")

            plugin.getResource("memory_party.c")?.use { input ->
                cFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return

            plugin.logger.info("Compiling native Memory Stressor from source...")
            val process = Runtime.getRuntime().exec(
                arrayOf("gcc", "-shared", "-fPIC", "-O3", cFile.absolutePath, "-o", libFile.absolutePath, "-lpthread")
            )
            if (process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0) {
                val arena = Arena.ofAuto()
                val lookup = SymbolLookup.libraryLookup(libFile.toPath(), arena)
                val descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG
                )
                memTestHandle = Linker.nativeLinker().downcallHandle(
                    lookup.find("run_memory_test").orElseThrow(), descriptor
                )
                plugin.logger.info("FFM Memory Stressor compiled and loaded! Ready for violence.")
            } else {
                val error = process.errorStream.bufferedReader().readText()
                plugin.logger.warning("Native compilation failed. Please ensure 'gcc' is installed.")
                plugin.logger.warning("Error: $error")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error during FFM setup: ${e.message}")
        }
    }

    fun runMemoryTest(durationSeconds: Int = 15): CompletableFuture<BenchmarkResult> {
        val future = CompletableFuture<BenchmarkResult>()
        val threads = Runtime.getRuntime().availableProcessors()
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            if (memTestHandle != null) {
                runNative(durationSeconds, threads, future)
            } else {
                runKotlin(durationSeconds, threads, future)
            }
        })
        
        return future
    }

    private fun runNative(durationSeconds: Int, threads: Int, future: CompletableFuture<BenchmarkResult>) {
        val start = System.currentTimeMillis()
        // Use 1GB or 50% of system RAM (outside heap)
        val totalMem = 1024L * 1024 * 1024 
        
        try {
            val churnedBytes = memTestHandle!!.invokeWithArguments(durationSeconds, threads, totalMem) as Long
            val end = System.currentTimeMillis()
            val elapsed = (end - start) / 1000.0

            val mbPerSec = (churnedBytes / 1024.0 / 1024.0) / elapsed
            val scoreStr = "%.2f MB/s".format(mbPerSec)
            val remark = io.github.Earth1283.utils.Judgement.getMemoryRemark(mbPerSec)

            val details = mm.deserialize("""
                <gradient:#ff00ff:#00ffff><bold>VIOLENT NATIVE RAM DESTRUCTION COMPLETE!</bold></gradient>
                <gray>Mode:</gray> <white>C-FFM (Bypassing JVM Heap)</white>
                <gray>Throughput:</gray> <#00bfff>${scoreStr}</#00bfff>
                <gray>Allocated:</gray> <white>${totalMem / 1024 / 1024} MB</white>
                <gray>Threads:</gray> <white>$threads</white>
                <gray>Duration:</gray> <white>${elapsed.toInt()}s</white>
                <hover:show_text:'<gray>Direct memory access via FFM API. Saturating memory controller by churning large blocks outside the Java Heap.</gray>'>[?]</hover>
            """.trimIndent())

            future.complete(BenchmarkResult("RAM (Native)", scoreStr, remark, details))
        } catch (e: Exception) {
            runKotlin(durationSeconds, threads, future)
        }
    }

    private fun runKotlin(durationSeconds: Int, threads: Int, future: CompletableFuture<BenchmarkResult>) {
        val workerThreads = ArrayList<Thread>()
        val throughputPasses = AtomicLong(0)
        val endTime = System.currentTimeMillis() + (durationSeconds * 1000)
        
        // Dynamic Block Size Calculation
        val maxMemory = Runtime.getRuntime().maxMemory()
        val safeMemory = (maxMemory * 0.85).toLong() 
        val safePerThread = safeMemory / threads / 2 
        val targetBlockSize = 512L * 1024 * 1024 
        
        var blockSizeLong = min(targetBlockSize, safePerThread)
        if (blockSizeLong < 1024 * 1024) blockSizeLong = 1024 * 1024 
        val blockSize = blockSizeLong.toInt()
        
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
                } catch (e: OutOfMemoryError) {
                }
            }
            workerThreads.add(t)
            t.start()
        }
        
        for (t in workerThreads) t.join()
        
        val latencyStart = System.nanoTime()
        val listSize = 16_000_000 
        val array = IntArray(listSize) { it }
        array.shuffle() 
        
        var current = 0
        for (i in 0 until 10_000_000) {
            current = array[current]
        }
        val latencyEnd = System.nanoTime()
        val latencyNs = (latencyEnd - latencyStart) / 10_000_000.0

        val totalBytes = throughputPasses.get() * blockSize.toLong()
        val mbPerSec = (totalBytes / 1024.0 / 1024.0) / durationSeconds
        val scoreStr = "%.2f".format(mbPerSec)
        val remark = io.github.Earth1283.utils.Judgement.getMemoryRemark(mbPerSec)
        
        val details = mm.deserialize("""
            <gradient:#00ff00:#00aaaa><bold>Rigorous RAM Benchmark Finished!</bold></gradient>
            <gray>Throughput:</gray> <#00bfff>${scoreStr} MB/s</#00bfff>
            <gray>Est. Latency:</gray> <#ff8c00>%.2f ns</#ff8c00>
            <gray>Note:</gray> <white>Native stresser unavailable, using JVM fallback.</white>
        """.trimIndent().format(latencyNs))
        
        future.complete(BenchmarkResult("RAM", "$scoreStr MB/s", remark, details))
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
