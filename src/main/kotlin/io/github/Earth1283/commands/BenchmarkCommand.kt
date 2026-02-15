package io.github.Earth1283.commands

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.benchmarks.CpuBenchmark
import io.github.Earth1283.benchmarks.StealTimeBenchmark
import io.github.Earth1283.benchmarks.DiskBenchmark
import io.github.Earth1283.benchmarks.MemoryBenchmark
import io.github.Earth1283.benchmarks.MsptBenchmark
import io.github.Earth1283.utils.HardwareInfo
import io.github.Earth1283.utils.Judgement
import io.github.Earth1283.utils.PassMarkFetcher
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.concurrent.CompletableFuture
import io.github.Earth1283.benchmarks.NetworkBenchmark

class BenchmarkCommand(private val plugin: HardwareAudit) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()
    private val cpuBenchmark = CpuBenchmark(plugin)
    private val stealBenchmark = StealTimeBenchmark(plugin)
    private val msptBenchmark = MsptBenchmark(plugin)
    private val diskBenchmark = DiskBenchmark(plugin)
    private val memoryBenchmark = MemoryBenchmark(plugin)
    private val networkBenchmark = NetworkBenchmark(plugin)
    private val passMarkFetcher = PassMarkFetcher(plugin)

    init {
        passMarkFetcher.fetchScores()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("hardwareaudit.use")) {
            sender.sendMessage(mm.deserialize("<red>No permission.</red>"))
            return true
        }

        if (args.isEmpty() || args[0].equals("help", true)) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "specs" -> {
                val hal = oshi.SystemInfo().hardware
                val cpu = hal.processor
                val mem = hal.memory
                
                val cpuName = cpu.processorIdentifier.name.trim()
                val score = passMarkFetcher.lookup(cpuName)

                sender.sendMessage(mm.deserialize("<gradient:#00ff00:#00aaaa><bold>Hardware Specs</bold></gradient>"))
                sender.sendMessage(mm.deserialize("<gray>CPU:</gray> <white>$cpuName</white>"))
                sender.sendMessage(mm.deserialize("<gray>Cores:</gray> <white>${cpu.physicalProcessorCount} Physical, ${cpu.logicalProcessorCount} Logical</white>"))
                sender.sendMessage(mm.deserialize("<gray>Max freq:</gray> <yellow>${cpu.maxFreq / 1_000_000_000.0} GHz</yellow>"))
                sender.sendMessage(mm.deserialize("<gray>RAM:</gray> <white>${mem.total / 1024 / 1024 / 1024} GB Total</white>"))
                sender.sendMessage(mm.deserialize("<gray>OS:</gray> <white>${oshi.SystemInfo().operatingSystem}</white>"))
                
                if (score != null) {
                    sender.sendMessage(mm.deserialize("<gray>PassMark Score:</gray> <light_purple><bold>$score</bold></light_purple>"))
                }
                
                val roast = Judgement.getCpuNameRemark(cpuName)
                if (roast != null) {
                    sender.sendMessage(mm.deserialize(roast))
                }
                
                // JVM Roast
                val jvmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments
                val jvmRoast = Judgement.getJvmRemark(jvmArgs)
                if (jvmRoast != null) sender.sendMessage(mm.deserialize(jvmRoast))
            }
            "score" -> {
                 val cpuName = oshi.SystemInfo().hardware.processor.processorIdentifier.name
                 val score = passMarkFetcher.lookup(cpuName)
                 if (score != null) {
                     sender.sendMessage(mm.deserialize("<green>Found Score for</green> <yellow>$cpuName</yellow>: <light_purple><bold>$score</bold></light_purple>"))
                 } else {
                     sender.sendMessage(mm.deserialize("<red>Could not find score for:</red> <yellow>$cpuName</yellow>"))
                 }
            }
            "cpu" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 30
                sender.sendMessage(mm.deserialize("<yellow>Running Single-Threaded CPU Benchmark ($duration s)...</yellow>"))
                cpuBenchmark.runCpuTest(duration).thenAccept { result ->
                    sender.sendMessage(result.details)
                    sender.sendMessage(mm.deserialize(result.judgement))
                }
            }
            "cpumulti" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 30
                sender.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><bold>Saturating ALL CPU cores ($duration s)...</bold></gradient>"))
                sender.sendMessage(mm.deserialize("<red><i>Expect severe lag during this test.</i></red>"))
                cpuBenchmark.runMultiCpuTest(duration).thenAccept { result ->
                    sender.sendMessage(result.details)
                    sender.sendMessage(mm.deserialize(result.judgement))
                }
            }
            "memory" -> {
                sender.sendMessage(mm.deserialize("<yellow>Running Multi-Threaded Memory Benchmark (Maximum Pressure)...</yellow>"))
                memoryBenchmark.runMemoryTest().thenAccept { result ->
                    sender.sendMessage(result.details)
                    sender.sendMessage(mm.deserialize(result.judgement))
                }
            }
            "disk" -> {
                val sizeGb = args.getOrNull(1)?.toIntOrNull() ?: 4
                sender.sendMessage(mm.deserialize("<yellow>Running Aggressive Disk I/O Benchmark (${sizeGb}GB)...</yellow>"))
                diskBenchmark.runDiskTest(sizeGb).thenAccept { result ->
                     sender.sendMessage(result.details)
                     sender.sendMessage(mm.deserialize(result.judgement))
                }
            }
            "steal" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 10
                sender.sendMessage(mm.deserialize("<yellow>Measuring Steal Time ($duration s)...</yellow>"))
                stealBenchmark.measureStealTime(duration).thenAccept { result ->
                    sender.sendMessage(result.details)
                    sender.sendMessage(mm.deserialize(result.judgement))
                }
            }
            "mspt" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 10
                sender.sendMessage(mm.deserialize("<yellow>Monitoring MSPT ($duration s)...</yellow>"))
                msptBenchmark.monitorMspt(duration).thenAccept { result ->
                    sender.sendMessage(result.details)
                    sender.sendMessage(mm.deserialize(result.judgement))
                }
            }
            "network" -> {
                sender.sendMessage(mm.deserialize("<yellow>Testing Network Speed (Download 100MB)...</yellow>"))
                networkBenchmark.runNetworkTest().thenAccept { result ->
                    sender.sendMessage(result.details)
                    sender.sendMessage(mm.deserialize(result.judgement))
                }
            }
            "all" -> {
                runAllBenchmarks(sender)
            }
            "claims" -> {
                runClaimsCheck(sender)
            }
            "neighbors" -> {
                val targetIp = args.getOrNull(1) ?: "127.0.0.1"
                sender.sendMessage(mm.deserialize("<yellow>Scanning for MC neighbors on $targetIp (10k-65k)...</yellow>"))
                scanNeighbors(sender, targetIp)
            }
            "nuke", "stress" -> {
                 sender.sendMessage(mm.deserialize("<gradient:#ff0000:#550000><bold>⚠ INITIATING SYSTEM NUKE (300s) ⚠</bold></gradient>"))
                 sender.sendMessage(mm.deserialize("<red>This will MAX OUT your CPU, RAM, and DISK for 5 minutes.</red>"))
                 sender.sendMessage(mm.deserialize("<red>The server WILL freeze. Do not panic.</red>"))
                 
                 // Run all violent tests
                 val cpuFuture = cpuBenchmark.runMultiCpuTest(300)
                 val memFuture = memoryBenchmark.runMemoryTest(300)
                 val diskFuture = diskBenchmark.runSustainedDiskTest(300)
                 
                 CompletableFuture.allOf(cpuFuture, memFuture, diskFuture).thenRun {
                     try {
                         val cpuRes = cpuFuture.get()
                         val memRes = memFuture.get()
                         val diskRes = diskFuture.get()
                         
                         sender.sendMessage(mm.deserialize("\n<gradient:#00ff00:#ff0000><bold>SYSTEM SURVIVED THE NUKE</bold></gradient>"))
                         sender.sendMessage(mm.deserialize("<gray>Violent CPU:</gray> <white>${cpuRes.score}</white>"))
                         sender.sendMessage(mm.deserialize("<gray>Violent RAM:</gray> <white>${memRes.score}</white>"))
                         sender.sendMessage(mm.deserialize("<gray>Sustained Disk:</gray> <white>${diskRes.score}</white>"))
                         
                         sender.sendMessage(mm.deserialize("<yellow>If you can read this, your host is solid (or at least didn't crash).</yellow>"))
                     } catch (e: Exception) {
                         sender.sendMessage(mm.deserialize("<red>Error retrieving nuke results: ${e.message}</red>"))
                         e.printStackTrace()
                     }
                 }
            }
            else -> sendHelp(sender)
        }
        return true
    }

    private fun scanNeighbors(sender: CommandSender, targetIp: String = "127.0.0.1") {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val isLocal = targetIp == "127.0.0.1" || targetIp == "localhost"
            
            // 1. Process Scan (Only if local)
            val neighborProcesses = if (isLocal) {
                val os = oshi.SystemInfo().operatingSystem
                val allProcesses = os.processes
                val currentPid = os.processId
                val mcStrings = listOf("java", "-Xmx", "org.bukkit.", "net.minecraft.", "paper.jar", "spigot.jar", "velocity.jar", "bungeecord.jar")
                
                allProcesses.filter { p ->
                    if (p.processID == currentPid) return@filter false
                    val cmd = p.commandLine?.lowercase() ?: ""
                    val name = p.name.lowercase()
                    (name.contains("java") || cmd.contains("java")) && mcStrings.any { cmd.contains(it.lowercase()) }
                }
            } else emptyList()

            // 2. Port Scan (targetIp:10,000-65,535) - Parallelized
            val openPorts = java.util.Collections.synchronizedList(ArrayList<Int>())
            val portExecutor = java.util.concurrent.Executors.newFixedThreadPool(128)
            val timeout = if (isLocal) 150 else 500
            
            val latch = java.util.concurrent.CountDownLatch(65535 - 10000 + 1)

            for (port in 10000..65535) {
                portExecutor.execute {
                    try {
                        java.net.Socket().use { socket ->
                            socket.connect(java.net.InetSocketAddress(targetIp, port), timeout)
                            // Verified MC: Send Legacy SLP (0xFE 0x01)
                            socket.outputStream.write(byteArrayOf(0xFE.toByte(), 0x01.toByte()))
                            socket.soTimeout = 200
                            if (socket.inputStream.read() == 0xFF) {
                                openPorts.add(port)
                            }
                        }
                    } catch (e: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }
            }

            try {
                latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: Exception) {}
            portExecutor.shutdownNow()
            
            // Filter current port only if scanning local
            val currentPort = Bukkit.getPort()
            val otherPorts = if (isLocal) openPorts.filter { it != currentPort } else openPorts

            sender.sendMessage(mm.deserialize("\n<gradient:#55ff55:#33aa33><bold>Neighbor Audit Results ($targetIp)</bold></gradient>"))
            if (isLocal) {
                sender.sendMessage(mm.deserialize("<gray>Process Scan:</gray> <white>${neighborProcesses.size} instances</white>"))
            }
            sender.sendMessage(mm.deserialize("<gray>Verified MC Ports:</gray> <white>${otherPorts.size}</white>"))
            
            val totalNeighbors = maxOf(neighborProcesses.size, otherPorts.size)
            val verdict = if (totalNeighbors == 0) "<green>Isolated. You're the only one here. For now.</green>"
                             else if (totalNeighbors < 3) "<yellow>Cozy. Just a few neighbors to share the lag with.</yellow>"
                             else if (totalNeighbors < 10) "<red>Crowded. Your host is hoarding servers like it's a 2011 gold rush.</red>"
                             else "<red><bold>Slumlord Detected.</bold> This node is a digital tenement. Your CPU is begging for an early grave.</red>"
            
            sender.sendMessage(mm.deserialize("<gray>Verdict:</gray> $verdict"))
            sender.sendMessage(mm.deserialize("<dark_gray><i>Note: Port scan verifies MC servers via legacy SLP.</i></dark_gray>"))
        })
    }

    private fun runClaimsCheck(sender: CommandSender) {
        sender.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><bold>VERIFYING HOST INTEGRITY</bold></gradient>"))
        sender.sendMessage(mm.deserialize("<gray>Testing for overselling and noisy neighbors...</gray>"))
        sender.sendMessage(mm.deserialize("<gray><i>(This runs Steal, Disk, and Network benchmarks)</i></gray>"))

        val results = java.util.Collections.synchronizedList(ArrayList<io.github.Earth1283.utils.BenchmarkResult>())

        stealBenchmark.measureStealTime(10).thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[1/3] Disk I/O check...</gray>"))
            diskBenchmark.runDiskTest()
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[2/3] Bandwidth check...</gray>"))
            networkBenchmark.runNetworkTest()
        }.thenAccept { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[3/3] Analyzing results...</gray>"))
            generateClaimsReport(sender, results)
        }
    }

    private fun generateClaimsReport(sender: CommandSender, results: List<io.github.Earth1283.utils.BenchmarkResult>) {
        val stealRes = results.find { it.name == "Steal" }
        val diskRes = results.find { it.name == "Disk" }
        val netRes = results.find { it.name == "Network" }

        sender.sendMessage(mm.deserialize("\n<gradient:#ff5555:#ffaa00><st>--------</st> <bold>HOST INTEGRITY REPORT</bold> <st>--------</st></gradient>"))

        var score = 100
        val warnings = ArrayList<String>()

        val stealVal = stealRes?.score?.substringBefore("%")?.toDoubleOrNull() ?: 0.0
        if (stealVal > 5.0) { score -= 40; warnings.add("High CPU Steal (>5%)") }
        else if (stealVal > 1.0) { score -= 10; warnings.add("Minor CPU Steal (>1%)") }

        val diskVal = diskRes?.score?.substringBefore(" MB/s")?.toDoubleOrNull() ?: 0.0
        if (diskVal < 100.0) { score -= 30; warnings.add("Slow Disk Write (<100MB/s)") }
        else if (diskVal < 300.0) { score -= 10; warnings.add("Mediocre Disk Speed") }

        val netVal = netRes?.score?.substringBefore(" Mbps")?.toDoubleOrNull() ?: 0.0
        if (netVal < 50.0) { score -= 20; warnings.add("Low Bandwidth (<50Mbps)") }

        val verdictColor = if (score >= 90) "<green>" else if (score >= 70) "<yellow>" else "<red>"
        val verdict = if (score >= 90) "PASS" else if (score >= 70) "SUSPECT" else "FAIL"

        sender.sendMessage(mm.deserialize("<bold>Verdict:</bold> $verdictColor<bold>$verdict</bold></$verdictColor> <gray>($score/100)</gray>"))
        
        if (warnings.isNotEmpty()) {
            sender.sendMessage(mm.deserialize("<bold><red>Issues Detected:</red></bold>"))
            for (w in warnings) {
                sender.sendMessage(mm.deserialize(" <red>• $w</red>"))
            }
        } else {
             sender.sendMessage(mm.deserialize("<green>No obvious overselling detected.</green>"))
        }

        sender.sendMessage(mm.deserialize(""))
        sender.sendMessage(mm.deserialize("<bold>Key Metrics:</bold>"))
        sender.sendMessage(mm.deserialize(" <gray>CPU Steal:</gray> <white>${stealRes?.score}</white>"))
        sender.sendMessage(mm.deserialize(" <gray>Disk Write:</gray> <white>${diskRes?.score}</white>"))
        sender.sendMessage(mm.deserialize(" <gray>Network:</gray> <white>${netRes?.score}</white>"))
        
        sender.sendMessage(mm.deserialize("<gradient:#ffaa00:#ff5555><st>------------------------------------</st></gradient>"))
    }


    private fun runAllBenchmarks(sender: CommandSender) {
        sender.sendMessage(mm.deserialize("<gradient:#ff0000:#ffff00><bold>STARTING RIGOROUS HARDWARE AUDIT</bold></gradient>"))
        sender.sendMessage(mm.deserialize("<gray>This will take about 120 seconds and <bold><red>WILL</red></bold> lag the server.</gray>"))
        
        val results = java.util.Collections.synchronizedList(ArrayList<io.github.Earth1283.utils.BenchmarkResult>())
        
        sender.sendMessage(mm.deserialize("<gray>[1/7] Running CPU Single-Thread Test...</gray>"))
        cpuBenchmark.runCpuTest(15).thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[2/7] Running CPU Multi-Thread Test...</gray>"))
            cpuBenchmark.runMultiCpuTest(15)
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[3/7] Running Memory Test...</gray>"))
            memoryBenchmark.runMemoryTest()
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[4/7] Running Disk Test...</gray>"))
            diskBenchmark.runDiskTest()
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[5/7] Testing Network...</gray>"))
            networkBenchmark.runNetworkTest()
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[6/7] Measuring Steal...</gray>"))
            stealBenchmark.measureStealTime(10)
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[7/7] Monitoring MSPT...</gray>"))
            msptBenchmark.monitorMspt(10)
        }.thenAccept { res ->
            results.add(res)
            generateFinalReport(sender, results)
        }
    }
    
    private fun generateFinalReport(sender: CommandSender, results: List<io.github.Earth1283.utils.BenchmarkResult>) {
        sender.sendMessage(mm.deserialize("\n<gradient:#00ff00:#00aaaa><st>----------------</st> <bold>HARDWARE AUDIT REPORT</bold> <st>----------------</st></gradient>"))
        
        for (res in results) {
            sender.sendMessage(mm.deserialize("<bold>${res.name}:</bold> <white>${res.score}</white>"))
            sender.sendMessage(mm.deserialize("  ↳ ${res.judgement}"))
        }

        val cpuName = oshi.SystemInfo().hardware.processor.processorIdentifier.name
        val roast = Judgement.getCpuNameRemark(cpuName)
        if (roast != null) {
             sender.sendMessage(mm.deserialize("<bold>CPU Model:</bold> $roast"))
        }
        
        val jvmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments
        val jvmRoast = Judgement.getJvmRemark(jvmArgs)
        if (jvmRoast != null) {
            sender.sendMessage(mm.deserialize("<bold>JVM:</bold> $jvmRoast"))
        }

        sender.sendMessage(mm.deserialize("<gradient:#00aaaa:#00ff00><st>--------------------------------------------------</st></gradient>"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.size == 1) {
            return mutableListOf("cpu", "cpumulti", "steal", "mspt", "disk", "memory", "network", "specs", "all", "score", "claims", "nuke", "neighbors").filter { it.startsWith(args[0], true) }.toMutableList()
        }
        if (args.size == 2) {
             if (args[0].equals("cpu", true) || args[0].equals("cpumulti", true) || args[0].equals("steal", true) || args[0].equals("mspt", true)) {
                 return mutableListOf("10", "30", "60")
             }
             if (args[0].equals("disk", true)) {
                 return mutableListOf("4", "8", "16", "32", "64", "128", "512", "1024")
             }
             if (args[0].equals("neighbors", true)) {
                 return mutableListOf("127.0.0.1")
             }
        }
        return null
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(mm.deserialize("<gradient:aqua:blue><bold>HardwareAudit Commands</bold></gradient>"))
        sender.sendMessage(mm.deserialize("<dark_gray>--------------------------------</dark_gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit specs</yellow> <gray>- View hardware & JVM details.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit score</yellow> <gray>- Check PassMark score for this CPU.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit cpu [sec]</yellow> <gray>- Single-core CPU benchmark.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit cpumulti [sec]</yellow> <gray>- Multi-core CPU saturation test.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit steal [sec]</yellow> <gray>- Measure scheduling delay/steal.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit mspt [sec]</yellow> <gray>- Monitor tick stability (Std Dev).</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit disk [sizeGB]</yellow> <gray>- Test Disk I/O speeds.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit memory</yellow> <gray>- Test Memory throughput (Violent).</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit network</yellow> <gray>- Test Download Speed.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit claims</yellow> <gray>- Run ALL tests to verify host validity.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit neighbors [ip]</yellow> <gray>- Scan for other servers on an IP.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit nuke</yellow> <gray>- <red>STRESS TEST EVERYTHING (5m).</red></gray>"))
        sender.sendMessage(mm.deserialize("<dark_gray>--------------------------------</dark_gray>"))
    }
}
