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
    compileOnly("com.destroystokyo.paper:paper-api:1.13.2-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))
    implementation("com.github.oshi:oshi-core:6.6.5")
    implementation("net.kyori:adventure-platform-bukkit:4.3.4")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-text-serializer-plain:4.17.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
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
    relocate("net.kyori", "io.github.earth1283.hardwareaudit.shadow.kyori")
    // JNA relocation causes UnsatisfiedLinkError because native libs aren't moved/found correctly
    // relocate("com.sun.jna", "io.github.earth1283.hardwareaudit.shadow.jna")

    // Minimize the jar - this will remove unused classes from dependencies
    minimize {
        // We might need to keep some classes if they're used dynamically (e.g. by OSHI or Adventure)
        exclude(dependency("com.github.oshi:oshi-core:.*"))
        exclude(dependency("net.kyori:.*:.*"))
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.runServer {
    minecraftVersion("1.19.4")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// Custom task to generate obfuscated variants
tasks.register("obfuscateJars") {
    description = "Generates multiple variants of the shadowJar with random data injected into META-INF."
    group = "build"
    dependsOn(tasks.shadowJar)
    
    val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
    val libsDir = shadowJarFile.parentFile

    doLast {
        if (!shadowJarFile.exists()) {
            throw GradleException("Could not find shadowJar at ${shadowJarFile.absolutePath}")
        }

        println("Base JAR: ${shadowJarFile.name}")

        // 1. Create CLEAN variant
        val cleanJar = libsDir.resolve(shadowJarFile.name.replace(".jar", "-CLEAN.jar"))
        shadowJarFile.copyTo(cleanJar, overwrite = true)
        println("Created: ${cleanJar.name}")

        // 2. Create OBFS variants
        for (i in 1..3) {
            val obfsJar = libsDir.resolve(shadowJarFile.name.replace(".jar", "-OBFS$i.jar"))
            
            // Inject random data safely into the ZIP structure
            try {
                val tempJar = libsDir.resolve("temp-${obfsJar.name}")
                ZipInputStream(shadowJarFile.inputStream()).use { zis ->
                    ZipOutputStream(tempJar.outputStream()).use { zos ->
                        // Copy existing entries
                        var entry = zis.nextEntry
                        while (entry != null) {
                            zos.putNextEntry(entry)
                            zis.copyTo(zos)
                            zos.closeEntry()
                            entry = zis.nextEntry
                        }
                        
                        // Add random entry to make the JAR unique (hash-wise)
                        val randomBytes = ByteArray(1024 * (1..50).random()) // 1-50KB
                        Random().nextBytes(randomBytes)
                        val randomName = "META-INF/obfuscation-${UUID.randomUUID()}.bin"
                        
                        zos.putNextEntry(ZipEntry(randomName))
                        zos.write(randomBytes)
                        zos.closeEntry()
                    }
                }
                
                if (obfsJar.exists()) obfsJar.delete()
                Files.move(tempJar.toPath(), obfsJar.toPath())
                
                println("Created: ${obfsJar.name} (Injected random data)")
            } catch (e: Exception) {
                println("Failed to obfuscate ${obfsJar.name}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
