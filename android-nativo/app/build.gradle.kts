plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Lectura de configuración pública de Supabase desde assets/js/config.js
val configFile = rootProject.file("../assets/js/config.js")
val (supabaseUrl, supabaseAnonKey) = if (configFile.exists()) {
    val content = configFile.readText()
    val urlMatch = Regex("""const\s+URL_PRODUCCION\s*=\s*["']([^"']+)["']""").find(content)
    val keyMatch = Regex("""const\s+KEY_PRODUCCION\s*=\s*["']([^"']+)["']""").find(content)
    Pair(
        urlMatch?.groupValues?.get(1) ?: "https://wotumvamyglfquahdnet.supabase.co",
        keyMatch?.groupValues?.get(1) ?: "sb_publishable_V0wt4JdNVXHc20_0tJWBHA_D47gcmbf"
    )
} else {
    Pair(
        "https://wotumvamyglfquahdnet.supabase.co",
        "sb_publishable_V0wt4JdNVXHc20_0tJWBHA_D47gcmbf"
    )
}

android {
    namespace = "pe.registroacademico.nativo"
    compileSdk = 34

    defaultConfig {
        applicationId = "pe.registroacademico.nativo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    flavorDimensions += "perfil"
    productFlavors {
        create("docente") {
            dimension = "perfil"
            applicationIdSuffix = ".docente"
            resValue("string", "app_name", "Registro Académico")
        }
        create("estudiante") {
            dimension = "perfil"
            applicationIdSuffix = ".estudiante"
            resValue("string", "app_name", "Carnet del Estudiante")
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
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Jetpack Compose BOM & UI
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Supabase Kotlin & Ktor Client
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)

    // Kotlinx Serialization & Coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Unit Testing
    testImplementation(libs.junit)
}
