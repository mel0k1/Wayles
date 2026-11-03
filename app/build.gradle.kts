import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.wayles.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wayles.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2"

        val lp = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) {
                f.inputStream().use { load(it) }
            }
        }
        buildConfigField(
            "int",
            "TD_API_ID",
            System.getenv("TD_API_ID") ?: lp.getProperty("wayles.apiId") ?: "6"
        )
        val hash = System.getenv("TD_API_HASH") ?: lp.getProperty("wayles.apiHash")
            ?: "eb06d4abfb49dc3eeb1aeb98ae0f581e"
        buildConfigField("String", "TD_API_HASH", "\"$hash\"")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("com.github.capullo-tech:lib-tdlib-android:bc9a091")
}
