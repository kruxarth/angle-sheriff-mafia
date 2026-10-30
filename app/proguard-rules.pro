# kotlinx.serialization keeps generated serializers for the saved game, the hosted table
# and the messages phones send each other.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.angelsheriffmafia.engine.**, com.angelsheriffmafia.lan.**, com.angelsheriffmafia.app.lan.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.angelsheriffmafia.engine.**$$serializer { *; }
-keep,includedescriptorclasses class com.angelsheriffmafia.lan.**$$serializer { *; }
-keep,includedescriptorclasses class com.angelsheriffmafia.app.lan.**$$serializer { *; }
