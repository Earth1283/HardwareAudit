package io.github.Earth1283.utils

import net.kyori.adventure.text.minimessage.MiniMessage

object Judgement {
    private val mm = MiniMessage.miniMessage()

    fun getCpuRemark(opsPerSec: Double): String {
        if (opsPerSec > 250) return "<green>Absolute Beast. Single-core monster.</green>"
        if (opsPerSec > 150) return "<green>Great. Perfect for high-performance Minecraft.</green>"
        if (opsPerSec > 75) return "<yellow>Decent. It'll get the job done, mostly.</yellow>"
        if (opsPerSec > 25) return "<yellow>Mediocre. Your host is likely using a shared thread from 2012.</yellow>"
        if (opsPerSec > 5) return "<red>Pathetic. Expect a slide-show when someone breaks a block.</red>"
        return "<red><bold>Literal Garbage.</bold> My smart fridge has more single-core performance than this e-waste.</red>"
    }

    fun getMultiCpuRemark(opsPerSec: Double, cores: Int): String {
        val totalScore = opsPerSec
        if (totalScore > 2000) return "<green>Insane throughput. This machine is a powerhouse.</green>"
        if (totalScore > 1000) return "<green>Solid multi-core performance.</green>"
        if (totalScore > 500) return "<yellow>Respectable for a mid-range server.</yellow>"
        if (totalScore > 250) return "<red>Weak multi-core. Fine for one server, bad for a network.</red>"
        return "<red><bold>Total Junk.</bold> Your host is overselling this node so hard it's legally fraud.</red>"
    }

    fun getCpuNameRemark(name: String): String? {
        val n = name.lowercase()
        if (n.contains("xeon")) {
             if (n.contains("e5-") || n.contains("e3-")) return "<red><b>Ancient Xeon:</b> This CPU belongs in a scrapyard, not a server rack.</red>"
             return "<yellow><b>Xeon:</b> Solid for multi-tasking, but check that single-thread speed.</yellow>"
        }
        if (n.contains("gold") || n.contains("silver") || n.contains("platinum")) return "<green><b>Scalable Xeon:</b> Enterprise grade, but clock speeds might be low.</green>"
        if (n.contains("ryzen") || n.contains("epyc") || n.contains("threadripper")) return "<green><b>Modern AMD:</b> Now we're talking. Performance per dollar is king here.</green>"
        if (n.contains("atom")) return "<red><b>Atom:</b> This is literally a netbook CPU. Why?</red>"
        if (n.contains("celeron") || n.contains("pentium")) return "<red><b>E-Waste:</b> Please stop hosting on this. For everyone's sake.</red>"
        if (n.contains("fx-")) return "<red><b>AMD FX:</b> A space heater that occasionally calculates things.</red>"
        return null
    }

    fun getDiskRemark(mbPerSec: Double): String {
        if (mbPerSec > 10000) return "<green><bold>MY EYES!</bold> THE CONTROLLER IS MELTING! CALL THE FIRE DEPARTMENT!</green>"
        if (mbPerSec > 5000) return "<green>Okay, we're actually partying now. This SSD is sweating.</green>"
        if (mbPerSec > 3000) return "<green>Gen4 NVMe? Blazing fast. Your OS is finally happy.</green>"
        if (mbPerSec > 1000) return "<yellow>Slightly faster than a carrier pigeon. Still boring.</yellow>"
        if (mbPerSec > 500) return "<yellow>SATA SSD. Decent, but we're here to party, not fill out tax forms.</yellow>"
        if (mbPerSec > 100) return "<red>Spinning Rust. Your disk is actually just a guy with a pen and paper. Embarrassing.</red>"
        return "<red><bold>SD Card?</bold> This disk speed is pathologically slow. Stop hosting on a calculator.</red>"
    }

    fun getMemoryRemark(mbPerSec: Double): String {
        if (mbPerSec > 25000) return "<green>DDR5 levels of speed. Excellent.</green>"
        if (mbPerSec > 15000) return "<green>DDR4 Dual Channel. Solid.</green>"
        if (mbPerSec > 5000) return "<yellow>Sluggish RAM. Probably single-channel garbage.</yellow>"
        return "<red>Crawl-speed memory. This bottleneck is offensive.</red>"
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
        var g1gc = false
        var zgc = false
        var shenandoah = false
        var parallel = false
        
        for (arg in args) {
            if (arg.startsWith("-Xmx")) xmx = arg
            if (arg.startsWith("-Xms")) xms = arg
            if (arg.contains("UseG1GC")) g1gc = true
            if (arg.contains("UseZGC")) zgc = true
            if (arg.contains("UseShenandoahGC")) shenandoah = true
            if (arg.contains("UseParallelGC")) parallel = true
        }
        
        if (parallel) return "<red><b>ParallelGC Detected:</b> Ancient GC for Minecraft. Use G1GC (or ZGC/Shenandoah).</red>"
        if (!g1gc && !zgc && !shenandoah) return "<yellow><b>Generic GC:</b> Consider using Aikar's flags or G1GC for better TPS stability.</yellow>"
        if (xmx != null && xms != null && xmx != xms) return "<red><b>Aikar is crying:</b> Xmx != Xms. This causes heap resize lag. Set them equal!</red>"
        
        return "<green>JVM Flags look solid.</green>"
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
