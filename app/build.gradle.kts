import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.play.publisher)
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

// Cuenta de servicio de Google Play: nunca en el repo, mismo criterio que el
// keystore. Solo existe en CI (ver .github/workflows/publish-play-internal.yml,
// que la reconstruye a partir de un secret de GitHub) o si un dev la baja a
// mano para probar una publicación real desde su máquina. Si no existe, el
// bloque `play {}` de abajo no configura las credenciales y la tarea de
// publicación simplemente falla si se la intenta correr (no rompe ningún
// build normal de compilación/instalación).
val playServiceAccountFile = file("play-service-account.json")

android {
    namespace = "com.redurbana.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.redurbana.app"
        minSdk = 26
        targetSdk = 35
        // En CI (ver .github/workflows/publish-play-internal.yml), cada
        // corrida trae GITHUB_RUN_NUMBER, que Play Console exige que sea
        // siempre mayor al de la subida anterior. Local (sin esa variable)
        // sigue siendo 1, como siempre.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
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
    // hiltViewModel(NavBackStackEntry) — RedUrbanaNavHost lo usa para
    // compartir un mismo LinesViewModel entre Lines y TripDetail (grafo
    // anidado TripPlanningGraph).
    implementation(libs.hilt.navigation.compose)
}

// Publicación automática a Play Console (track "internal"), ver
// .github/workflows/publish-play-internal.yml. Sin el archivo de
// credenciales no se puede ni resolver la ruta con `file()` sin que Gradle
// tire error al evaluar el script, por eso el `if` envuelve todo el bloque
// en vez de solo el `serviceAccountCredentials.set(...)`.
if (playServiceAccountFile.exists()) {
    play {
        serviceAccountCredentials.set(playServiceAccountFile)
        track.set("internal")
        defaultToAppBundles.set(true)
    }
}
