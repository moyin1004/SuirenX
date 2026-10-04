plugins {
    alias(libs.plugins.suirenx.android.application)
    alias(libs.plugins.suirenx.android.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.suirenx.app"

    defaultConfig {
        applicationId = "io.suirenx.app"
        versionCode = 2
        versionName = "0.1.1"
    }
}

dependencies {
    implementation(project(":apps:android:core:domain"))
    implementation(project(":apps:android:core:data"))
    implementation(project(":apps:android:core:ui"))
    implementation(project(":apps:android:feature:assets"))
    implementation(project(":apps:android:feature:settings"))
    implementation(project(":apps:android:feature:tools"))
    implementation(project(":apps:android:feature:expiry"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
}
