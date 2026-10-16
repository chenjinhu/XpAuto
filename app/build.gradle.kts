plugins {
    alias(libs.plugins.androidApplication)
}

android {
    // 源码根包与应用标识统一；变更时需同步源码目录、Manifest 和 JNI 入口。
    namespace = "com.vixmobi.auto"
    compileSdk = 34
    ndkVersion = "27.0.12077973"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "com.vixmobi.auto"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }



}



dependencies {
    implementation("com.github.getActivity:XXPermissions:18.63") // 权限请求框架：https://github.com/getActivity/XXPermissions
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.caoccao.javet:javet-v8-android:5.0.11") // 包含 Android 断点暂停循环修复；升级时同步验证原生中断桥接。
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // 网络请求框架
    implementation("net.lingala.zip4j:zip4j:2.11.5") // 支持密码和文件名编码的 ZIP 文件
    implementation("com.google.zxing:core:3.5.3") // 二维码编解码，不依赖摄像头
    implementation("org.java-websocket:Java-WebSocket:1.5.7") // 主要提供了Websocket服务端能力
    implementation(libs.appcompat)
    implementation("androidx.drawerlayout:drawerlayout:1.1.1")
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0") // 本地 HTTP 协议与下载集成测试
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
