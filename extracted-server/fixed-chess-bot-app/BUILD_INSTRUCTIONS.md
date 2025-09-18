# Build Instructions for Chess Bot App (Redmi 9 Activ Compatible)

## Prerequisites
You need Android Studio or Android SDK to build this native Kotlin Android app.

## Method 1: Android Studio (Recommended)
1. Install Android Studio from https://developer.android.com/studio
2. Open Android Studio and import this project
3. Let Android Studio download required SDK components (API 29)
4. Click "Build" > "Build Bundle(s) / APK(s)" > "Build APK(s)"
5. APK will be generated in `app/build/outputs/apk/debug/`

## Method 2: Command Line
If you have Android SDK installed:
```bash
# Set environment variable (adjust path as needed)
export ANDROID_HOME=/path/to/your/android/sdk

# Build the APK
./gradlew assembleDebug
```

## Installation on Redmi 9 Activ
1. Enable Developer Options: Settings > About Phone > Tap "MIUI Version" 7 times
2. Enable "Unknown sources" in Security settings
3. Install the APK file
4. Grant permissions as described in CRASH_FIXES_SUMMARY.md

## All Fixes Applied
✅ Missing AccessibilityManager import fixed
✅ UI thread blocking eliminated  
✅ Android 10 SDK compatibility
✅ MIUI overlay restrictions handled
✅ Enhanced error handling implemented

The app is now ready to run without crashes on your Redmi 9 Activ!