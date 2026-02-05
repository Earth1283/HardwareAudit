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

class BenchmarkCommand(private val plugin: HardwareAudit) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()
    private val cpuBenchmark = CpuBenchmark(plugin)
    private val stealBenchmark = StealTimeBenchmark(plugin)
    private val msptBenchmark = MsptBenchmark(plugin)
    private val diskBenchmark = DiskBenchmark(plugin)
    private val memoryBenchmark = MemoryBenchmark(plugin)
    private val passMarkFetcher = PassMarkFetcher(plugin)

    init {
        passMarkFetcher.fetchScores()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", true)) {
            sender.sendMessage(mm.deserialize("<gradient:aqua:blue><bold>HardwareAudit Commands</bold></gradient>"))
            sender.sendMessage(mm.deserialize("<dark_gray>--------------------------------</dark_gray>"))
            sender.sendMessage(mm.deserialize("<yellow>/audit specs</yellow> <gray>- View hardware & JVM details.</gray>"))
            sender.sendMessage(mm.deserialize("<yellow>/audit score</yellow> <gray>- Check PassMark score for this CPU.</gray>"))
            sender.sendMessage(mm.deserialize("<yellow>/audit cpu [sec]</yellow> <gray>- Benchmark CPU performance.</gray>"))
            sender.sendMessage(mm.deserialize("<yellow>/audit steal [sec]</yellow> <gray>- Measure scheduling delay/steal.</gray>"))
            sender.sendMessage(mm.deserialize("<yellow>/audit mspt [sec]</yellow> <gray>- Monitor tick stability (Std Dev).</gray>"))
            sender.sendMessage(mm.deserialize("<yellow>/audit disk</yellow> <gray>- Test Disk I/O speeds (NVMe detection).</gray>"))
            sender.sendMessage(mm.deserialize("<yellow>/audit memory</yellow> <gray>- Test Memory throughput.</gray>"))
            sender.sendMessage(mm.deserialize("<yellow>/audit claims</yellow> <gray>- Run ALL tests to verify host validity.</gray>"))
            sender.sendMessage(mm.deserialize("<dark_gray>--------------------------------</dark_gray>"))
            return true
        }

        when (args[0].lowercase()) {
            "specs" -> {
                sender.sendMessage(mm.deserialize("<green>Collecting hardware specs...</green>"))
                val specs = HardwareInfo.getSpecs()
                sender.sendMessage(mm.deserialize(specs))
                
                // Append score if available
                val cpuName = oshi.SystemInfo().hardware.processor.processorIdentifier.name
                val score = passMarkFetcher.lookup(cpuName)
                if (score != null) {
                    sender.sendMessage(mm.deserialize("<gray>PassMark Score:</gray> <light_purple><bold>$score</bold></light_purple>"))
                }
                
                val roast = Judgement.getCpuNameRemark(cpuName)
                if (roast != null) {
                    sender.sendMessage(mm.deserialize(roast))
                }
            }
            "score" -> {
                 val cpuName = oshi.SystemInfo().hardware.processor.processorIdentifier.name
                 sender.sendMessage(mm.deserialize("<gray>Looking up score for:</gray> <aqua>$cpuName</aqua>..."))
                 val score = passMarkFetcher.lookup(cpuName)
                 if (score != null) {
                     sender.sendMessage(mm.deserialize("<green>PassMark Score:</green> <light_purple><bold>$score</bold></light_purple>"))
                 } else {
                     sender.sendMessage(mm.deserialize("<red>Score not found in cache.</red> <gray>(Try again in a moment if still fetching...)</gray>"))
                 }
            }
            "cpu" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 30
                sender.sendMessage(mm.deserialize("<yellow>Running CPU benchmark for <white>$duration</white> seconds...</yellow>"))
                cpuBenchmark.runCpuTest(duration).thenAccept { score ->
                    sender.sendMessage(mm.deserialize("<green>CPU Benchmark Finished!</green>"))
                    sender.sendMessage(mm.deserialize("<gray>Score:</gray> <gold>${"%.2f".format(score)} ops/sec</gold>"))
                    sender.sendMessage(mm.deserialize(Judgement.getCpuRemark(score)))
                }
            }
            "steal" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 10
                sender.sendMessage(mm.deserialize("<yellow>Measuring Steal Time for <white>$duration</white> seconds...</yellow>"))
                stealBenchmark.measureStealTime(duration).thenAccept { result ->
                     sender.sendMessage(mm.deserialize("<green>Steal Time Analysis Finished!</green>"))
                     
                     // Method 1: OSHI
                     val oshiColor = if (result.oshiSteal > 1.0) "<red>" else "<green>"
                     sender.sendMessage(mm.deserialize("<gray>OSHI Steal:</gray> $oshiColor${"%.2f".format(result.oshiSteal)}%</reset>"))
                     
                     // Method 3: Jitter
                     val jitterColor = if (result.jitterMs > 10.0) "<red>" else "<green>"
                     sender.sendMessage(mm.deserialize("<gray>Avg Scheduler Delay (Jitter):</gray> $jitterColor${"%.2f".format(result.jitterMs)}ms</reset>"))
                     
                     sender.sendMessage(mm.deserialize(Judgement.getStealRemark(result.oshiSteal)))
                }
            }
            "mspt" -> {
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 10
                msptBenchmark.monitorMspt(sender, duration)
            }
            "disk" -> {
                diskBenchmark.runDiskTest(sender)
            }
            "memory" -> {
                memoryBenchmark.runMemoryTest(sender)
            }
            "all" -> {
                onCommand(sender, command, label, arrayOf("specs"))
                onCommand(sender, command, label, arrayOf("cpu", "30")) 
                onCommand(sender, command, label, arrayOf("steal", "10"))
                onCommand(sender, command, label, arrayOf("mspt", "10"))
                onCommand(sender, command, label, arrayOf("disk"))
                onCommand(sender, command, label, arrayOf("memory"))
            }
            "claims" -> {
                sender.sendMessage(mm.deserialize("<yellow>--- Verifying Host Claims ---</yellow>"))
                onCommand(sender, command, label, arrayOf("cpu", "30"))
                onCommand(sender, command, label, arrayOf("disk"))
                onCommand(sender, command, label, arrayOf("memory"))
                onCommand(sender, command, label, arrayOf("steal", "10"))
            }
            else -> sender.sendMessage(mm.deserialize("<red>Unknown subcommand. Use: cpu, steal, mspt, disk, memory, specs, all, claims</red>"))
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String>? {
        if (args.size == 1) {
            return mutableListOf("cpu", "steal", "mspt", "disk", "memory", "specs", "all", "score", "claims").filter { it.startsWith(args[0], true) }.toMutableList()
        }
        if (args.size == 2 && (args[0].equals("cpu", true) || args[0].equals("steal", true) || args[0].equals("mspt", true))) {
             return mutableListOf("10", "30", "60")
        }
        return null
    }
}
