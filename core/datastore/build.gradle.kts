plugins {
    alias(libs.plugins.unidoc.android.library)
    alias(libs.plugins.unidoc.hilt)
}

android {
    namespace = "com.rajdeep.unidoc.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
}
