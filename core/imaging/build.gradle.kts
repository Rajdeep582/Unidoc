plugins {
    alias(libs.plugins.unidoc.jvm.library)
}

// Pure JVM image pipeline over the OpenCV Java API. On Android the classes and native
// library come from the official OpenCV AAR (core:scan); here we compile against the
// identical API and run unit tests with the desktop build (natives bundled).
dependencies {
    api(projects.core.model)
    compileOnly(libs.opencv.jvm)
    testImplementation(libs.opencv.jvm)
}
