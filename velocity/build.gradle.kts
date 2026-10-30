plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":core"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.velocity.api)
    testImplementation(platform(libs.junit6.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.shadowJar {
    archiveBaseName.set("RadarLink-Velocity")
}
