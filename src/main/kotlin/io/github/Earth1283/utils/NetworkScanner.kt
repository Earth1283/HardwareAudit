package io.github.Earth1283.utils

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import oshi.SystemInfo
import oshi.software.os.OSProcess

data class NetworkScanResult(
    val targetIp: String,
    val neighborProcesses: List<OSProcess>,
    val openPorts: List<Int>,
    val isLocal: Boolean
)

class NetworkScanner(private val plugin: JavaPlugin) {
    
    fun scan(targetIp: String): CompletableFuture<NetworkScanResult> {
        val future = CompletableFuture<NetworkScanResult>()
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val isLocal = targetIp == "127.0.0.1" || targetIp == "localhost"
            
            // 1. Process Scan (Only if local)
            val neighborProcesses = if (isLocal) {
                val os = SystemInfo().operatingSystem
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

            // 2. Port Scan
            val openPorts = Collections.synchronizedList(ArrayList<Int>())
            // Use a dedicated pool for this scan to avoid blocking common pools
            val portExecutor = Executors.newFixedThreadPool(128) 
            val timeout = if (isLocal) 150 else 500
            val latch = java.util.concurrent.CountDownLatch(65535 - 10000 + 1)

            for (port in 10000..65535) {
                portExecutor.execute {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(targetIp, port), timeout)
                            // Verified MC: Send Legacy SLP (0xFE 0x01)
                            socket.outputStream.write(byteArrayOf(0xFE.toByte(), 0x01.toByte()))
                            socket.soTimeout = 200
                            if (socket.inputStream.read() == 0xFF) {
                                openPorts.add(port)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore connection failures
                    } finally {
                        latch.countDown()
                    }
                }
            }

            try {
                latch.await(60, TimeUnit.SECONDS)
            } catch (e: Exception) {
                // Timeout
            }
            portExecutor.shutdownNow()
            
            future.complete(NetworkScanResult(targetIp, neighborProcesses, openPorts, isLocal))
        })
        
        return future
    }
}
