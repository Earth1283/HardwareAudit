package io.github.Earth1283.utils

import net.kyori.adventure.text.minimessage.MiniMessage

object Judgement {
    private val mm = MiniMessage.miniMessage()

    fun getCpuRemark(opsPerSec: Double): String {
        // Sieve passes per second.
        // 5000+ is insane
        // 2000+ is good
        // 500+ is ok
        if (opsPerSec > 5000) return "<green>Absolute Beast. NASA called, they want their PC back.</green>"
        if (opsPerSec > 2000) return "<green>Respectable. It can run Crysis.</green>"
        if (opsPerSec > 500) return "<yellow>Average. Just like your hosting provider's stupidity.</yellow>"
        if (opsPerSec > 100) return "<red>Slow. My grandma types faster than this CPU calculates.</red>"
        return "<red><bold>Potato Detected.</bold> My pocket calculator is faster. Upgrade immediately.</red>"
    }

    fun getCpuNameRemark(name: String): String? {
        val n = name.lowercase()
        if (n.contains("xeon")) return "<red><b>Xeon Detected:</b> Ancient server junk? Single-thread performance is critical for Minecraft, not 50 slow cores.</red>"
        if (n.contains("atom")) return "<red><b>Atom Detected:</b> Is this running on a toaster or a netbook?</red>"
        if (n.contains("celeron")) return "<red><b>Celeron Detected:</b> E-waste. Please recycle.</red>"
        if (n.contains("pentium")) return "<red><b>Pentium Detected:</b> What year is it? 2005?</red>"
        if (n.contains("opteron")) return "<red><b>Opteron Detected:</b> This belongs in a museum, not a datacenter.</red>"
        if (n.contains("a-series")) return "<red><b>AMD A-Series:</b> Integrated graphics won't save you here.</red>"
        if (n.contains("fx-")) return "<red><b>AMD FX:</b> Hope you have a fire extinguisher handy.</red>"
        return null
    }

    fun getDiskRemark(mbPerSec: Double): String {
        if (mbPerSec > 2000) return "<green>Blazing fast. NVMe goodness.</green>"
        if (mbPerSec > 500) return "<yellow>Decent. Probably a SATA SSD.</yellow>"
        if (mbPerSec > 100) return "<red>Spinning Rust (HDD) detected. Welcome to 2010.</red>"
        return "<red><bold>Hamster Wheel Drive.</bold> Are you saving data to a floppy disk?</red>"
    }

    fun getMemoryRemark(mbPerSec: Double): String {
        if (mbPerSec > 10000) return "<green>Lightning fast memory.</green>"
        if (mbPerSec > 5000) return "<yellow>It works.</yellow>"
        return "<red>Is this RAM or just a really fast swap file?</red>"
    }

    fun getStealRemark(stealPercent: Double): String {
        if (stealPercent < 1.0) return "<green>Clean. No noisy neighbors.</green>"
        if (stealPercent < 5.0) return "<yellow>Bit crowded. Requires social distancing.</yellow>"
        if (stealPercent < 20.0) return "<red>Overcrowded. Your host is scamming you.</red>"
        return "<red><bold>CRITICAL.</bold> Your CPU is stolen property. Call the police.</red>"
    }
    
    fun getMsptRemark(stdDev: Double): String {
        if (stdDev < 2.0) return "<green>Smooth as butter.</green>"
        if (stdDev < 10.0) return "<yellow>A bit jittery. Have you tried turning it off and on again?</yellow>"
        return "<red>Parkinson's Simulator. The lag spikes are real.</red>"
    }

    fun getJvmRemark(args: List<String>): String? {
        var xmx: String? = null
        var xms: String? = null
        var parallel = false
        
        for (arg in args) {
            if (arg.startsWith("-Xmx")) xmx = arg
            if (arg.startsWith("-Xms")) xms = arg
            if (arg.contains("UseParallelGC")) parallel = true
        }
        
        if (parallel) return "<red><b>ParallelGC Detected:</b> Do you enjoy lag spikes? Use G1GC or ZGC.</red>"
        if (xmx != null && xms != null && xmx != xms) return "<red><b>Aikar is crying:</b> Xmx != Xms. This causes heap resize lag. Fix your flags.</red>"
        if (xmx == null) return "<red><b>No Flags Detected:</b> Running naked? You're brave.</red>"
        
        return "<green>JVM Flags look decent. (Or you hid them well).</green>"
    }
    fun getNetworkRemark(mbps: Double): String {
        if (mbps > 500) return "<green>Fiber optic? Blazing fast.</green>"
        if (mbps > 100) return "<green>Acceptable datacenter speeds.</green>"
        if (mbps > 25) return "<yellow>Home internet detected. Hosting from your bedroom?</yellow>"
        return "<red><b>Dial-up Detected.</b> Are you using a carrier pigeon?</red>"
    }

    fun stripTags(message: String): String {
        return mm.stripTags(message)
    }
}
