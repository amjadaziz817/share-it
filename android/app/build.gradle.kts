import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins { id("com.android.application") }

android {
    namespace = "com.shareit"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.shareit"
        minSdk = 30 // Android 11+: "All files access" permission.
        targetSdk = 36
        // Build number from the build time: each new APK is newer, and the app shows when it was built.
        versionCode = (System.currentTimeMillis() / 60_000).toInt()
        versionName = "0.2 (build " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ")"
    }
    // A fixed debug key in the repo, so each CI build installs as an update over the one before.
    // Its password is the public default "android": the key is not a secret.
    signingConfigs.getByName("debug").storeFile = rootProject.file("debug.keystore")
    // The release key is outside the repo: set shareit.storeFile and shareit.storePassword in ~/.gradle/gradle.properties.
    val storeFile = providers.gradleProperty("shareit.storeFile").orNull
    if (storeFile != null) {
        val pass = providers.gradleProperty("shareit.storePassword").get()
        signingConfigs.create("release") {
            this.storeFile = file(storeFile)
            storePassword = pass
            keyAlias = "share-it"
            keyPassword = pass
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
}

base { archivesName = "share-it" } // APK name: share-it-<buildType>.apk

// server.aar is built from ../server by `gomobile bind` (see README.md).
dependencies { implementation(files("libs/server.aar")) }
