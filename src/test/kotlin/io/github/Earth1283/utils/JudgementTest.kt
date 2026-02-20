package io.github.Earth1283.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JudgementTest {

    @Test
    fun testGetCpuRemark() {
        val beast = Judgement.getCpuRemark(300.0)
        assertTrue(beast.contains("Absolute Beast"), "Expected 'Absolute Beast' for 300 ops/sec")

        val garbage = Judgement.getCpuRemark(1.0)
        assertTrue(garbage.contains("Literal Garbage"), "Expected 'Literal Garbage' for 1 ops/sec")
    }

    @Test
    fun testGetDiskRemark() {
        val nvme = Judgement.getDiskRemark(4000.0)
        assertTrue(nvme.contains("Gen4 NVMe"), "Expected 'Gen4 NVMe' for 4000 MB/s")

        val rust = Judgement.getDiskRemark(50.0)
        assertTrue(rust.contains("SD Card?"), "Expected 'SD Card?' for 50 MB/s")
    }

    @Test
    fun testStripTags() {
        val tagged = "<red>Hello <bold>World</bold></red>"
        val stripped = Judgement.stripTags(tagged)
        assertEquals("Hello World", stripped)
    }

    @Test
    fun testGetJvmRemark() {
        val badFlags = listOf("-Xmx4G", "-Xms2G", "-XX:+UseG1GC")
        val remark = Judgement.getJvmRemark(badFlags)
        assertTrue(remark?.contains("Aikar is crying") == true, "Expected Aikar warning for Xmx != Xms")

        val goodFlags = listOf("-Xmx4G", "-Xms4G", "UseG1GC")
        val goodRemark = Judgement.getJvmRemark(goodFlags)
        assertTrue(goodRemark?.contains("Flags look solid") == true, "Expected 'Flags look solid' for correct flags")
    }
}
