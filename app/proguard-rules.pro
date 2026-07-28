# kotlinx.serialization keeps the generated serializers on the @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.trialtracker.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.trialtracker.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
