plugins {
    kotlin("jvm")
}

// Módulo de dominio puro. Depende de domain-transport solo para reusar
// tipos compartidos (GeoPoint, RouteId, ReliabilityScore) — no al revés:
// domain-transport NO sabe que existe crowdsourcing.
dependencies {
    implementation(project(":domain:domain-transport"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation("javax.inject:javax.inject:1")
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
}

kotlin {
    jvmToolchain(17)
}
