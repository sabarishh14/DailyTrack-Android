# Keep API DTOs intact — Moshi's generated adapters are looked up by class-name
# convention at runtime, and R8 renaming/removing a DTO or its JsonAdapter breaks
# JSON parsing silently (only visible as a runtime crash, not a build failure).
-keep class com.example.dailytrack_mobile.data.remote.dto.** { *; }
-keepnames class com.example.dailytrack_mobile.data.remote.dto.** { *; }
-keepclassmembers class **JsonAdapter {
    <init>(...);
    <fields>;
}

# Moshi
-keepclasseswithmembers class * {
    @com.squareup.moshi.FromJson <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonQualifier interface *
-keep class kotlin.Metadata { *; }
-dontwarn org.jetbrains.annotations.**

# OkHttp / Retrofit platform detection (safe no-ops if the optional classes aren't present)
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn retrofit2.Platform$Java8

# Coroutines
-dontwarn kotlinx.coroutines.**
