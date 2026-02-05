package io.github.Earth1283

import io.github.Earth1283.commands.BenchmarkCommand
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin

class HardwareAudit : JavaPlugin() {
    
    val mm = MiniMessage.miniMessage()

    override fun onEnable() {
        logger.info("HardwareAudit is starting up...")
        
        // Register commands
        val benchmarkCommand = BenchmarkCommand(this)
        getCommand("audit")?.setExecutor(benchmarkCommand)
        getCommand("audit")?.tabCompleter = benchmarkCommand
        
        logger.info("HardwareAudit enabled successfully!")
    }

    override fun onDisable() {
        logger.info("HardwareAudit disabled.")
    }
}
