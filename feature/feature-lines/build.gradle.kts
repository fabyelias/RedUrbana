plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.redurbana.feature.lines"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-location"))
    implementation(project(":domain:domain-transport"))
    implementation(project(":domain:domain-crowdsourcing"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Buscador de destino: geocoding real de Mapbox (SearchEngine). Usa el
    // mismo repositorio privado y el mismo token público que el Maps SDK.
    implementation(libs.mapbox.search)
    implementation(libs.kotlinx.coroutines.core)

    // Mini-mapa estático del detalle de itinerario (TripPreviewMap): cámara
    // fija, sin flota simulada — no es LiveMapScreen, así que no hace falta
    // ninguna dependencia además de las del SDK de mapas en sí.
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.compose)

    // "Verse manejando" (CarNavigationViewModel/ExploreViewModel): construye
    // LiveDriverPosition, que expone kotlinx.datetime.Instant — domain-crowdsourcing
    // lo declara "implementation", no "api", así que no llega solo por
    // transitividad (ver comentario en su build.gradle.kts).
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
}
