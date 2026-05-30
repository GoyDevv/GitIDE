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
    }
}

rootProject.name = "GitIDE"

// Include the main application module
include(":app")

// Include the Termux submodules located in the root project directory
include(":terminal-emulator")
include(":terminal-view")
