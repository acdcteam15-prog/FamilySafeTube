# R8 / ProGuard rules for release builds.
#
# Strategy: keep all of our own code (readable crash traces, and nothing here relies on
# reflection that R8 could miss) and let R8 shrink the libraries. Each library ships its own
# consumer rules (Media3, Glide, OkHttp, WorkManager, Compose, DataStore), so only a few
# explicit extras are needed.

-keep class com.example.familysafetube.** { *; }

# Keep line numbers so stack traces from release builds are useful.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# DataStore Preferences uses protobuf-lite generated classes.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { <fields>; }

# OkHttp's optional TLS providers are not on Android; silence their missing-class warnings.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# OkHttp 5 (platform classes it looks up reflectively).
-dontwarn okhttp3.internal.platform.**
