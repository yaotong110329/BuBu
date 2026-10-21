import java.util.Properties
import java.util.concurrent.TimeUnit

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
        versionCode = 4
        versionName = "1.1.2"

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

// Connected tests install and uninstall the app. Never run them on personal devices.
object ConnectedTestSafety {
    fun verify(adb: String, serial: String, avd: String, androidSerial: String) {
        check(serial.matches(Regex("emulator-[0-9]+")) && avd.isNotBlank()) {
            "Connected tests require -Pbubu.testEmulatorSerial=emulator-PORT and " +
                "-Pbubu.testEmulatorAvd=DISPOSABLE_AVD. Physical devices are not allowed."
        }
        check(androidSerial == serial) { "Set ANDROID_SERIAL to the requested test emulator serial." }
        fun readAdb(vararg arguments: String): String {
            val process = ProcessBuilder(listOf(adb) + arguments).redirectErrorStream(true).start()
            check(process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                "ADB verification timed out; refusing to run connected tests."
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            check(process.exitValue() == 0) { "ADB verification failed; refusing to run connected tests." }
            return output
        }
        val devices = readAdb("devices").lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("List of devices") && !it.startsWith("*") }
            .map { it.split(Regex("\\s+")) }
            .toList()
        check(devices.size == 1 && devices.single().take(2) == listOf(serial, "device")) {
            "ADB must contain only the specified disposable emulator. Disconnect other devices first."
        }
        val actualAvd = readAdb("-s", serial, "emu", "avd", "name")
            .lineSequence().firstOrNull()?.trim()
        check(actualAvd == avd) { "The emulator does not match the explicitly selected disposable AVD." }
    }
}

val safetyAdbPath = androidComponents.sdkComponents.adb.get().asFile.absolutePath
val testEmulatorSerial = providers.gradleProperty("bubu.testEmulatorSerial").orElse("").get()
val testEmulatorAvd = providers.gradleProperty("bubu.testEmulatorAvd").orElse("").get()
val testAndroidSerial = providers.environmentVariable("ANDROID_SERIAL").orElse("").get()
val verifyConnectedTestDevice = tasks.register("verifyConnectedTestDevice") {
    group = "verification"
    description = "Refuses connected tests unless ADB contains only an explicitly selected disposable emulator."
    inputs.properties(mapOf("adb" to safetyAdbPath, "serial" to testEmulatorSerial, "avd" to testEmulatorAvd, "androidSerial" to testAndroidSerial))
    doLast {
        val values = inputs.properties
        ConnectedTestSafety.verify(values["adb"] as String, values["serial"] as String, values["avd"] as String, values["androidSerial"] as String)
    }
}
tasks.configureEach {
    if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
        dependsOn(verifyConnectedTestDevice)
        inputs.properties(mapOf("adb" to safetyAdbPath, "serial" to testEmulatorSerial, "avd" to testEmulatorAvd, "androidSerial" to testAndroidSerial))
        // Recheck immediately before the test action, even after a long APK build.
        doFirst {
            val values = inputs.properties
            ConnectedTestSafety.verify(values["adb"] as String, values["serial"] as String, values["avd"] as String, values["androidSerial"] as String)
        }
    }
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
