package io.github.Earth1283.utils

import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import oshi.hardware.GlobalMemory
import java.lang.management.ManagementFactory
import java.text.DecimalFormat

object HardwareInfo {
    private val si = SystemInfo()
    private val hal = si.hardware
    private val df = DecimalFormat("#.##")

    fun getSpecs(): String {
        val cpu = hal.processor
        val memory = hal.memory
        val os = si.operatingSystem

        val sb = StringBuilder()
        sb.append("<gray>CPU Model:</gray> <aqua><bold>${cpu.processorIdentifier.name}</bold></aqua>\n")
        sb.append("<gray>Max Frequency:</gray> <yellow>${formatFreq(cpu.maxFreq)}</yellow>\n")
        sb.append("<gray>Physical Cores:</gray> <white>${cpu.physicalProcessorCount}</white> <gray>Logical:</gray> <white>${cpu.logicalProcessorCount}</white>\n")
        sb.append("<gray>Architecture:</gray> <white>${cpu.processorIdentifier.microarchitecture}</white>\n")
        
        sb.append("<gray>Memory:</gray> <white>${formatBytes(memory.available)}</white> / <white>${formatBytes(memory.total)}</white> <gray>(Available/Total)</gray>\n")
        sb.append("<gray>Swap:</gray> <white>${formatBytes(memory.virtualMemory.swapUsed)}</white> / <white>${formatBytes(memory.virtualMemory.swapTotal)}</white>\n")
        
        sb.append("<gray>OS:</gray> <white>$os</white>\n")
        sb.append("<gray>Uptime:</gray> <white>${formatDuration(os.systemUptime)}</white>\n")

        val runtimeMx = ManagementFactory.getRuntimeMXBean()
        sb.append("<gray>JVM Version:</gray> <white>${runtimeMx.vmName} ${runtimeMx.vmVersion}</white>\n")
        
        val args = runtimeMx.inputArguments
        if (args.isNotEmpty()) {
            sb.append("<gray>JVM Flags:</gray>\n")
            args.forEach { arg ->
                sb.append(" <dark_gray>-</dark_gray> <white>$arg</white>\n")
            }
        }

        return sb.toString().trim()
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var i = 0
        var v = bytes.toDouble()
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return "${df.format(v)} ${units[i]}"
    }

    private fun formatFreq(hertz: Long): String {
        return "${df.format(hertz.toDouble() / 1_000_000_000.0)} GHz"
    }

    private fun formatDuration(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val mins = (seconds % 3600) / 60
        return "${days}d ${hours}h ${mins}m"
    }
}
