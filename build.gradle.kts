plugins {
    kotlin("jvm") version "2.3.20-Beta2"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.Random
import java.util.UUID
import java.nio.file.Files

group = "io.github.Earth1283"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))
    implementation("com.github.oshi:oshi-core:6.6.5")
}

tasks.shadowJar {
    archiveClassifier.set("all")
    // Exclude unnecessary JNA native libraries to reduce JAR size
    val excludedPlatforms = listOf(
        "aix-ppc", "aix-ppc64", "freebsd-aarch64", "freebsd-x86", "freebsd-x86-64",
        "linux-arm", "linux-armel", "linux-mips64el", "linux-ppc", "linux-ppc64le",
        "linux-s390x", "linux-x86", "openbsd-x86", "openbsd-x86-64", "sunos-sparc",
        "sunos-sparcv9", "sunos-x86", "sunos-x86-64", "win32-x86", "linux-loongarch64",
        "linux-riscv64", "dragonflybsd-x86-64"
    )
    for (platform in excludedPlatforms) {
        exclude("com/sun/jna/$platform/**")
    }
    
    // Relocate dependencies to avoid conflicts with other plugins
    relocate("com.github.oshi", "io.github.earth1283.hardwareaudit.shadow.oshi")
    relocate("oshi", "io.github.earth1283.hardwareaudit.shadow.oshi.core")
    // JNA relocation causes UnsatisfiedLinkError because native libs aren't moved/found correctly
    // relocate("com.sun.jna", "io.github.earth1283.hardwareaudit.shadow.jna")

    // Minimize the jar - this will remove unused classes from dependencies
    minimize()
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Custom task to generate obfuscated variants
tasks.register("obfuscateJars") {
    dependsOn("shadowJar")
    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val libsDir = File(buildDir, "libs")
        // Find the shadow jar (usually has -all or just version)
        val shadowJarFile = libsDir.listFiles()?.find { 
            it.name.endsWith(".jar") && !it.name.contains("-plain") && !it.name.contains("CLEAN") && !it.name.contains("OBFS")
        } ?: throw GradleException("Could not find shadowJar in $libsDir")

        println("Base JAR: ${shadowJarFile.name}")

        // 1. Create CLEAN variant
        val cleanJar = File(libsDir, shadowJarFile.name.replace(".jar", "-CLEAN.jar"))
        shadowJarFile.copyTo(cleanJar, overwrite = true)
        println("Created: ${cleanJar.name}")

        // 2. Create OBFS variants
        for (i in 1..3) {
            val obfsJar = File(libsDir, shadowJarFile.name.replace(".jar", "-OBFS$i.jar"))
            shadowJarFile.copyTo(obfsJar, overwrite = true)
            
            // Inject random data safely into the ZIP structure
            try {
                // Use ZipOutputStream to append an entry instead of complex FileSystem handling
                // This is simpler and less prone to unresolved reference issues in Gradle Kotlin DSL
                val tempJar = File(libsDir, "temp-${obfsJar.name}")
                ZipInputStream(obfsJar.inputStream()).use { zis ->
                    ZipOutputStream(tempJar.outputStream()).use { zos ->
                        // Copy existing entries
                        var entry = zis.nextEntry
                        while (entry != null) {
                            zos.putNextEntry(entry)
                            zis.copyTo(zos)
                            zos.closeEntry()
                            entry = zis.nextEntry
                        }
                        
                        // Add random entry
                        val randomBytes = ByteArray(1024 * (1..50).random()) // 1-50KB
                        Random().nextBytes(randomBytes)
                        val randomName = "META-INF/obfuscation-${UUID.randomUUID()}.bin"
                        
                        zos.putNextEntry(ZipEntry(randomName))
                        zos.write(randomBytes)
                        zos.closeEntry()
                    }
                }
                
                // Replace original with temp
                obfsJar.delete()
                // Use java.nio.file.Files.move for better reliability/exception reporting
                Files.move(tempJar.toPath(), obfsJar.toPath())
                
                println("Created: ${obfsJar.name} (Injected random data)")
            } catch (e: Exception) {
                println("Failed to obfuscate ${obfsJar.name}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
