plugins {
    alias(libs.plugins.suirenx.android.application)
    alias(libs.plugins.suirenx.android.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.suirenx.app"

    defaultConfig {
        applicationId = "io.suirenx.app"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":apps:android:core:data"))
    implementation(project(":apps:android:core:ui"))
    implementation(project(":apps:android:feature:assets"))
    implementation(project(":apps:android:feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.material3)
}

kapt {
    correctErrorTypes = true
}

