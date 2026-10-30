plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":bukkit"))
    implementation(project(":velocity"))
    implementation(project(":bungee"))
}

tasks.shadowJar {
    archiveBaseName.set("RadarLink")
}
