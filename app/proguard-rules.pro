# -----------------------------------------------------------------------------
# JavaScript bridges (plugin runtime)
#
# Every method exposed to plugin JavaScript is looked up by name through
# WebView.addJavascriptInterface(), so it must keep its exact name and must not be
# shrunk away. This covers DioxaminePluginBridge and TempFileBridge (and any
# bridge added later) instead of listing classes one by one.
# -----------------------------------------------------------------------------
-keepattributes *Annotation*

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve JavascriptInterface methods on DioxaminePluginBridge from R8 obfuscation/stripping
-keepclassmembers class io.github.rhythmcache.dioxamine.plugin.DioxaminePluginBridge {
    @android.webkit.JavascriptInterface <methods>;
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
-keep class org.bouncycastle.jsse.provider.** { *; }

# Post-quantum provider classes, also registered by name when the BCPQC provider is used.
-keep class org.bouncycastle.pqc.jcajce.provider.** { *; }

-dontwarn org.bouncycastle.**
