plugins {
    alias(libs.plugins.suirenx.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.suirenx.core.data"
    buildFeatures.buildConfig = true
}

dependencies {
    implementation(project(":apps:android:core:model"))
    implementation(project(":apps:android:core:domain"))

    testImplementation(libs.junit4)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
}

kapt {
    correctErrorTypes = true
}

