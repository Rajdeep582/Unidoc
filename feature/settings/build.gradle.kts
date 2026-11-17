plugins {
    alias(libs.plugins.unidoc.android.feature)
}

android {
    namespace = "com.rajdeep.unidoc.feature.settings"
}

dependencies {
    implementation(projects.core.datastore)
}
