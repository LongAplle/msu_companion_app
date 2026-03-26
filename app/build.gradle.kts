plugins {
    alias(libs.plugins.android.application)
<<<<<<< HEAD
    // for google services
=======
    //for google services
>>>>>>> origin/master
    id("com.google.gms.google-services")
}

android {
    namespace = "edu.msu.cse476.msucompanion"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "edu.msu.cse476.msucompanion"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = "AIzaSyAe8un_fWR4yH7DSBIBauvyhG_UmkUlQ6o"
        buildConfigField("String", "PLACES_API_KEY", "\"AIzaSyAe8un_fWR4yH7DSBIBauvyhG_UmkUlQ6o\"")
    }

    buildFeatures {
        buildConfig = true
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
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("com.google.android.material:material:1.9.0")
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.places)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Room
    annotationProcessor(libs.room.compiler)
    implementation(libs.room.ktx)

    // Firebase BoM — manages all Firebase library versions
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-auth")
<<<<<<< HEAD
=======

>>>>>>> origin/master
}