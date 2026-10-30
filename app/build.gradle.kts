plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.radardeganhos.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.radardeganhos.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 24
        versionName = "1.23"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/radar.jks")
            storePassword = "radar2026"
            keyAlias = "radar"
            keyPassword = "radar2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
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

    buildFeatures {
        viewBinding = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // O lintVital do release aborta o build quando o projeto é compilado em JDK 25
    // (a máquina do motorista estava toda em Java 25): ele falha apontando a própria
    // string de versão. O lint continua disponível pelo menu do Android Studio.
    // Não reative checkReleaseBuilds sem testar o build num JDK 25.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // OCR offline (modelo embarcado, nao usa internet nem Play Services)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    testImplementation("junit:junit:4.13.2")
    // 4.17: ASM novo o suficiente para ler classes do JDK 25 (major 69) — o 4.12.2
    // quebra com "Unsupported class file major version 69" em todo teste Robolectric.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.5.0")
}
