# Keep annotations so DI/serialization metadata survives shrinking.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisible*Annotations

# --- Hilt / Dagger ------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclasseswithmembernames class * { @javax.inject.Inject <init>(...); }

# --- Room ---------------------------------------------------------------------
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.stick.app.data.database.** { *; }

# --- kotlinx.serialization ----------------------------------------------------
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static <1>$* *;
    static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.stick.core.model.** { *; }
-keep class com.stick.stickersource.**.dto.** { *; }

# --- Retrofit / OkHttp --------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# --- Fresco (native animated-image decoders, loaded reflectively) -------------
-keep class com.facebook.** { *; }
-dontwarn com.facebook.**

# --- FFmpeg Kit (JNI) ---------------------------------------------------------
-keep class com.antonkarpenko.ffmpegkit.** { *; }
-dontwarn com.antonkarpenko.ffmpegkit.**

# Native methods must keep their names for JNI to bind.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep diagnostic logging usable in release builds.
-keep class android.util.Log { *; }
