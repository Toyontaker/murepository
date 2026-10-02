plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.toyontaker.cheerdotsvoice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.toyontaker.cheerdotsvoice"
        minSdk = 31
        targetSdk = 35
        versionCode = 9
        versionName = "0.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests; the android.jar version is a stub.
    testImplementation("org.json:json:20240303")
}
