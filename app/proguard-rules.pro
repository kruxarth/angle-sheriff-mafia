# kotlinx.serialization keeps generated serializers for the saved game.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.angelsheriffmafia.engine.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.angelsheriffmafia.engine.**$$serializer { *; }
