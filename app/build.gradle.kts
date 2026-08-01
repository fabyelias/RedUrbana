plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.redurbana.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.redurbana.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-skeleton"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Ensamblado: app conoce todas las features y las implementaciones de data,
    // pero ninguna feature conoce a otra ni a los módulos de data directamente.
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))

    implementation(project(":domain:domain-transport"))
    implementation(project(":data:data-transport"))
    implementation(project(":data:data-crowdsourcing"))

    implementation(project(":feature:feature-dashboard"))
    implementation(project(":feature:feature-map"))
    implementation(project(":feature:feature-vehicle-detail"))
    implementation(project(":feature:feature-alerts"))
    implementation(project(":feature:feature-stops"))
    implementation(project(":feature:feature-settings"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
