plugins {
    alias(libs.plugins.unidoc.android.feature)
}

android {
    namespace = "com.rajdeep.unidoc.feature.scanner.home"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.pdf)
    implementation(libs.coil.compose)
}
