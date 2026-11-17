pluginManagement {
    includeBuild("build-logic")
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Unidoc"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:model")
include(":core:datastore")
include(":core:ui-kit")
include(":core:imaging")
include(":core:scan")
include(":core:database")
include(":core:data")
include(":core:pdf")
include(":core:converter")
include(":feature:scanner:home")
include(":feature:scanner:capture")
include(":feature:scanner:editor")
include(":feature:scanner:export")
include(":feature:converter:tools")
include(":feature:settings")
include(":feature:converter:hub")
