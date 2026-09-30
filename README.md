# UPlay

UPlay is a dark, modern Android video player foundation built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Current foundation
- Local video selection using Android's document picker (no broad storage permission).
- Playback through Media3 ExoPlayer.
- Direct HTTPS video URL input.
- Responsive dark UI with UPlay blue/green accent system.
- Approved U-shaped blue/green play mark with pixel accents.
- GitHub Actions builds and uploads a debug APK artifact only.

## Build
Open in Android Studio with JDK 17 and Android SDK 35, or run `gradle assembleDebug` with Gradle 8.9.

The app accepts direct media URLs; it does not scrape or resolve arbitrary webpage links.
