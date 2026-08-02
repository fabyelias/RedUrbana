plugins {
    kotlin("jvm")
}

// Fakes y utilidades de test compartidas entre módulos, ej: FakeTransportDataProvider.
dependencies {
    implementation(project(":domain:domain-transport"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.turbine)
    implementation(libs.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
