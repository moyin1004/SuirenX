plugins {
    alias(libs.plugins.suirenx.kotlin.library)
}

dependencies {
    api(project(":apps:android:core:model"))
    implementation(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
}

