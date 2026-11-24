# -----------------------------------------------------------------------------
# Attributes
#
# Annotations and generic signatures are read at runtime by WebView (JavaScript
# bridges), kotlinx.serialization and Kotlin reflection-free code that still
# inspects inner classes. SourceFile/LineNumberTable keep release stack traces
# readable (use the mapping file from the build to de-obfuscate them).
# -----------------------------------------------------------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# JavaScript bridges (plugin runtime)
#
# Every method exposed to plugin JavaScript is looked up by name through
# WebView.addJavascriptInterface(), so it must keep its exact name and must not be
# shrunk away. This covers DioxaminePluginBridge and TempFileBridge (and any
# bridge added later) instead of listing classes one by one.
# -----------------------------------------------------------------------------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve JavascriptInterface methods on DioxaminePluginBridge from R8 obfuscation/stripping
-keepclassmembers class io.github.rhythmcache.dioxamine.plugin.DioxaminePluginBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# -----------------------------------------------------------------------------
# kotlinx.serialization
#
# Plugin manifests, the online plugin index and update checks are decoded with
# generated serializers that are looked up through Companion.serializer().
# The library ships its own consumer rules; these make the app's own @Serializable
# classes safe in R8 full mode regardless of library version.
# -----------------------------------------------------------------------------
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class io.github.rhythmcache.dioxamine.**$$serializer { *; }
-keepclassmembers class io.github.rhythmcache.dioxamine.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.rhythmcache.dioxamine.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# -----------------------------------------------------------------------------
# Enums persisted or compared by name
#
# PluginPermission / PermissionPolicy are stored in SharedPreferences through
# name() and read back through valueOf()/string matching, so their constants must
# never be renamed or removed, otherwise saved permission choices would be lost
# or misread after an update.
# -----------------------------------------------------------------------------
-keepclassmembers enum io.github.rhythmcache.dioxamine.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -----------------------------------------------------------------------------
# Native methods
# -----------------------------------------------------------------------------
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# -----------------------------------------------------------------------------
# Bouncy Castle (adb-kt key generation, pairing and TLS)
#
# Security providers register their algorithms by class-name string and the JCA
# framework instantiates them reflectively, so R8 cannot see the usages.
# -----------------------------------------------------------------------------
# Bouncy Castle Provider reflection rules (required for adb-kt key generation & TLS)
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

# BCJSSE (TLS) provider, registered by name: SSLContext.TLS -> ...jsse.provider.SSLContextSpi$TLS etc.
# The public jsse API classes are also probed reflectively by the provider.
-keep class org.bouncycastle.jsse.provider.** { *; }
-keep class org.bouncycastle.jsse.** { *; }

# Post-quantum provider classes, also registered by name when the BCPQC provider is used.
-keep class org.bouncycastle.pqc.jcajce.provider.** { *; }

-dontwarn org.bouncycastle.**
