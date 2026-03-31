# ===== Meow Rescue ProGuard Rules =====

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# --- Gson ---
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.meowrescue.game.data.** { *; }

# --- JBox2D ---
-keep class org.jbox2d.** { *; }

# --- Google Play In-App Update ---
-keep class com.google.android.play.core.** { *; }

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- General ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
