plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
}
android {
 androidResources { noCompress += "gguf" }
 namespace = "com.aibondhu.mini"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.aibondhu.mini"
  minSdk = 26
  targetSdk = 35
  versionCode = 3
  versionName = "0.3.0"
  ndk { abiFilters += listOf("arm64-v8a") }
  externalNativeBuild { cmake { cppFlags += listOf("-std=c++17", "-O3") } }
 }
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("com.google.android.material:material:1.12.0")
 implementation("androidx.activity:activity-ktx:1.9.3")
}

// Rebuild trigger: include native llama.cpp checkout in CI.
