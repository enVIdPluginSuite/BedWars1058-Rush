import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.etran"
version = providers.gradleProperty("pluginVersion").get()

val spigotApiVersion = "1.8.8-R0.1-SNAPSHOT"
val bedwarsApiVersion = providers.gradleProperty("bedwarsApiVersion").get()
val bedwarsApiJar = providers.gradleProperty("bedwarsApiJar").orNull

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        name = "spigot"
        content { includeGroup("org.spigotmc") }
    }
    maven("https://repo.andrei1058.dev/releases/") {
        name = "andrei1058"
        content { includeGroup("com.andrei1058.bedwars") }
    }
}

dependencies {
    // We compile against the OLDEST supported API (1.8.8) so that nothing newer than 1.8 can sneak into the code.
    // Anything that differs between Minecraft versions is resolved by name at runtime (see the `compat` package).
    // (Non-transitive: its old BungeeCord-chat snapshot is no longer hosted and this plugin only needs org.bukkit.)
    compileOnly("org.spigotmc:spigot-api:$spigotApiVersion") { isTransitive = false }

    // BedWars1058 API: explicit jar > ./libs/*.jar > Maven artifact
    val localJars = fileTree("libs") { include("*.jar") }
    when {
        bedwarsApiJar != null -> compileOnly(files(bedwarsApiJar))
        !localJars.isEmpty -> compileOnly(localJars)
        else -> compileOnly("com.andrei1058.bedwars:bedwars-api:$bedwarsApiVersion")
    }

    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
    testImplementation("org.spigotmc:spigot-api:$spigotApiVersion") { isTransitive = false }
    testImplementation("org.yaml:snakeyaml:1.15")
    testImplementation("com.google.guava:guava:17.0")
    testImplementation("commons-lang:commons-lang:2.6")
}

kotlin {
    compilerOptions {
        // Minecraft 1.8 servers run on Java 8, so emit Java 8 bytecode and check against the Java 8 class library.
        jvmTarget = JvmTarget.JVM_1_8
        freeCompilerArgs.addAll(
            "-Xjdk-release=8",
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    // The shaded jar is the only artifact worth installing.
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("BedWars1058-Rush-${project.version}.jar")
    // Kotlin's stdlib has to ship inside the plugin (1.8-1.15 servers cannot download libraries), so move it out of
    // the way of every other plugin that also bundles Kotlin.
    relocate("kotlin", "dev.etran.bedwarsrush.libs.kotlin")
    relocate("kotlinx", "dev.etran.bedwarsrush.libs.kotlinx")
    relocate("org.jetbrains.annotations", "dev.etran.bedwarsrush.libs.jetbrains.annotations")
    relocate("org.intellij.lang.annotations", "dev.etran.bedwarsrush.libs.intellij.annotations")
    exclude("DebugProbesKt.bin", "META-INF/maven/**", "META-INF/*.version")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
