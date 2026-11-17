plugins {
    alias(libs.plugins.unidoc.android.library)
    alias(libs.plugins.unidoc.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rajdeep.unidoc.core.scan"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(projects.core.imaging)
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    api(libs.opencv.android)
    implementation(libs.mlkit.text.latin)
    implementation(libs.mlkit.text.devanagari)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.serialization.json)
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
