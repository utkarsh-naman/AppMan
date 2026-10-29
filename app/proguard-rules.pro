# BouncyCastle resolves algorithm implementations by name at runtime. Needed
# to build the self-signed certificate used for ADB pairing TLS.
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-dontwarn org.bouncycastle.**

# libadb loads org.conscrypt.OpenSSLProvider and calls
# org.conscrypt.Conscrypt#exportKeyingMaterial by reflection during pairing.
# Names must survive R8 (JNI + Class.forName). If this is stripped, libadb
# falls back to the platform Conscrypt hidden API, which is blocked on
# Android 14+ and breaks pairing.
-keep class org.conscrypt.** { *; }
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**
