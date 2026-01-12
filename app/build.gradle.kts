import java.io.ByteArrayOutputStream
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("com.google.gms.google-services")
    alias(libs.plugins.ksp)
}

fun getVersionCode(): Int {
    return try {
        val stdout = ByteArrayOutputStream()
        project.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            standardOutput = stdout
        }
        // Offset 63 para garantir que a versão comece em 81 (commit count atual: 18)
        stdout.toString().trim().toInt() + 63
    } catch (e: Exception) {
        // Fallback version code
        81
    }
}

fun getVersionName(): String {
    return try {
        val stdout = ByteArrayOutputStream()
        project.exec {
            commandLine("git", "describe", "--tags", "--always")
            standardOutput = stdout
        }
        stdout.toString().trim()
    } catch (e: Exception) {
        // Fallback version name
        "1.0"
    }
}

// Helper para ler local.properties
fun getLocalProperty(key: String): String {
    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        properties.load(FileInputStream(localPropertiesFile))
    }
    return properties.getProperty(key) ?: ""
}

android {
    namespace = "com.rmrbranco.galacticcom"
    compileSdk = 36

    signingConfigs {
        create("release") {
            // TODO: Substitua com os seus dados de keystore
            // keyAlias = System.getenv("KEYSTORE_ALIAS")
            // keyPassword = System.getenv("KEYSTORE_PASSWORD")
            // storeFile = file(System.getenv("KEYSTORE_PATH"))
            // storePassword = System.getenv("KEYSTORE_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.rmrbranco.galacticcom"
        minSdk = 24
        targetSdk = 36
        versionCode = getVersionCode()
        versionName = getVersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        // Ler AdMob ID e injetar no Manifesto
        val admobAppId = getLocalProperty("ADMOB_APP_ID")
        // Fallback para ID de teste se não encontrar (segurança para builds CI/CD)
        val finalAdmobId = if (admobAppId.isNotEmpty()) admobAppId else "ca-app-pub-3940256099942544~3347511713"
        
        manifestPlaceholders["admobAppId"] = finalAdmobId
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    lint {
        textReport = true
        textOutput = file("lint-report.txt")
        abortOnError = false
    }
}

dependencies {
    // AndroidX Core & UI
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.multidex)
    implementation("androidx.cardview:cardview:1.0.0")

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Firebase
    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")


    // Navigation Component
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Paging
    implementation(libs.androidx.paging.runtime.ktx)

    // Glide
    implementation(libs.glide)
    ksp(libs.glide.compiler)

    // CircleImageView
    implementation(libs.circleimageview)
    
    // AdMob
    implementation(libs.play.services.ads)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}