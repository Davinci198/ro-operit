import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ai.assistance.mmd"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        // DISABLED externalNativeBuild {
    //             cmake {
    //                 cppFlags += listOf("-std=c++17", "-fno-emulated-tls")
    //                 arguments += listOf(
    //                     "-DANDROID_STL=c++_static",
    //                     "-DANDROID_PLATFORM=android-26",
    //                     "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
    //                 )
    //             }
    // DISABLED }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // DISABLED externalNativeBuild {
    //         cmake {
    //             // DISABLED path = file("CMakeLists.txt")
    //             version = "3.22.1"
    //         }
    // DISABLED }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}