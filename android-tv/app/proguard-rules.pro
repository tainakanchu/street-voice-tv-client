# Add project specific ProGuard rules here.
# For StreetVoice TV PoC - keep serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.example.streetvoicetv.data.model.**$$serializer { *; }
-keepclassmembers class com.example.streetvoicetv.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.streetvoicetv.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
