# ─── Stack traces ─────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Room ─────────────────────────────────────────────────────────────────────
# Room generates DAOs and entities at compile time; the runtime references them
# by reflection, so we must keep them.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers @androidx.room.Entity class * { *; }

# ─── ML Kit ───────────────────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ─── Kotlin coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ─── Kotlin serialisation (if added later) ────────────────────────────────────
-dontwarn kotlinx.serialization.**

# ─── CameraX ──────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ─── Compose ──────────────────────────────────────────────────────────────────
# Compose is generally safe with R8 full-mode, but keep lambda classes used
# by recomposition infrastructure.
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ─── General ──────────────────────────────────────────────────────────────────
# Preserve line numbers for crash reports
-keepattributes *Annotation*
