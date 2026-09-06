# ProGuard rules for Women Safety System

# Firebase
-keepattributes Signature
-keepattributes *Annotation*

# Firestore model classes
-keep class com.womensafety.system.data.model.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.womensafety.system.**$$serializer { *; }
-keepclassmembers class com.womensafety.system.** { *** Companion; }
-keepclasseswithmembers class com.womensafety.system.** { kotlinx.serialization.KSerializer serializer(...); }
