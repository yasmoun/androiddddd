plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.pdfreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pdfreader"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation("io.github.afreakyelf:Pdf-Viewer:2.1.1")  // Correctly reference the Pdf-Viewer library
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation ("com.tom-roush:pdfbox-android:2.0.27.0")  // pdfbox-android pour Android
    implementation ("net.dankito.text.extraction:pdfbox-android-text-extractor:0.6.1")  // Extraction de texte PDF
    implementation ("com.rmtheis:tess-two:5.4.1")
}