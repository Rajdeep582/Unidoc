plugins {
    alias(libs.plugins.unidoc.android.library)
    alias(libs.plugins.unidoc.hilt)
}

android {
    namespace = "com.rajdeep.unidoc.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.scan)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(libs.room.runtime)
}
