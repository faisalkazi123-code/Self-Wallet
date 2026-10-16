# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.selfwallet.app.**$$serializer { *; }
-keepclassmembers class com.selfwallet.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.selfwallet.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
