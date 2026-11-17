# Digital signatures (Sign PDF → certify): BouncyCastle CMS resolves algorithm/ASN.1 classes by
# name at runtime; keep them so release builds sign the same as debug.
-keep class org.bouncycastle.cms.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-keep class org.bouncycastle.cert.** { *; }
-keep class org.bouncycastle.asn1.** { *; }
-dontwarn org.bouncycastle.**
