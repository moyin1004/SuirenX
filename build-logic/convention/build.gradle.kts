plugins {
    `kotlin-dsl`
}

group = "io.suirenx.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "suirenx.android.application"
            implementationClass = "SuirenXAndroidApplicationPlugin"
        }
        register("androidLibrary") {
            id = "suirenx.android.library"
            implementationClass = "SuirenXAndroidLibraryPlugin"
        }
        register("androidCompose") {
            id = "suirenx.android.compose"
            implementationClass = "SuirenXAndroidComposePlugin"
        }
        register("kotlinLibrary") {
            id = "suirenx.kotlin.library"
            implementationClass = "SuirenXKotlinLibraryPlugin"
        }
    }
}

