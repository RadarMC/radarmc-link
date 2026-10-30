plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":core"))
    compileOnly(libs.bungeecord.api)

    testImplementation(libs.bungeecord.api)
    testImplementation(platform(libs.junit6.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("bungee.yml") {
        expand("version" to version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("RadarLink-Bungee")
}
