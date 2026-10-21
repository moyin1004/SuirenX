import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.suirenx.android.application)
    alias(libs.plugins.suirenx.android.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

abstract class GenerateBuildInfoTask : DefaultTask() {
    @get:Input abstract val identity: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun generate() {
        val output = outputDir.file("raw/suirenx_build.json").get().asFile
        output.parentFile.mkdirs()
        output.writeText(identity.get())
    }
}

val packageIdentity = providers.exec {
    workingDir(rootDir)
    commandLine("python3", rootProject.file("scripts/build_info.py"))
}.standardOutput.asText.get()
val packageInfo = JsonSlurper().parseText(packageIdentity) as Map<*, *>
val generatedBuildInfo = layout.buildDirectory.dir("generated/suirenxBuildInfo/res")
val generateBuildInfo = tasks.register<GenerateBuildInfoTask>("generateSuirenxBuildInfo") {
    identity.set(packageIdentity)
    outputDir.set(generatedBuildInfo)
}

android {
    namespace = "io.suirenx.app"

    defaultConfig {
        applicationId = "io.suirenx.app"
        versionCode = (packageInfo["build_number"] as Number).toInt()
        versionName = packageInfo["version"] as String
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(generateBuildInfo, GenerateBuildInfoTask::outputDir)
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
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
}
