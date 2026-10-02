plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dhruv21.chromakit.ml"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":chromakit-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.mlkit.segmentation.selfie)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
