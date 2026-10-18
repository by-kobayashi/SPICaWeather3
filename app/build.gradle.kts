import com.android.build.api.artifact.SingleArtifact
import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "me.spica.spicaweather3"
    compileSdk = 37



    defaultConfig {
        applicationId = "me.spica.spicaweather3" +
            if (providers.gradleProperty("widgetVerification").isPresent) ".widgetverification" else ""
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.0_preview5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            //armeabi armeabi-v7a arm64-v8a x86 x86_64
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86_64")
        }

        // 从 local.properties 读取 API 配置
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }
        buildConfigField(
            "String",
            "HEFENG_API_KEY",
            "\"${properties.getProperty("HEFENG_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "HEFENG_API_ID",
            "\"${properties.getProperty("HEFENG_API_ID", "")}\""
        )
    }

    signingConfigs {
        register("signingConfig") {
            storePassword = "SPICa27"
            keyAlias = "wuqi"
            keyPassword = "SPICa27"
            storeFile = rootProject.file("key.jks")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("signingConfig")
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("signingConfig")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true  // 启用 BuildConfig
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(project(":jbox2d"))
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.koin.androidx.compose)
    // Jetpack Compose integration
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.dynamicanimation.ktx)
    val version = "0.9.0"
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)
    implementation(libs.haze.glass)
    implementation(libs.accompanist.systemuicontroller)
    // define a BOM and its version
    implementation(platform(libs.okhttp.bom))
    // define any required OkHttp artifacts without version
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.sandwich.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.reorderable)
    implementation(libs.baidumapsdk.location)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
}

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val variantName = variant.name.replaceFirstChar { it.uppercase() }
    val mapping = variant.artifacts.get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
    val verifyWidgetTypes = tasks.register("verify${variantName}WidgetTypes") {
        group = "verification"
        inputs.file(mapping)
        doLast {
            val names = listOf("TodayInfoAppWidget", "WeeklyWeatherAppWidget", "ScatteredWeatherAppWidget")
                .map { "me.spica.spicaweather3.ui.app_widget.$it" }
            val expected = names.map { "$it -> $it:" }.toSet()
            val actual = mapping.get().asFile.useLines { lines -> lines.filter { it in expected }.toSet() }
            check(actual == expected) { "小组件实现类被合并、移除或重命名，请检查 R8 保留规则" }
        }
    }
    tasks.matching { it.name == "assemble$variantName" }.configureEach {
        dependsOn(verifyWidgetTypes)
    }
}
