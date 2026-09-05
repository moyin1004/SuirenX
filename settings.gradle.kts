pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "SuirenX"

include(":apps:android:app")
include(":apps:android:core:model")
include(":apps:android:core:domain")
include(":apps:android:core:data")
include(":apps:android:core:ui")
include(":apps:android:feature:assets")

include(":apps:android:feature:settings")
