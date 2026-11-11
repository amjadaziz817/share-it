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
}

base { archivesName = "share-it" } // APK name: share-it-<buildType>.apk

// server.aar is built from ../server by `gomobile bind` (see README.md).
dependencies { implementation(files("libs/server.aar")) }
