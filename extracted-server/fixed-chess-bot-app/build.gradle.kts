
import org.gradle.api.tasks.Delete

plugins {
    // Add any top-level plugins here if necessary, e.g., for dependency management or build scans
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Android Gradle Plugin compatible with Android 10
        classpath("com.android.tools.build:gradle:4.2.2")
        classpath(kotlin("gradle-plugin", version = "1.5.32")) // Compatible Kotlin version
    }
}


allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}