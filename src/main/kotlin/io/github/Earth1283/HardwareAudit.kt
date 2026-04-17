package io.github.Earth1283

import io.github.Earth1283.commands.BenchmarkCommand
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin

class HardwareAudit : JavaPlugin() {
    
    val mm = MiniMessage.miniMessage()
    lateinit var adventure: BukkitAudiences

    override fun onEnable() {
        adventure = BukkitAudiences.create(this)
        logger.info("HardwareAudit is starting up...")
        
        // Register commands
        val benchmarkCommand = BenchmarkCommand(this)
        getCommand("audit")?.setExecutor(benchmarkCommand)
        getCommand("audit")?.tabCompleter = benchmarkCommand
        
        logger.info("HardwareAudit enabled successfully!")
    }

    override fun onDisable() {
        if (this::adventure.isInitialized) {
            adventure.close()
        }
        logger.info("HardwareAudit disabled.")
    }
}
