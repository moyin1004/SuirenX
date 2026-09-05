plugins {
    alias(libs.plugins.suirenx.android.library)
    alias(libs.plugins.suirenx.android.compose)
}

android {
    namespace = "io.suirenx.core.ui"
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

