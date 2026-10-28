-keep class com.codexlocal.engine.* { *; }
-keep class com.codexlocal.engine.gguf.* { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class kotlin.Metadata { *; }
