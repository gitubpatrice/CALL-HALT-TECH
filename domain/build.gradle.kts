import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The detection logic, in plain Kotlin/JVM (docs/ARCHITECTURE.md, D-001). This module has NO dependency
// but the Kotlin standard library: it cannot reach Android, a clock or a disk, so every rule of the
// method (PROJET.md, § 3) is a unit test that runs in milliseconds on any machine.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
    // `suspend` needs no library in main; the use case's test runs it with runTest.
    testImplementation(libs.coroutines.test)
}
