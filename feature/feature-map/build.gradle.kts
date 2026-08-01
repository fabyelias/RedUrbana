plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.redurbana.feature.map"
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
    implementation(project(":domain:domain-transport"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    // Mapbox Maps SDK + extensión de Jetpack Compose (reemplaza a Google Maps
    // Compose). Requiere el repositorio privado de Mapbox configurado en
    // settings.gradle.kts, autenticado con MAPBOX_DOWNLOADS_TOKEN — ver
    // docs/CREDENTIALS_SETUP.md en la raíz del proyecto.
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.compose)

    implementation(libs.play.services.location)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // TODO: VehicleClusterManager ya no es tan necesario si se usa el
    // clustering nativo de Mapbox para PointAnnotation — evaluar en el
    // siguiente paso si conviene migrar VehicleClusterer.kt a esa API.
}
