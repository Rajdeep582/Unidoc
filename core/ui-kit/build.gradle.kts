plugins {
    alias(libs.plugins.unidoc.android.library)
    alias(libs.plugins.unidoc.android.compose)
}

android {
    namespace = "com.rajdeep.unidoc.core.ui"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
}
