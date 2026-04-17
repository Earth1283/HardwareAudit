package io.github.Earth1283.commands

import io.github.Earth1283.HardwareAudit
import io.github.Earth1283.benchmarks.*
import io.github.Earth1283.utils.*
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.util.concurrent.CompletableFuture
import oshi.SystemInfo

class BenchmarkCommand(private val plugin: HardwareAudit) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()
    private val cpuBenchmark = CpuBenchmark(plugin)
    private val stealBenchmark = StealTimeBenchmark(plugin)
    private val msptBenchmark = MsptBenchmark(plugin)
    private val diskBenchmark = DiskBenchmark(plugin)
    private val memoryBenchmark = MemoryBenchmark(plugin)
    private val networkBenchmark = NetworkBenchmark(plugin)
    private val passMarkFetcher = PassMarkFetcher(plugin)
    
    private val networkScanner = NetworkScanner(plugin)
    private val hostAuditor = HostAuditor(stealBenchmark, diskBenchmark, networkBenchmark)

    init {
        passMarkFetcher.fetchScores()
    }

    private fun sendMessage(sender: CommandSender, message: String) {
        plugin.adventure.sender(sender).sendMessage(mm.deserialize(message))
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("hardwareaudit.use")) {
            sendMessage(sender, "<red>No permission.</red>")
            return true
        }

        if (args.isEmpty() || args[0].equals("help", true)) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "specs" -> {
                val hal = SystemInfo().hardware
                val cpu = hal.processor
                val mem = hal.memory
                
                val cpuName = cpu.processorIdentifier.name.trim()
                val score = passMarkFetcher.lookup(cpuName)

                sendMessage(sender, "<gradient:#00ff00:#00aaaa><bold>Hardware Specs</bold></gradient>")
                sendMessage(sender, "<gray>CPU:</gray> <white>$cpuName</white>")
                sendMessage(sender, "<gray>Cores:</gray> <white>${cpu.physicalProcessorCount} Physical, ${cpu.logicalProcessorCount} Logical</white>")
                sendMessage(sender, "<gray>Max freq:</gray> <yellow>${cpu.maxFreq / 1_000_000_000.0} GHz</yellow>")
                sendMessage(sender, "<gray>RAM:</gray> <white>${mem.total / 1024 / 1024 / 1024} GB Total</white>")
                sendMessage(sender, "<gray>OS:</gray> <white>${SystemInfo().operatingSystem}</white>")
                
                if (score != null) {
                    sendMessage(sender, "<gray>PassMark Score:</gray> <light_purple><bold>$score</bold></light_purple>")
                }
                
                val roast = Judgement.getCpuNameRemark(cpuName)
                if (roast != null) {
                    sendMessage(sender, roast)
                }
                
                // JVM Roast
                val jvmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments
                val jvmRoast = Judgement.getJvmRemark(jvmArgs)
                if (jvmRoast != null) sendMessage(sender, jvmRoast)
            }
            "score" -> {
                 val cpuName = SystemInfo().hardware.processor.processorIdentifier.name
                 val score = passMarkFetcher.lookup(cpuName)
                 if (score != null) {
                     sendMessage(sender, "<green>Found Score for</green> <yellow>$cpuName</yellow>: <light_purple><bold>$score</bold></light_purple>")
                 } else {
                     sendMessage(sender, "<red>Could not find score for:</red> <yellow>$cpuName</yellow>")
                 }
            }
            "cpu" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 30
                sendMessage(sender, "<yellow>Running Single-Threaded CPU Benchmark ($duration s)...</yellow>")
                cpuBenchmark.runCpuTest(duration).thenAccept { result ->
                    plugin.adventure.sender(sender).sendMessage(result.details)
                    sendMessage(sender, result.judgement)
                }
            }
            "cpumulti" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 30
                sendMessage(sender, "<gradient:#ff5555:#ffaa00><bold>Saturating ALL CPU cores ($duration s)...</bold></gradient>")
                sendMessage(sender, "<red><i>Expect severe lag during this test.</i></red>")
                cpuBenchmark.runMultiCpuTest(duration).thenAccept { result ->
                    plugin.adventure.sender(sender).sendMessage(result.details)
                    sendMessage(sender, result.judgement)
                }
            }
            "memory" -> {
                sendMessage(sender, "<yellow>Running Multi-Threaded Memory Benchmark (Maximum Pressure)...</yellow>")
                memoryBenchmark.runMemoryTest().thenAccept { result ->
                    plugin.adventure.sender(sender).sendMessage(result.details)
                    sendMessage(sender, result.judgement)
                }
            }
            "disk" -> {
                val sizeGb = args.getOrNull(1)?.toIntOrNull() ?: 4
                sendMessage(sender, "<yellow>Running Aggressive Disk I/O Benchmark (${sizeGb}GB)...</yellow>")
                diskBenchmark.runDiskTest(sizeGb).thenAccept { result ->
                     plugin.adventure.sender(sender).sendMessage(result.details)
                     sendMessage(sender, result.judgement)
                }
            }
            "steal" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 10
                sendMessage(sender, "<yellow>Measuring Steal Time ($duration s)...</yellow>")
                stealBenchmark.measureStealTime(duration).thenAccept { result ->
                    plugin.adventure.sender(sender).sendMessage(result.details)
                    sendMessage(sender, result.judgement)
                }
            }
            "mspt" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 10
                sendMessage(sender, "<yellow>Monitoring MSPT ($duration s)...</yellow>")
                msptBenchmark.monitorMspt(duration).thenAccept { result ->
                    plugin.adventure.sender(sender).sendMessage(result.details)
                    sendMessage(sender, result.judgement)
                }
            }
            "network" -> {
                sendMessage(sender, "<yellow>Testing Network Speed (Download 100MB)...</yellow>")
                networkBenchmark.runNetworkTest().thenAccept { result ->
                    plugin.adventure.sender(sender).sendMessage(result.details)
                    sendMessage(sender, result.judgement)
                }
            }
            "all" -> {
                runAllBenchmarks(sender)
            }
            "claims" -> {
                sendMessage(sender, "<gradient:#ff5555:#ffaa00><bold>VERIFYING HOST INTEGRITY</bold></gradient>")
                sendMessage(sender, "<gray>Testing for overselling and noisy neighbors...</gray>")
                sendMessage(sender, "<gray><i>(This runs Steal, Disk, and Network benchmarks)</i></gray>")

                hostAuditor.audit { progress -> 
                    sendMessage(sender, progress)
                }.thenAccept { results ->
                    generateClaimsReport(sender, results)
                }
            }
            "neighbors" -> {
                val targetIp = args.getOrNull(1) ?: "127.0.0.1"
                sendMessage(sender, "<yellow>Scanning for MC neighbors on $targetIp (10k-65k)...</yellow>")
                networkScanner.scan(targetIp).thenAccept { result ->
                    displayNeighborResults(sender, result)
                }
            }
            "nuke", "stress" -> {
                 sendMessage(sender, "<gradient:#ff0000:#550000><bold>⚠ INITIATING SYSTEM NUKE (300s) ⚠</bold></gradient>")
                 sendMessage(sender, "<red>This will MAX OUT your CPU, RAM, and DISK for 5 minutes.</red>")
                 sendMessage(sender, "<red>The server WILL freeze. Do not panic.</red>")
                 
                 // Run all violent tests
                 val cpuFuture = cpuBenchmark.runMultiCpuTest(300)
                 val memFuture = memoryBenchmark.runMemoryTest(300)
                 val diskFuture = diskBenchmark.runSustainedDiskTest(300)
                 
                 CompletableFuture.allOf(cpuFuture, memFuture, diskFuture).thenRun {
                     try {
                         val cpuRes = cpuFuture.get()
                         val memRes = memFuture.get()
                         val diskRes = diskFuture.get()
                         
                         sendMessage(sender, "\n<gradient:#00ff00:#ff0000><bold>SYSTEM SURVIVED THE NUKE</bold></gradient>")
                         sendMessage(sender, "<gray>Violent CPU:</gray> <white>${cpuRes.score}</white>")
                         sendMessage(sender, "<gray>Violent RAM:</gray> <white>${memRes.score}</white>")
                         sendMessage(sender, "<gray>Sustained Disk:</gray> <white>${diskRes.score}</white>")
                         
                         sendMessage(sender, "<yellow>If you can read this, your host is solid (or at least didn't crash).</yellow>")
                     } catch (e: Exception) {
                         sendMessage(sender, "<red>Error retrieving nuke results: ${e.message}</red>")
                         e.printStackTrace()
                     }
                 }
            }
            else -> sendHelp(sender)
        }
        return true
    }

    private fun displayNeighborResults(sender: CommandSender, result: NetworkScanResult) {
        val otherPorts = if (result.isLocal) result.openPorts.filter { it != Bukkit.getPort() } else result.openPorts

        sendMessage(sender, "\n<gradient:#55ff55:#33aa33><bold>Neighbor Audit Results (${result.targetIp})</bold></gradient>")
        if (result.isLocal) {
            sendMessage(sender, "<gray>Process Scan:</gray> <white>${result.neighborProcesses.size} instances</white>")
        }
        sendMessage(sender, "<gray>Verified MC Ports:</gray> <white>${otherPorts.size}</white>")
        
        val totalNeighbors = maxOf(result.neighborProcesses.size, otherPorts.size)
        val verdict = if (totalNeighbors == 0) "<green>Isolated. You're the only one here. For now.</green>"
                         else if (totalNeighbors < 3) "<yellow>Cozy. Just a few neighbors to share the lag with.</yellow>"
                         else if (totalNeighbors < 10) "<red>Crowded. Your host is hoarding servers like it's a 2011 gold rush.</red>"
                         else "<red><bold>Slumlord Detected.</bold> This node is a digital tenement. Your CPU is begging for an early grave.</red>"
        
        sendMessage(sender, "<gray>Verdict:</gray> $verdict")
        sendMessage(sender, "<dark_gray><i>Note: Port scan verifies MC servers via legacy SLP.</i></dark_gray>")
    }

    private fun generateClaimsReport(sender: CommandSender, results: List<BenchmarkResult>) {
        val stealRes = results.find { it.name == "Steal" }
        val diskRes = results.find { it.name == "Disk" }
        val netRes = results.find { it.name == "Network" }

        sendMessage(sender, "\n<gradient:#ff5555:#ffaa00><st>--------</st> <bold>HOST INTEGRITY REPORT</bold> <st>--------</st></gradient>")

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

        sendMessage(sender, "<bold>Verdict:</bold> $verdictColor<bold>$verdict</bold></$verdictColor> <gray>($score/100)</gray>")
        
        if (warnings.isNotEmpty()) {
            sendMessage(sender, "<bold><red>Issues Detected:</red></bold>")
            for (w in warnings) {
                sendMessage(sender, " <red>• $w</red>")
            }
        } else {
             sendMessage(sender, "<green>No obvious overselling detected.</green>")
        }

        sendMessage(sender, "")
        sendMessage(sender, "<bold>Key Metrics:</bold>")
        sendMessage(sender, " <gray>CPU Steal:</gray> <white>${stealRes?.score}</white>")
        sendMessage(sender, " <gray>Disk Write:</gray> <white>${diskRes?.score}</white>")
        sendMessage(sender, " <gray>Network:</gray> <white>${netRes?.score}</white>")
        
        sendMessage(sender, "<gradient:#ffaa00:#ff5555><st>------------------------------------</st></gradient>")
    }


    private fun runAllBenchmarks(sender: CommandSender) {
        sendMessage(sender, "<gradient:#ff0000:#ffff00><bold>STARTING RIGOROUS HARDWARE AUDIT</bold></gradient>")
        sendMessage(sender, "<gray>This will take about 120 seconds and <bold><red>WILL</red></bold> lag the server.</gray>")
        
        val results = java.util.Collections.synchronizedList(ArrayList<BenchmarkResult>())
        
        sendMessage(sender, "<gray>[1/7] Running CPU Single-Thread Test...</gray>")
        cpuBenchmark.runCpuTest(15).thenCompose { res ->
            results.add(res)
            sendMessage(sender, "<gray>[2/7] Running CPU Multi-Thread Test...</gray>")
            cpuBenchmark.runMultiCpuTest(15)
        }.thenCompose { res ->
            results.add(res)
            sendMessage(sender, "<gray>[3/7] Running Memory Test...</gray>")
            memoryBenchmark.runMemoryTest()
        }.thenCompose { res ->
            results.add(res)
            sendMessage(sender, "<gray>[4/7] Running Disk Test...</gray>")
            diskBenchmark.runDiskTest()
        }.thenCompose { res ->
            results.add(res)
            sendMessage(sender, "<gray>[5/7] Testing Network...</gray>")
            networkBenchmark.runNetworkTest()
        }.thenCompose { res ->
            results.add(res)
            sendMessage(sender, "<gray>[6/7] Measuring Steal...</gray>")
            stealBenchmark.measureStealTime(10)
        }.thenCompose { res ->
            results.add(res)
            sendMessage(sender, "<gray>[7/7] Monitoring MSPT...</gray>")
            msptBenchmark.monitorMspt(10)
        }.thenAccept { res ->
            results.add(res)
            generateFinalReport(sender, results)
        }
    }
    
    private fun generateFinalReport(sender: CommandSender, results: List<BenchmarkResult>) {
        sendMessage(sender, "\n<gradient:#00ff00:#00aaaa><st>----------------</st> <bold>HARDWARE AUDIT REPORT</bold> <st>----------------</st></gradient>")
        
        for (res in results) {
            sendMessage(sender, "<bold>${res.name}:</bold> <white>${res.score}</white>")
            sendMessage(sender, "  ↳ ${res.judgement}")
        }

        val cpuName = SystemInfo().hardware.processor.processorIdentifier.name
        val roast = Judgement.getCpuNameRemark(cpuName)
        if (roast != null) {
             sendMessage(sender, "<bold>CPU Model:</bold> $roast")
        }
        
        val jvmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments
        val jvmRoast = Judgement.getJvmRemark(jvmArgs)
        if (jvmRoast != null) {
            sendMessage(sender, "<bold>JVM:</bold> $jvmRoast")
        }

        sendMessage(sender, "<gradient:#00aaaa:#00ff00><st>--------------------------------------------------</st></gradient>")
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
        sendMessage(sender, "<gradient:aqua:blue><bold>HardwareAudit Commands</bold></gradient>")
        sendMessage(sender, "<dark_gray>--------------------------------</dark_gray>")
        sendMessage(sender, "<yellow>/audit specs</yellow> <gray>- View hardware & JVM details.</gray>")
        sendMessage(sender, "<yellow>/audit score</yellow> <gray>- Check PassMark score for this CPU.</gray>")
        sendMessage(sender, "<yellow>/audit cpu [sec]</yellow> <gray>- Single-core CPU benchmark.</gray>")
        sendMessage(sender, "<yellow>/audit cpumulti [sec]</yellow> <gray>- Multi-core CPU saturation test.</gray>")
        sendMessage(sender, "<yellow>/audit steal [sec]</yellow> <gray>- Measure scheduling delay/steal.</gray>")
        sendMessage(sender, "<yellow>/audit mspt [sec]</yellow> <gray>- Monitor tick stability (Std Dev).</gray>")
        sendMessage(sender, "<yellow>/audit disk [sizeGB]</yellow> <gray>- Test Disk I/O speeds.</gray>")
        sendMessage(sender, "<yellow>/audit memory</yellow> <gray>- Test Memory throughput (Violent).</gray>")
        sendMessage(sender, "<yellow>/audit network</yellow> <gray>- Test Download Speed.</gray>")
        sendMessage(sender, "<yellow>/audit claims</yellow> <gray>- Run ALL tests to verify host validity.</gray>")
        sendMessage(sender, "<yellow>/audit neighbors [ip]</yellow> <gray>- Scan for other servers on an IP.</gray>")
        sendMessage(sender, "<yellow>/audit nuke</yellow> <gray>- <red>STRESS TEST EVERYTHING (5m).</red></gray>")
        sendMessage(sender, "<dark_gray>--------------------------------</dark_gray>")
    }
}
