package io.github.Earth1283.utils

import io.github.Earth1283.HardwareAudit
import org.bukkit.Bukkit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class PassMarkFetcher(private val plugin: HardwareAudit) {

    private val scores = ConcurrentHashMap<String, String>()
    private var isLoaded = false
    private var isFetching = false

    fun fetchScores() {
        if (isLoaded || isFetching) return
        isFetching = true
        plugin.logger.info("Starting background fetch of PassMark scores...")

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val url = URL("https://www.cpubenchmark.net/cpu_list.php")
                val con = url.openConnection() as HttpURLConnection
                con.requestMethod = "GET"
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                
                if (con.responseCode != 200) {
                    plugin.logger.warning("Failed to fetch PassMark scores: HTTP ${con.responseCode}")
                    isFetching = false
                    return@Runnable
                }

                val reader = BufferedReader(InputStreamReader(con.inputStream))
                // Regex to find: <a href="...cpu=Name...">Name</a></td><td>Score</td>
                // The dump showed: <a href="/cpu_lookup.php?cpu=Intel+Core+i3-4030U+%40+1.90GHz&amp;id=2277">Intel Core i3-4030U @ 1.90GHz</a></td><td>1,869</td>
                val pattern = Pattern.compile("<a href=\"[^\"]*cpu_lookup\\.php\\?cpu=[^\"]*\">([^<]+)</a></td><td>([0-9,]+)</td>")
                
                var line: String?
                var count = 0
                while (reader.readLine().also { line = it } != null) {
                    val matcher = pattern.matcher(line)
                    if (matcher.find()) {
                        val name = matcher.group(1)
                        val score = matcher.group(2).replace(",", "")
                        scores[normalize(name)] = score
                        count++
                    }
                }
                reader.close()
                isLoaded = true
                plugin.logger.info("Successfully loaded $count PassMark scores.")
            } catch (e: Exception) {
                plugin.logger.warning("Error fetching PassMark scores: ${e.message}")
            } finally {
                isFetching = false
            }
        })
    }

    fun lookup(cpuName: String): String? {
        val normalized = normalize(cpuName)
        // Direct match attempt
        if (scores.containsKey(normalized)) return scores[normalized]
        
        // Fuzzy / Partial match? 
        // Sometimes OSHI has "Intel(R) Core(TM)..." and PassMark has "Intel Core..."
        // The normalize function should handle most of this.
        
        // If exact normalized match fails, try finding one that contains the other?
        // This might arguably match wrong CPUs, but it's better than nothing.
        // Let's iterate if not found.
        
        val key = scores.keys.find { it.equals(normalized, ignoreCase = true) }
        if (key != null) return scores[key]

        // Try 'contains' for model numbers e.g. "i7-7700K"
        // But be careful not to match "i7-7700" to "i7-7700K"
        
        return null
    }

    private fun normalize(name: String): String {
        return name.replace("(R)", "")
            .replace("(TM)", "")
            .replace("CPU", "")
            .replace("@", "")
            .replace(Regex("\\s+"), " ") // collapse spaces
            .trim()
            .lowercase()
    }
    
    fun isReady(): Boolean = isLoaded
}
