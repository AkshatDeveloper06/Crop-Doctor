import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Optional online helper key. Put DEEPSEEK_API_KEY=... in local.properties (never committed) or set the env var.
// Leave it empty and the app is fully offline.
val onlineKey: String = run {
    val f = rootProject.file("local.properties")
    val p = Properties()
    if (f.exists()) f.inputStream().use { p.load(it) }
    (p.getProperty("DEEPSEEK_API_KEY") ?: System.getenv("DEEPSEEK_API_KEY") ?: "").trim()
        .replace("\\", "").replace("\"", "")
}

android {
    namespace = "com.cropdoctor.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cropdoctor.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$onlineKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    // The model must stay uncompressed so it can be memory-mapped.
    androidResources { noCompress += listOf("tflite") }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.mlkit.text.devanagari)

    implementation(libs.androidx.work)
    implementation(libs.gson)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")   // real org.json for JVM tests (Android's is a stub there)
}
