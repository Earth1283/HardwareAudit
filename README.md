# HardwareAudit

**Audit your summer host's claims against reality.**

A Paper/Spigot plugin designed to aggressively benchmark Minecraft server hardware, expose lies, and provide judgmental feedback on performance.

This plugin works on all SpigotMC forks.

## Features

### 🔥 Aggressive Benchmarking
- **CPU Stress Test**: Uses a **Prime Sieve (Sieve of Eratosthenes)** to saturate the CPU's cache and branch prediction. No lightweight math here.
- **Memory Saturation**: Launches **Multi-Threaded** copy operations to saturate available memory bandwidth across all cores. Upcomming C/JNI implementation soon.
- **Disk I/O**: Abuses C's O_DIRECT to write data directly to the disk to detect slow HDD "spinning rust" or cheap SATA SSDs.
- **Steal Time**: Detects "noisy neighbors" on shared hosts by measuring CPU steal % and thread scheduling jitter.

### 📊 PassMark Integration
- Fetches **Live PassMark Scores** from `cpubenchmark.net` for your specific CPU model.
- Compares your host's hardware against official industry benchmarks.

### 🤬 Judgement Mode
- **Mean Remarks**: The plugin analyzes your scores and provides a "verdict" (e.g., "Hamster Wheel Drive Detected", "Potato CPU").
- **CPU Hate**: Specifically detects and roasts horrendous server CPUs like older **Xeons**, **Atoms**, **Celerons**, and **AMD FX** chips.

### 🛡️ Ban Evasion (Obfuscation)
Hosting providers love to ban benchmark plugins by file hash.
- **Auto-Obfuscation**: Every build generates **3 Unique Variants** (e.g., `-OBFS1.jar`).
- **Method**: Injects random harmless data into the JAR's `META-INF` folder.
- **Result**: Every JAR has a completely unique SHA-256 hash, making hash-based blocking impossible.

## Commands

| Command | Description |
| :--- | :--- |
| `/audit all` | Run **ALL** benchmarks (CPU, RAM, Disk, Steal, MSPT). |
| `/audit specs` | View detailed hardware specs (CPU Model, Cores, RAM, OS) + PassMark Score. |
| `/audit score` | Lookup the official PassMark score for the server's CPU. |
| `/audit cpu [sec]` | Run the Prime Sieve CPU stress test (Default: 30s). |
| `/audit memory` | Run the Multi-Threaded Memory Bandwidth test. |
| `/audit disk` | Run the Sequential Disk I/O test (512MB). |
| `/audit steal [sec]` | Measure CPU Steal Time and Jitter. |
| `/audit mspt [sec]` | Monitor Tick Duration output and Standard Deviation. |
| `/audit claims` | Quickly verify host claims (Cpu + Disk + Memory). |

## Installation
1. Download a release from the [Releases Page](../../releases).
2. Pick **ANY** of the JARs (`CLEAN`, `OBFS1`, `OBFS2`, etc.). They are effectively identical but have different hashes.
3. Drop into `plugins/` folder.
4. Restart server.

## Building
To build locally:
```bash
./gradlew clean shadowJar obfuscateJars
```
Artifacts will be in `build/libs/`.
