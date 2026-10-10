import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration
import java.util.Properties

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val nafsSupabaseUrl = providers.gradleProperty("NAFS_SUPABASE_URL")
    .orElse(providers.environmentVariable("EXPO_PUBLIC_SUPABASE_URL"))
    .orElse("")
val nafsSupabaseKey = providers.gradleProperty("NAFS_SUPABASE_PUBLISHABLE_KEY")
    .orElse(providers.environmentVariable("EXPO_PUBLIC_SUPABASE_ANON_KEY"))
    .orElse("")
val nafsApiBaseUrl = providers.gradleProperty("NAFS_API_BASE_URL")
    .orElse(providers.environmentVariable("EXPO_PUBLIC_API_BASE_URL"))
    .orElse("https://nafs-one.vercel.app")

// Supply the existing release identity from a private file outside this repository.
val releaseSigningFile = providers.gradleProperty("NAFS_SIGNING_PROPERTIES_FILE")
    .orElse(providers.environmentVariable("NAFS_SIGNING_PROPERTIES_FILE"))
val releaseSigning = releaseSigningFile.orNull?.let { path ->
    val configFile = file(path)
    require(configFile.isFile) { "NAFS_SIGNING_PROPERTIES_FILE must name an existing private properties file" }
    Properties().apply { configFile.inputStream().use { load(it) } }.also { properties ->
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { name ->
            require(!properties.getProperty(name).isNullOrBlank()) { "Missing release signing property: $name" }
        }
        require(file(properties.getProperty("storeFile")).isFile) { "Release keystore file does not exist" }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.ahmad.nafs"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ahmad.nafs"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        buildConfigField("String", "SUPABASE_URL", nafsSupabaseUrl.get().asBuildConfigString())
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", nafsSupabaseKey.get().asBuildConfigString())
        buildConfigField("String", "API_BASE_URL", nafsApiBaseUrl.get().asBuildConfigString())
        buildConfigField("boolean", "GOOGLE_AUTH_ENABLED", providers.gradleProperty("NAFS_GOOGLE_AUTH_ENABLED").orElse("false").map { it.toBoolean().toString() }.get())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseSigning != null) create("release") {
            storeFile = file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

// A hung test (for example an animation that never idles) fails the build instead of stalling CI.
tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(15))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    // Screenshot tests: Compose screens rendered on the JVM (Robolectric) and saved as PNG (Roborazzi).
    testImplementation(composeBom)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

