# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line number information for better crash reporting
-keepattributes SourceFile,LineNumberTable

# Keep all source file information for better stack traces
-renamesourcefileattribute SourceFile

# Keep all classes related to Firebase and Crashlytics
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep Gemini AI classes
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# Keep Picovoice classes for wake word detection
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# Keep Room database classes
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep OkHttp3 classes
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Moshi classes for JSON serialization
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Keep Gson classes
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep Kotlinx Serialization classes
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep UI Automator classes
-keep class androidx.test.uiautomator.** { *; }
-dontwarn androidx.test.uiautomator.**

# Keep RevenueCart classes
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# Keep app-specific classes that might be used via reflection
-keep class com.blurr.voice.** { *; }

# Keep all enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep all Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep all Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}