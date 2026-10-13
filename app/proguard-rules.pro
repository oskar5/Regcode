# Rules applied to the release build (R8 full mode).
# Keep this file small and let the library consumer rules do the heavy lifting.

# Keep annotations + signatures that are read reflectively, and keep line
# numbers so Play Console can symbolicate crashes with mapping.txt.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------ Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep class androidx.room.** { *; }
-keepclassmembers enum * { *; }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------ Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ------------------------------------------------------------------ DataStore
-dontwarn androidx.datastore.**

# ------------------------------------------------------------------ Kotlin / coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ------------------------------------------------------------------ App entry points
-keep class com.codeeditor.CodeEditorApp { *; }
-keep class com.codeeditor.MainActivity { *; }
