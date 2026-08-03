pluginManagement {
    repositories {
        // Google's Maven is not reachable from this network (every
        // dl.google.com/dl/android/maven2/* path 404s). This mirror proxies both
        // Google's repository and Maven Central. Drop the mirror lines if google()
        // resolves directly for you.
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
    }
}

rootProject.name = "Latent"
include(":app")
