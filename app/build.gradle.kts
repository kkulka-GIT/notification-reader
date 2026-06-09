plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.notificationreader"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.notificationreader"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    testImplementation(files(rootProject.file(".gradle-dist/gradle-8.10.2/lib/junit-4.13.2.jar")))
    testImplementation(files(rootProject.file(".gradle-dist/gradle-8.10.2/lib/hamcrest-core-1.3.jar")))
}
