plugins {
    `java-library`
    `java-test-fixtures`
}

val generatedSources = layout.buildDirectory.dir("generated/sources/buildinfo")

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val version = project.version.toString()
    inputs.property("version", version)
    outputs.dir(generatedSources)
    doLast {
        val file = generatedSources.get().file("org/radarmc/link/core/BuildInfo.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package org.radarmc.link.core;
            |
            |public final class BuildInfo {
            |    public static final String VERSION = "$version";
            |
            |    private BuildInfo() {}
            |}
            |""".trimMargin()
        )
    }
}

sourceSets.main {
    java.srcDir(generatedSources)
}

tasks.compileJava {
    dependsOn(generateBuildInfo)
}

dependencies {
    implementation(libs.gson)
    implementation(libs.snakeyaml)

    testFixturesImplementation(libs.gson)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

val testJava8 = tasks.register<Test>("testJava8") {
    description = "Runs the core tests on Java 8."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(testJava8)
}
