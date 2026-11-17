plugins {
    alias(libs.plugins.unidoc.android.feature)
}

android {
    namespace = "com.rajdeep.unidoc.feature.scanner.export"
}

dependencies {
    implementation(projects.core.pdf)
    implementation(projects.core.datastore)
    implementation(libs.androidx.documentfile)
}
