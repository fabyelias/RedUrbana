plugins {
    id("java-library")
    alias(libs.plugins.kotlin.android) apply false
    kotlin("jvm")
}

// Módulo de dominio puro: sin dependencias de Android ni de ningún proveedor de datos.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation("javax.inject:javax.inject:1")
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(project(":core:core-testing"))
}

kotlin {
    jvmToolchain(17)
}
