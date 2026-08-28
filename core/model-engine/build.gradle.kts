plugins {
    id("com.android.library")
}

android {
    namespace = "com.hormonelog.core.modelengine"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:evidence"))
    testImplementation("junit:junit:4.13.2")
}
