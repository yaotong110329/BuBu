import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) localPropertiesFile.inputStream().use(::load)
}
val googleWebClientId = providers.gradleProperty("BUBU_GOOGLE_WEB_CLIENT_ID")
    .orNull
    ?: localProperties.getProperty("BUBU_GOOGLE_WEB_CLIENT_ID")
    ?: ""
val releaseSigningProperties = Properties().apply {
    val signingPropertiesFile = rootProject.file("signing.properties")
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use(::load)
}
val releaseSigningKeys = listOf(
    "BUBU_RELEASE_STORE_FILE",
    "BUBU_RELEASE_STORE_PASSWORD",
    "BUBU_RELEASE_KEY_ALIAS",
    "BUBU_RELEASE_KEY_PASSWORD",
)
val hasReleaseSigning = releaseSigningKeys.all { !releaseSigningProperties.getProperty(it).isNullOrBlank() } &&
    rootProject.file(releaseSigningProperties.getProperty("BUBU_RELEASE_STORE_FILE", "")).isFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.kumo.bubu"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kumo.bubu"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"

    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("BUBU_RELEASE_STORE_FILE"))
                storePassword = releaseSigningProperties.getProperty("BUBU_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningProperties.getProperty("BUBU_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningProperties.getProperty("BUBU_RELEASE_KEY_PASSWORD")
            }
            proguardFiles("proguard-rules.pro")
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

    defaultConfig {
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

}

tasks.register("verifyReleaseConfiguration") {
    group = "verification"
    description = "Checks the non-versioned Google OAuth and Android signing inputs required for a release build."
    inputs.property("googleWebClientConfigured", googleWebClientId.isNotBlank())
    inputs.property("releaseSigningConfigured", hasReleaseSigning)
    doLast {
        check(inputs.properties["googleWebClientConfigured"] == true) {
            "Set BUBU_GOOGLE_WEB_CLIENT_ID in local.properties or pass it with -P for the release build."
        }
        check(inputs.properties["releaseSigningConfigured"] == true) {
            "Create the ignored signing.properties from signing.properties.example and provide a readable release keystore."
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn("verifyReleaseConfiguration")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
