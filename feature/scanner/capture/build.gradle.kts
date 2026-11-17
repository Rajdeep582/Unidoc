plugins {
    alias(libs.plugins.unidoc.android.feature)
}

android {
    namespace = "com.rajdeep.unidoc.feature.scanner.capture"
}

dependencies {
    implementation(projects.core.scan)
    implementation(projects.core.datastore)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.compose)
    implementation(libs.mlkit.barcode)
    implementation(libs.androidx.exifinterface)
}
