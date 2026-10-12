# Keep annotations and signatures reflected upon at runtime.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------ Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------ Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ------------------------------------------------------------------ DataStore
-dontwarn androidx.datastore.**

# ------------------------------------------------------------------ App entry points
-keep class com.codeeditor.CodeEditorApp { *; }
-keep class com.codeeditor.MainActivity { *; }
