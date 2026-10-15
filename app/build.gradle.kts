plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.beta.auraos"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    // URL del backend: se lee de local.properties (no versionado) para no fijarla en el código.
    val localProps = rootProject.file("local.properties").takeIf { it.exists() }
        ?.readLines()?.mapNotNull { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2 && !line.trimStart().startsWith("#")) parts[0].trim() to parts[1].trim() else null
        }?.toMap() ?: emptyMap()
    val apiBaseUrlDebug = localProps["api.baseUrl.debug"] ?: "http://10.0.2.2:3000/"
    val apiBaseUrlRelease = localProps["api.baseUrl.release"] ?: apiBaseUrlDebug

    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val props = keystoreFile.readLines().mapNotNull { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }.toMap()
        signingConfigs {
            create("release") {
                storeFile = file(props["storeFile"]!!)
                storePassword = props["storePassword"]!!
                keyAlias = props["keyAlias"]!!
                keyPassword = props["keyPassword"]!!
            }
        }
    }

    defaultConfig {
        applicationId = "com.beta.auraos"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlDebug\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrlRelease\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.lifecycle.process)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.coil)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}