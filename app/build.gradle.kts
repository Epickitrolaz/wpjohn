plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** CI passes these in; local builds keep the values below. Blank values fall back. */
fun setting(name: String, fallback: String): String {
    val prop = project.findProperty(name) as String?
    if (!prop.isNullOrBlank()) return prop
    val env = System.getenv(name)
    if (!env.isNullOrBlank()) return env
    return fallback
}

val wpVersionCode = setting("WP_VERSION_CODE", "1").toIntOrNull() ?: 1
val wpVersionName = setting("WP_VERSION_NAME", "2.0")

android {
    namespace = "io.wpreborn.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.wpreborn.app"
        minSdk = 23
        targetSdk = 35
        versionCode = wpVersionCode
        versionName = wpVersionName
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        create("release") {
            // CI restores the secret into keystore/wp-ci.jks; the committed key
            // is the fallback so local and automated builds share an identity.
            val ciKeystore = rootProject.file("keystore/wp-ci.jks")
            storeFile = if (ciKeystore.exists()) ciKeystore else rootProject.file("keystore/release.jks")
            storePassword = setting("WP_STORE_PASSWORD", "wpreborn")
            keyAlias = setting("WP_KEY_ALIAS", "wpreborn")
            keyPassword = setting("WP_KEY_PASSWORD", "wpreborn")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.version")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
