import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.example.fishspot"
    compileSdk = 35
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    defaultConfig {
        applicationId = "rs.fishspot.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // Na vrhu ili unutar defaultConfig bloka učitavamo properties:
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }

// Zamenjuješ stare linije ovim bezbednim varijantama:
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"${properties.getProperty("OPENWEATHER_API_KEY")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // NOVI NAČIN ZA SDK 35:
    androidResources {
        noCompress += "tflite"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Čuva AR kameru i Sceneform renderovanje
            pickFirsts.add("lib/**/libfilament-jni.so")
            pickFirsts.add("lib/**/libc++_shared.so")
            // Rešava konflikte za TensorFlow Lite native fajlove
            pickFirsts.add("lib/**/libtensorflowlite_jni.so")
            pickFirsts.add("lib/**/libtensorflowlite_support_jni.so")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Upravljač verzijama za Compose
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Stabilne verzije bez "-android" dupliranja
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // TensorFlow Lite biblioteke
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    implementation("androidx.compose.runtime:runtime-livedata:1.6.7")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // Mape i lokacija
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.0.0")

    // AR Kamera (Sceneform fork)
    implementation("com.gorisse.thomas.sceneform:sceneform:1.23.0")

    // Mreža i Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Ostalo
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation(libs.androidx.appcompat)
    implementation(libs.core)

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Secrets plugin ubacuje sve ključeve iz local.properties u BuildConfig.
// Gemini ključ se koristi samo u Cloud Function i ne sme da završi u aplikaciji.
secrets {
    ignoreList.add("GEMINI_API_KEY")
}

// KLJUČNI DEO: Čišćenje dupliranih API klasa i održavanje AR-a
configurations.all {
    // Sprečava sudar Gemini-jevog LiteRT-a i starog TFLite-a
    exclude(group = "org.tensorflow", module = "tensorflow-lite-api")

    // Sprečava sudar starih i novih Sceneform paketa za AR
    exclude(group = "com.google.ar.sceneform", module = "core")
    exclude(group = "com.google.ar.sceneform", module = "rendering")
    exclude(group = "com.google.ar.sceneform", module = "sceneform-base")
    exclude(group = "com.google.ar.sceneform", module = "filament-android")
}