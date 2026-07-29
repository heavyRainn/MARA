plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.care.voice.platform.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":brain-core"))
    implementation(project(":piper-spike"))

    implementation(libs.androidx.core.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.squareup.retrofit2:retrofit:2.11.0")
    api("com.squareup.retrofit2:converter-moshi:2.11.0")
    api("com.squareup.moshi:moshi-kotlin:1.15.1")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    val room = "2.6.1"
    api("androidx.room:room-runtime:$room")
    api("androidx.room:room-ktx:$room")
    kapt("androidx.room:room-compiler:$room")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.room:room-testing:$room")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
