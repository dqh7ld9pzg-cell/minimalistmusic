plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    alias(libs.plugins.baselineprofile) // this version matches your Kotlin version

}

android {
    namespace = "com.minimalistmusic"
    compileSdk = 35
    sourceSets {
        named("main") {
            java.setSrcDirs(setOf("src/main/kotlin",
                "src/main/java",
            ))
            //java.setSrcDirs(setOf("src/main/kotlin","src/main/java"))
        }
    }

//    sourceSets {
//        getByName("main") {
//            java.srcDirs("src/main/java", "src/main/kotlin")
//        }
//    }

    defaultConfig {
        applicationId = "com.minimalistmusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.2.19"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // API配置
        buildConfigField("String", "NETEASE_API_BASE_URL", "\"https://music-api.example.com\"")
        buildConfigField("String", "API_KEY", "\"YOUR_API_KEY_HERE\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // 替换为实际签名
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        jvmTarget = "17"
        // 明确设置语言版本
        languageVersion = "1.9"
        apiVersion = "1.9"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.9"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    // 使用 Material 2 icons，但 ripple 已在全局配置中排除
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Hilt (依赖注入)
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-compiler:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room (数据库)
    // implementation("androidx.room:room-runtime:2.6.0")
    // implementation("androidx.room:room-ktx:2.6.0")
    // kapt("androidx.room:room-compiler:2.6.0")
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Retrofit (网络请求)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Coil (图片加载)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ExoPlayer (音频播放)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-session:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // DataStore (数据存储)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Accompanist (系统UI控制)
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    // 移除废弃的swiperefresh，使用Material3官方pullRefresh (2025-11-28)

    // Reorderable (拖拽排序 2025-11-24)
    // Reorderable - 拖拽排序（2025-11-29：替换为支持新版Compose的库）
    implementation("sh.calvin.reorderable:reorderable:2.4.0")

    // LazyColumn Scrollbar (快速滑动条 2025-11-24)
    implementation("com.github.nanihadesuka:LazyColumnScrollbar:2.2.0")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}


// 确保所有 Kotlin 任务使用正确的版本
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        languageVersion = "1.9"
        apiVersion = "1.9"
        jvmTarget = "17"
    }
}
// Kapt配置
kapt {
    javacOptions {
        option("-source", "17")
        option("-target", "17")
    }
    correctErrorTypes = true
}



