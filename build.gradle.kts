import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.spotless) apply false
}

val shippedRelease = 8
val platformTestRelease = 21
val lintWithoutObsoleteReleaseWarning = "-Xlint:all,-processing,-serial,-options"
val relocatedLibraries = "org.radarmc.link.lib"
val palantirVersion = libs.versions.palantir.java.format.get()

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    extensions.configure<SpotlessExtension> {
        java {
            target("src/*/java/**/*.java")
            palantirJavaFormat(palantirVersion)
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(shippedRelease)
        options.compilerArgs.addAll(listOf(lintWithoutObsoleteReleaseWarning, "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        maxParallelForks = 1
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    plugins.withId("com.gradleup.shadow") {
        tasks.named<Jar>("jar") {
            archiveClassifier.set("plain")
        }
        tasks.named<ShadowJar>("shadowJar") {
            archiveClassifier.set("")
            relocate("com.google.gson", "$relocatedLibraries.gson")
            relocate("org.yaml.snakeyaml", "$relocatedLibraries.snakeyaml")
            exclude(
                "META-INF/maven/**",
                "META-INF/proguard/**",
                "META-INF/versions/**",
                "module-info.class",
                "META-INF/*.kotlin_module",
            )
            dependencies {
                exclude(dependency("com.google.errorprone:.*"))
            }
            mergeServiceFiles()
            manifest {
                attributes("paperweight-mappings-namespace" to "mojang")
            }
        }
        tasks.named("assemble") {
            dependsOn("shadowJar")
        }
    }
}

configure(listOf(project(":bukkit"), project(":velocity"), project(":bungee"))) {
    tasks.named<JavaCompile>("compileTestJava") {
        options.release.set(platformTestRelease)
    }
    listOf("testCompileClasspath", "testRuntimeClasspath").forEach { name ->
        configurations.named(name) {
            attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, platformTestRelease)
        }
    }
}

tasks.register<Copy>("pluginJars") {
    group = "build"
    description = "Collects the platform jars into build/plugins."
    listOf("bukkit", "velocity", "bungee", "universal").forEach { name ->
        from(project(":$name").tasks.named("shadowJar"))
    }
    into(layout.buildDirectory.dir("plugins"))
}
