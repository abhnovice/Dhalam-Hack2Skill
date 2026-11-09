plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.android.legacy-kapt")
    id("io.objectbox")
}

val googleWebClientId = providers.gradleProperty("DHALAM_GOOGLE_WEB_CLIENT_ID")
    .orElse(providers.environmentVariable("DHALAM_GOOGLE_WEB_CLIENT_ID"))
    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
    .orElse("")
val backendUrl = providers.gradleProperty("DHALAM_BACKEND_URL")
    .orElse(providers.environmentVariable("DHALAM_BACKEND_URL")).orElse("")
val whatsAppBotNumber = providers.gradleProperty("DHALAM_WHATSAPP_BOT_NUMBER")
    .orElse(providers.environmentVariable("DHALAM_WHATSAPP_BOT_NUMBER")).orElse("")
fun configString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r") + "\""

val resolvedGoogleWebClientId = googleWebClientId.get()
require(resolvedGoogleWebClientId.isEmpty() ||
    resolvedGoogleWebClientId.endsWith(".apps.googleusercontent.com")) {
    "DHALAM_GOOGLE_WEB_CLIENT_ID must be a Web OAuth client ID."
}
if (providers.gradleProperty("DHALAM_REQUIRE_GOOGLE_SIGNIN").map(String::toBoolean).orElse(false).get()) {
    require(resolvedGoogleWebClientId.isNotEmpty()) {
        "Set DHALAM_GOOGLE_WEB_CLIENT_ID before building the shared demo APK."
    }
}

android {
    namespace = "com.dhalam.ap"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dhalam.ap"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"
        // Public developer configuration only. No API keys or shared passwords in the APK.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", configString(resolvedGoogleWebClientId))
        buildConfigField("String", "ASK_BACKEND_URL", configString(backendUrl.get()))
        buildConfigField("String", "WHATSAPP_BOT_NUMBER", configString(whatsAppBotNumber.get().trim()))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "ASK_BACKEND_URL",
                configString(backendUrl.get().ifBlank { "http://10.0.2.2:18765" }))
        }
        release {
            // R8's package-scoped Kotlin rewrite makes AndroidX Startup fail
            // before MainActivity on the release APK (IllegalAccessError).
            optimization { enable = false }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    constraints {
        implementation("androidx.fragment:fragment:1.8.9") {
            because("Credential Manager's transitive Fragment must support Activity Result APIs.")
        }
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.work:work-runtime:2.11.0")
    implementation("io.objectbox:objectbox-android:5.4.2")
}
