plugins {
    id("com.android.application")
}

android {
    namespace = "com.qingping.screen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qingping.screen"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = false
    }
}

// 本项目刻意不依赖 AndroidX / 第三方库：零联网权限，纯本地运行，构建体积最小。
