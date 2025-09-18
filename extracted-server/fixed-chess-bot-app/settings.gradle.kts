pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "com.android.application") {
        useVersion("4.2.2")  // Compatible with Android 10
      }
      if (requested.id.id == "org.jetbrains.kotlin.android") {
        useVersion("1.5.32")  // Compatible Kotlin version
      }
    }
  }
}

rootProject.name = "project"
include(":app")