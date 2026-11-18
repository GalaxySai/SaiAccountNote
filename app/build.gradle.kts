plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.galaxysai.saiaccountnote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.galaxysai.saiaccountnote"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 让 Room 把数据库结构导出到 app/schemas 目录,
        // 这样以后加字段/写迁移时有据可查,也方便 Review。
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Room:运行时 + 注解处理器(Java 项目用 annotationProcessor,不需要 kapt/ksp)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // 数据库加密
    implementation(libs.sqlcipher.android)

    // 生物识别(指纹/面容)
    implementation(libs.biometric)

    // 侧边栏(DrawerLayout)
    implementation(libs.drawerlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}