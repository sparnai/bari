# kotlinx.serialization – keep generated serializers for our models.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.sparnai.bari.**$$serializer { *; }
-keepclassmembers class com.sparnai.bari.** {
    *** Companion;
}
-keepclasseswithmembers class com.sparnai.bari.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.sparnai.bari.core.** { *; }

# OkHttp optional platform integrations
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
