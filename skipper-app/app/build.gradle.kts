import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
    jvm()

    sourceSets {
        commonMain.dependencies { }
        jvmMain.dependencies { implementation(compose.runtime) }
        jvmTest.dependencies { implementation("junit:junit:4.13.2") }
        androidMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.animation)
            implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
            implementation("top.yukonga.miuix.kmp:miuix-squircle:0.9.4")
            implementation("androidx.activity:activity-compose:1.12.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        }
    }
}

android {
    namespace = "com.wumisc.skipper"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wumisc.skipper"
        minSdk = 29
        targetSdk = 37
        versionCode = 12
        versionName = "0.2.9-m10"
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
}
