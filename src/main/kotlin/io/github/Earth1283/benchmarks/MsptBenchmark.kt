package io.github.Earth1283.benchmarks

import io.github.Earth1283.HardwareAudit
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import kotlin.math.pow
import kotlin.math.sqrt

class MsptBenchmark(private val plugin: HardwareAudit) {

    private val mm = MiniMessage.miniMessage()

    fun monitorMspt(sender: CommandSender, durationSeconds: Int) {
        val samples = ArrayList<Double>()
        var ticks = 0
        val expectedTicks = durationSeconds * 20
        var lastTickTime = System.nanoTime()

        sender.sendMessage(mm.deserialize("<yellow>Monitoring MSPT for <white>$durationSeconds</white> seconds...</yellow>"))

        var taskId = -1

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            val now = System.nanoTime()
            val mspt = (now - lastTickTime) / 1_000_000.0
            
            // Skip the very first delta as it includes the scheduling delay of the first run
            if (ticks > 0) {
                samples.add(mspt)
            }
            lastTickTime = now
            ticks++

            if (ticks > expectedTicks + 1) { // +1 for the warm up tick
                Bukkit.getScheduler().cancelTask(taskId)
                report(sender, samples)
            }
        }, 0L, 1L)
    }

    private fun report(sender: CommandSender, samples: List<Double>) {
        if (samples.isEmpty()) {
            sender.sendMessage(mm.deserialize("<red>No samples collected!</red>"))
            return
        }

        val min = samples.minOrNull() ?: 0.0
        val max = samples.maxOrNull() ?: 0.0
        val avg = samples.average()
        
        // Calculate Standard Deviation
        // SD = sqrt( sum((x - mean)^2) / N )
        val variance = samples.sumOf { (it - avg).pow(2) } / samples.size
        val stdDev = sqrt(variance)

        sender.sendMessage(mm.deserialize("<green>MSPT Analysis Finished!</green>"))
        sender.sendMessage(mm.deserialize("<gray>Samples:</gray> <white>${samples.size}</white>"))
        sender.sendMessage(mm.deserialize("<gray>Min:</gray> <green>${"%.2f".format(min)}ms</green>"))
        sender.sendMessage(mm.deserialize("<gray>Max:</gray> <red>${"%.2f".format(max)}ms</red>"))
        sender.sendMessage(mm.deserialize("<gray>Avg:</gray> <yellow>${"%.2f".format(avg)}ms</yellow>"))
        
        val stdDevColor = if (stdDev < 5.0) "<green>" else if (stdDev < 15.0) "<yellow>" else "<red>"
        sender.sendMessage(mm.deserialize("<gray>Std Dev:</gray> $stdDevColor${"%.2f".format(stdDev)}ms</reset>"))
        sender.sendMessage(mm.deserialize(io.github.Earth1283.utils.Judgement.getMsptRemark(stdDev)))
    }
}
