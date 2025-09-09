pluginManagement {
    repositories {
        // Keep all three so plugins & their markers resolve correctly
        google()
        maven { url = uri("https://maven.google.com") }   // explicit fallback
        maven { url = uri("https://dl.google.com/dl/android/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Let module-level repositories win (so we can hardwire the Google repos)
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        maven { url = uri("https://maven.google.com") }
        maven { url = uri("https://dl.google.com/dl/android/maven2/") }
        mavenCentral()
    }
}


rootProject.name = "Agriscan"
include(":app")