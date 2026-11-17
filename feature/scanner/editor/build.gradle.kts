plugins {
    alias(libs.plugins.unidoc.android.feature)
}

android {
    namespace = "com.rajdeep.unidoc.feature.scanner.editor"
}

dependencies {
    implementation(projects.core.scan)
    implementation(libs.coil.compose)
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.reorderable)
    implementation(libs.kotlinx.serialization.json)
}
