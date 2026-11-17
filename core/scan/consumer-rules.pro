# OpenCV's native library binds to org.opencv Java classes, methods and fields (e.g. Mat.nativeObj)
# by name via JNI; the AAR ships no consumer rules, so keep the API intact.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
