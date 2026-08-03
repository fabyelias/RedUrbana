import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Keystore de release: nunca en el repo. Ver docs/CREDENTIALS_SETUP.md
// sección 5. Si el archivo no existe (ej. build de debug de un dev nuevo),
// simplemente no se define el signingConfig de release.
val keystorePropertiesFile = file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
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

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
    implementation(project(":feature:feature-lines"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)
    // Solo para el tema base de la Activity en themes.xml (Theme.Material3.*
    // XML) — la UI en sí es 100% Compose, no usa vistas de Material Components.
    implementation(libs.material)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
