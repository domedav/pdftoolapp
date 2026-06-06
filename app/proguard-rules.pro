# --- Keep everything to avoid crashes, allow obfuscation ---

# Keep all classes and members from being deleted, but allow renaming (obfuscation)
#-keep,allowobfuscation class ** { *; }

# Coil 3
#-keep class coil3.** { *; }
#-keep interface coil3.** { *; }
#-dontwarn coil3.**

# Jetpack Compose & Kotlin (Do not obfuscate core AndroidX)
#-keep class androidx.** { *; }
#-keep class kotlinx.** { *; }
#-keep class sh.calvin.reorderable.** { *; }

# App Code (Allow obfuscation, but explicitly keep the entry points)
#-keep class com.domedav.pdftool.MainActivity { *; }

# Common Android/Kotlin Attributes
#-keepattributes *Annotation*
#-keepattributes Signature
#-keepattributes InnerClasses,EnclosingMethod

# --- R8 / ProGuard Optimization ---
-allowaccessmodification
-dontpreverify
