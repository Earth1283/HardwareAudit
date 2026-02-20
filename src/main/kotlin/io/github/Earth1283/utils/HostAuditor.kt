package io.github.Earth1283.utils

import io.github.Earth1283.benchmarks.DiskBenchmark
import io.github.Earth1283.benchmarks.NetworkBenchmark
import io.github.Earth1283.benchmarks.StealTimeBenchmark
import java.util.concurrent.CompletableFuture
import java.util.Collections
import java.util.ArrayList

class HostAuditor(
    private val stealBenchmark: StealTimeBenchmark,
    private val diskBenchmark: DiskBenchmark,
    private val networkBenchmark: NetworkBenchmark
) {

    fun audit(onProgress: (String) -> Unit): CompletableFuture<List<BenchmarkResult>> {
        val results = Collections.synchronizedList(ArrayList<BenchmarkResult>())
        
        return stealBenchmark.measureStealTime(10).thenCompose { res ->
            results.add(res)
            onProgress("<gray>[1/3] Disk I/O check...</gray>")
            diskBenchmark.runDiskTest()
        }.thenCompose { res ->
            results.add(res)
            onProgress("<gray>[2/3] Bandwidth check...</gray>")
            networkBenchmark.runNetworkTest()
        }.thenApply { res ->
            results.add(res)
            onProgress("<gray>[3/3] Analyzing results...</gray>")
            results
        }
    }
}
