plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.pyzhpets.jump"
    compileSdk = 35
    defaultConfig { applicationId = "com.pyzhpets.jump.skybound05"; minSdk = 24; targetSdk = 28; versionCode = 6; versionName = "6.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
