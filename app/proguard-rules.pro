# kotlinx.serialization and Navigation type-safe routes ship their own consumer rules.
# Keep line numbers for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit (text + barcode) share internal component plumbing that R8 optimised into a null
# dereference ("Attempt to read from field ... on a null object reference" when opening Text).
# Keep both SDKs as shipped.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.odml.** { *; }
