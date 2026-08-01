plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.redurbana.data.favorites"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":domain:domain-favorites"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-database"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // TODO: repositorio + Room DAO reales (roadmap paso 3+).
}
