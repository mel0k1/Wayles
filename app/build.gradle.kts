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
        versionCode = 6
        versionName = "0.4.1"

        val lp = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) {
                f.inputStream().use { load(it) }
            }
        }
        val envApiId = System.getenv("TD_API_ID")?.trim()?.takeIf { it.isNotBlank() }
        val lpApiId = lp.getProperty("wayles.apiId")?.trim()?.takeIf { it.isNotBlank() }
        buildConfigField("int", "TD_API_ID", envApiId ?: lpApiId ?: "6")
        val envHash = System.getenv("TD_API_HASH")?.trim()?.takeIf { it.isNotBlank() }
        val lpHash = lp.getProperty("wayles.apiHash")?.trim()?.takeIf { it.isNotBlank() }
        val hash = envHash ?: lpHash ?: "eb06d4abfb49dc3eeb1aeb98ae0f581e"
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
