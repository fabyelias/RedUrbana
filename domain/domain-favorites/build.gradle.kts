plugins {
    kotlin("jvm")
}

// Módulo de dominio puro. TODO: modelos y UseCases de favorites (roadmap paso 3+).
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(17)
}
