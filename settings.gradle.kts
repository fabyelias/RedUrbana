import org.gradle.authentication.http.BasicAuthentication

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Repositorio privado de Mapbox: requiere MAPBOX_DOWNLOADS_TOKEN (token
        // secreto sk.*, distinto del token público pk.* que usa la app en runtime)
        // definido en ~/.gradle/gradle.properties de cada desarrollador — NUNCA en
        // este repositorio. Ver README / mensaje de Claude sobre el setup de Mapbox.
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication { create<BasicAuthentication>("basic") }
            credentials {
                username = "mapbox"
                password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").getOrElse("")
            }
        }
    }
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "RedUrbana"

include(
    ":app",

    // core
    ":core:core-common",
    ":core:core-ui",
    ":core:core-database",
    ":core:core-network",
    ":core:core-location",
    ":core:core-testing",

    // domain
    ":domain:domain-transport",
    ":domain:domain-favorites",
    ":domain:domain-alerts",
    ":domain:domain-user",
    ":domain:domain-crowdsourcing",

    // data
    ":data:data-transport",
    ":data:data-favorites",
    ":data:data-alerts",
    ":data:data-user",
    ":data:data-crowdsourcing",

    // feature
    ":feature:feature-dashboard",
    ":feature:feature-map",
    ":feature:feature-lines",
    ":feature:feature-stops",
    ":feature:feature-vehicle-detail",
    ":feature:feature-favorites",
    ":feature:feature-alerts",
    ":feature:feature-history",
    ":feature:feature-settings",
    ":feature:feature-auth",

    // sync
    ":sync",
)
