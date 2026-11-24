import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.photomatch"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.photomatch"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

// Builds the Rust core (rust/) into src/main/jniLibs before every build.
val sdkDir: String = run {
    val props = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    props.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: ""
}
val cargoBuild = tasks.register<Exec>("cargoBuild") {
    val ndkDir = File(sdkDir, "ndk").listFiles()?.maxByOrNull { it.name }
    workingDir = rootProject.file("rust")
    environment("ANDROID_NDK_HOME", ndkDir?.absolutePath ?: "")
    commandLine(
        "cargo", "ndk", "-t", "x86_64", "-t", "arm64-v8a", "-P", "30",
        "-o", project.file("src/main/jniLibs").absolutePath, "build", "--release"
    )
    inputs.dir(rootProject.file("rust/src"))
    inputs.file(rootProject.file("rust/Cargo.toml"))
    outputs.dir(project.file("src/main/jniLibs"))
}
tasks.named("preBuild") { dependsOn(cargoBuild) }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui-compose:1.11.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
