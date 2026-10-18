plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.drumpractice.offline"; compileSdk = 35
 defaultConfig { applicationId = "com.drumpractice.offline"; minSdk = 29; targetSdk = 35; versionCode = 6; versionName = "0.6" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0"); implementation("com.github.wendykierp:JTransforms:3.1") }
