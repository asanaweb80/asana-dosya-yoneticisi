plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
}
android {
 namespace = "tr.com.asana.dosyayoneticisi"
 compileSdk = 35
 defaultConfig {
  applicationId = "tr.com.asana.dosyayoneticisi"
  minSdk = 26
  targetSdk = 35
  versionCode = 1
  versionName = "0.1.0"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
 buildTypes { getByName("release") { isMinifyEnabled = false } }
}
dependencies {
 implementation("androidx.documentfile:documentfile:1.0.1")
}
