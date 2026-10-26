# tor-android: JNI entry points and control library
-keep class org.torproject.jni.** { *; }
-keep class net.freehaven.tor.control.** { *; }

# GeckoView
-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.geckoview.**

# IPtProxy (gomobile bindings)
-keep class IPtProxy.** { *; }
-keep class go.** { *; }

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
