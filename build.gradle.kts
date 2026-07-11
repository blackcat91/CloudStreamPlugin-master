import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {

    // For Android projects:
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
}


buildscript {
    repositories {
        google()
        mavenCentral()
        // Shitpack repo which contains our tools and dependencies
        maven("https://jitpack.io")
    }
 //Test
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        // Cloudstream gradle plugin which makes everything work and builds plugins
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")

    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // when running through github workflow, GITHUB_REPOSITORY should contain current repository name
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "user/repo")
    }

    android {
        namespace = "com.myiptv"

        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17) // Required
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }



    dependencies {
        val cloudstream by configurations
        val implementation by configurations

        // Stubs for all cloudstream classes
        cloudstream("com.lagradost:cloudstream3:pre-release")

        // These dependencies can include any of those which are added by the app,
        // but you don't need to include any of them if you don't need them.
        // https://github.com/recloudstream/cloudstream/blob/master/app/build.gradle.kts
        implementation(kotlin("stdlib")) // Adds Standard Kotlin Features
        implementation("com.github.Blatzar:NiceHttp:0.4.11") // HTTP Lib
        implementation("org.jsoup:jsoup:1.18.3") // HTML Parser
        // IMPORTANT: Do not bump Jackson above 2.13.1, as newer versions will
        // break compatibility on older Android devices.
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1") // JSON Parser
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        implementation(platform("io.github.jan-tennert.supabase:bom:3.0.1")) // Check for latest version
        implementation("io.github.jan-tennert.supabase:storage-kt")
        implementation("io.ktor:ktor-client-android:2.3.12") // Use a Ktor engine suitable for your platform
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}