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
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

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
                sender.sendMessage(mm.deserialize("<yellow>Running Aggressive CPU Benchmark ($duration s)...</yellow>"))
                cpuBenchmark.runCpuTest(duration).thenAccept { result ->
                    sender.sendMessage(result.details)
                    sender.sendMessage(mm.deserialize(result.judgement))
                }
            }
            "memory" -> {
                sender.sendMessage(mm.deserialize("<yellow>Running Multi-Threaded Memory Benchmark...</yellow>"))
                memoryBenchmark.runMemoryTest().thenAccept { result ->
                    sender.sendMessage(result.details)
                    sender.sendMessage(mm.deserialize(result.judgement))
                }
            }
            "disk" -> {
                sender.sendMessage(mm.deserialize("<yellow>Running Aggressive Disk I/O Benchmark (2GB)...</yellow>"))
                diskBenchmark.runDiskTest().thenAccept { result ->
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
            else -> sendHelp(sender)
        }
        return true
    }

    private fun runClaimsCheck(sender: CommandSender) {
        sender.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><bold>VERIFYING HOST INTEGRITY</bold></gradient>"))
        sender.sendMessage(mm.deserialize("<gray>Testing for overselling and noisy neighbors...</gray>"))
        sender.sendMessage(mm.deserialize("<gray><i>(This runs Steal, Disk, and Network benchmarks)</i></gray>"))

        val results = java.util.Collections.synchronizedList(ArrayList<io.github.Earth1283.utils.BenchmarkResult>())

        // 1. Check Steal (CPU Contention)
        stealBenchmark.measureStealTime(10).thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[1/3] Disk I/O check...</gray>"))
            // 2. Check Disk (IO Contention)
            // Use 15s timeout for disk to fail fast if it's really bad? No, run the full test.
            diskBenchmark.runDiskTest()
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[2/3] Bandwidth check...</gray>"))
            // 3. Check Network (Uplink Congestion)
            networkBenchmark.runNetworkTest()
        }.thenAccept { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[3/3] Analyzing results...</gray>"))
            generateClaimsReport(sender, results)
        }
    }

    private fun generateClaimsReport(sender: CommandSender, results: List<io.github.Earth1283.utils.BenchmarkResult>) {
        // Extract metrics
        val stealRes = results.find { it.name == "Steal" }
        val diskRes = results.find { it.name == "Disk" }
        val netRes = results.find { it.name == "Network" }

        sender.sendMessage(mm.deserialize("\n<gradient:#ff5555:#ffaa00><st>--------</st> <bold>HOST INTEGRITY REPORT</bold> <st>--------</st></gradient>"))

        // Analysis
        var score = 100
        val warnings = ArrayList<String>()

        // Analyze Steal (Format: "0.2% (Good)")
        val stealVal = stealRes?.score?.substringBefore("%")?.toDoubleOrNull() ?: 0.0
        if (stealVal > 5.0) { score -= 40; warnings.add("High CPU Steal (>5%)") }
        else if (stealVal > 1.0) { score -= 10; warnings.add("Minor CPU Steal (>1%)") }

        // Analyze Disk (Format: "500.00 MB/s (Write)") - Parsing is tricky, let's just grab the number
        val diskVal = diskRes?.score?.substringBefore(" MB/s")?.toDoubleOrNull() ?: 0.0
        if (diskVal < 100.0) { score -= 30; warnings.add("Slow Disk Write (<100MB/s)") }
        else if (diskVal < 300.0) { score -= 10; warnings.add("Mediocre Disk Speed") }

        // Analyze Network (Format: "100.00 Mbps")
        val netVal = netRes?.score?.substringBefore(" Mbps")?.toDoubleOrNull() ?: 0.0
        if (netVal < 50.0) { score -= 20; warnings.add("Low Bandwidth (<50Mbps)") }

        // Final Verdict
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

        // Show Key Metrics in a grid/summary way
        sender.sendMessage(mm.deserialize(""))
        sender.sendMessage(mm.deserialize("<bold>Key Metrics:</bold>"))
        sender.sendMessage(mm.deserialize(" <gray>CPU Steal:</gray> <white>${stealRes?.score}</white>"))
        sender.sendMessage(mm.deserialize(" <gray>Disk Write:</gray> <white>${diskRes?.score}</white>"))
        sender.sendMessage(mm.deserialize(" <gray>Network:</gray> <white>${netRes?.score}</white>"))
        
        sender.sendMessage(mm.deserialize("<gradient:#ffaa00:#ff5555><st>------------------------------------</st></gradient>"))
    }


    private fun runAllBenchmarks(sender: CommandSender) {
        sender.sendMessage(mm.deserialize("<gradient:#ff0000:#ffff00><bold>STARTING FULL HARDWARE AUDIT</bold></gradient>"))
        sender.sendMessage(mm.deserialize("<gray>This will take about 60-90 seconds and <st>may</st> <bold><red>WILL</red></bold> lag the server.</gray>"))
        
        val results = java.util.Collections.synchronizedList(ArrayList<io.github.Earth1283.utils.BenchmarkResult>())
        
        // Chain them sequentially to minimize interference
        // CPU -> RAM -> Disk -> Network -> Steal -> MSPT
        
        sender.sendMessage(mm.deserialize("<gray>[1/6] Running CPU Test...</gray>"))
        cpuBenchmark.runCpuTest(15).thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[2/6] Running Memory Test...</gray>"))
            memoryBenchmark.runMemoryTest()
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[3/6] Running Disk Test...</gray>"))
            diskBenchmark.runDiskTest()
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[4/6] Testing Network...</gray>"))
            networkBenchmark.runNetworkTest()
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[5/6] Measuring Steal...</gray>"))
            stealBenchmark.measureStealTime(10)
        }.thenCompose { res ->
            results.add(res)
            sender.sendMessage(mm.deserialize("<gray>[6/6] Monitoring MSPT...</gray>"))
            msptBenchmark.monitorMspt(10)
        }.thenAccept { res ->
            results.add(res)
            generateFinalReport(sender, results)
        }
    }
    
    private fun generateFinalReport(sender: CommandSender, results: List<io.github.Earth1283.utils.BenchmarkResult>) {
        sender.sendMessage(mm.deserialize("\n<gradient:#00ff00:#00aaaa><st>----------------</st> <bold>HARDWARE AUDIT REPORT</bold> <st>----------------</st></gradient>"))
        
        for (res in results) {
            // Summary line
            sender.sendMessage(mm.deserialize("<bold>${res.name}:</bold> <white>${res.score}</white>"))
            
            // Extract text from judgement (strip tags) is preferred but for now print raw is probably fine as it's just one line?
            // Actually, showing the judgement in full color is nice.
            sender.sendMessage(mm.deserialize("  ↳ ${res.judgement}"))
        }

        // Add Spec Summary
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
            return mutableListOf("cpu", "steal", "mspt", "disk", "memory", "network", "specs", "all", "score", "claims").filter { it.startsWith(args[0], true) }.toMutableList()
        }
        if (args.size == 2 && (args[0].equals("cpu", true) || args[0].equals("steal", true) || args[0].equals("mspt", true))) {
             return mutableListOf("10", "30", "60")
        }
        return null
    }
    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(mm.deserialize("<gradient:aqua:blue><bold>HardwareAudit Commands</bold></gradient>"))
        sender.sendMessage(mm.deserialize("<dark_gray>--------------------------------</dark_gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit specs</yellow> <gray>- View hardware & JVM details.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit score</yellow> <gray>- Check PassMark score for this CPU.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit cpu [sec]</yellow> <gray>- Benchmark CPU performance.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit steal [sec]</yellow> <gray>- Measure scheduling delay/steal.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit mspt [sec]</yellow> <gray>- Monitor tick stability (Std Dev).</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit disk</yellow> <gray>- Test Disk I/O speeds (NVMe detection).</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit memory</yellow> <gray>- Test Memory throughput.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit network</yellow> <gray>- Test Download Speed.</gray>"))
        sender.sendMessage(mm.deserialize("<yellow>/audit claims</yellow> <gray>- Run ALL tests to verify host validity.</gray>"))
        sender.sendMessage(mm.deserialize("<dark_gray>--------------------------------</dark_gray>"))
    }
}
