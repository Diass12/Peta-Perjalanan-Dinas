import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "id.tub.dinas"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.tub.dinas"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.7.2"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        // Kunci debug tetap agar APK debug-latest bisa menimpa (update) versi sebelumnya tanpa uninstall.
        getByName("debug") {
            storeFile = file("debug-shared.jks")
            storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // APK untuk dibagikan: R8 + penyusutan resource, tidak debuggable. Sementara ditandatangani dengan kunci
        // yang sama dengan debug-latest supaya bisa menimpa (update) pemasangan lama TANPA hapus data.
        create("distribusi") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true; buildConfig = true }
    packaging { jniLibs { useLegacyPackaging = false } }
    lint { abortOnError = false }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlite.ktx)
    implementation(libs.sqlcipher)
    implementation(libs.osmdroid)
    implementation(libs.play.location)
    implementation(libs.biometric)
    implementation(libs.fragment)
    implementation(libs.exifinterface)
    implementation(libs.coroutines.android)
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}
