plugins {
    alias(libs.plugins.suirenx.android.library)
    alias(libs.plugins.suirenx.android.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.suirenx.feature.assets"
}

dependencies {
    implementation(project(":apps:android:core:model"))
    implementation(project(":apps:android:core:domain"))
    implementation(project(":apps:android:core:ui"))

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

kapt {
    correctErrorTypes = true
}
