plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.redurbana.data.crowdsourcing"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":domain:domain-crowdsourcing"))
    implementation(project(":domain:domain-transport"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-location"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Cliente real de Supabase (crowd_pings / vehicle_group_estimates) — ver
    // supabase/README.md. BOM alinea las versiones de los módulos de Supabase;
    // el engine de Ktor (ktor-client-android) se elige aparte, no viene con el BOM.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
}
