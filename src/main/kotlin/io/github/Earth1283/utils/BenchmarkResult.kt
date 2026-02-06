package io.github.Earth1283.utils

import net.kyori.adventure.text.Component

data class BenchmarkResult(
    val name: String,
    val score: String,
    val judgement: String,
    val details: Component
)
