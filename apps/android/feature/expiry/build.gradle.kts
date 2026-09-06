plugins {
    alias(libs.plugins.suirenx.android.library)
    alias(libs.plugins.suirenx.android.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android { namespace = "io.suirenx.feature.expiry" }

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
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
}

kapt { correctErrorTypes = true }
