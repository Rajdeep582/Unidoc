plugins {
    alias(libs.plugins.unidoc.android.feature)
}

android {
    namespace = "com.rajdeep.unidoc.feature.converter.tools"
}

dependencies {
    implementation(projects.core.scan)
    implementation(projects.core.datastore)
    implementation(libs.androidx.documentfile)
    implementation(libs.reorderable)
    implementation(libs.coil.compose)
}
