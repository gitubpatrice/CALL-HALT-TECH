pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// No foojay-resolver-convention plugin, on purpose (Agenda Tech, 2026-08-14): the project asks for no
// JVM toolchain, and the plugin's presence changes classes.dex — a build F-Droid could not reproduce.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Call Halt Tech"

// docs/ARCHITECTURE.md, D-001: the detection logic lives in a module that CANNOT import Android.
include(":domain")
include(":app")
