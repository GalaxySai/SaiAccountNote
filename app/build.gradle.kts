import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 正式签名配置放在项目根目录的 keystore.properties(不提交到 Git)。
// 没有这个文件时,Release 构建保持未签名,不影响 Debug 构建。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

android {
    namespace = "com.galaxysai.saiaccountnote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.galaxysai.saiaccountnote"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 让 Room 把数据库结构导出到 app/schemas 目录,
        // 这样以后加字段/写迁移时有据可查,也方便 Review。
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // 有 keystore.properties 时自动用正式签名;没有就保持未签名
            signingConfig = signingConfigs.findByName("release")
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

    // 列表(RecyclerView)
    implementation(libs.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}