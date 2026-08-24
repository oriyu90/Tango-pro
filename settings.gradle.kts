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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  // Kotlin/Wasm registers the pinned Node.js distribution as an Ivy repository.
  // Keep project repositories enabled so the toolchain can be resolved by Gradle.
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "TangoPro"

include(":app")
include(":shared")
include(":webApp")
include(":sqliteWasmWorker")
