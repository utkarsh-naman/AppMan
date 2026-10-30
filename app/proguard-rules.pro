# libadb uses BouncyCastle's lightweight crypto API directly (HKDF, AES-GCM,
# SHA-256) and AppMan uses its ASN.1 cert builder to sign the ADB TLS
# certificate. Neither path goes through the BC JCE provider, so R8 is allowed
# to strip it (~1.9 MB of dex, which Android also stores again as vdex/odex).
-dontwarn org.bouncycastle.**

# libadb loads org.conscrypt.OpenSSLProvider and calls
# org.conscrypt.Conscrypt#exportKeyingMaterial by reflection during pairing.
# Names must survive R8 (JNI + Class.forName). If this is stripped, libadb
# falls back to the platform Conscrypt hidden API, which is blocked on
# Android 14+ and breaks pairing.
-keep class org.conscrypt.** { *; }
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**
