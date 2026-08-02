plugins {
    kotlin("jvm")
}

// Módulo de dominio puro. TODO: modelos y UseCases de alerts (roadmap paso 3+).
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
