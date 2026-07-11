dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.1")) // Check for latest version
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android:2.3.12") // Use a Ktor engine suitable for your platform
}

// Use an integer for version numbers
version = 3
//New Build
cloudstream {
    // All of these properties are optional, you can safely remove any of them.

    description = "My IPTV Player"
    authors = listOf("Cloudburst", "BlackCat91")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("Live")

    requiresResources = true
    language = "en"

    // Random CC logo I found
    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}