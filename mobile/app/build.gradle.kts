plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}


android {
    namespace = "com.example.financehub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.financehub"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"")
            buildConfigField("String", "API_ENDPOINT", "\"http://10.0.2.2:8000/api/v1/\"")
            buildConfigField("boolean", "IS_PRODUCTION", "false")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.101:8000/\"")
            buildConfigField("String", "API_ENDPOINT", "\"http://192.168.1.101:8000/api/v1/\"")
            buildConfigField("boolean", "IS_PRODUCTION", "true")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.runtime.livedata)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.smbj)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    androidTestImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material3)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.serialization.json)
    
    // Network dependencies for sync
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")
    // Lifecycle components for sync state
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.runtime.livedata.v154)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
}
