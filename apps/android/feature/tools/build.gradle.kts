plugins {
    alias(libs.plugins.suirenx.android.library)
    alias(libs.plugins.suirenx.android.compose)
}

android { namespace = "io.suirenx.feature.tools" }

dependencies {
    implementation(project(":apps:android:core:ui"))
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
