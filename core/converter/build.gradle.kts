plugins {
    alias(libs.plugins.unidoc.android.library)
    alias(libs.plugins.unidoc.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    namespace = "com.rajdeep.unidoc.core.converter"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.scan)
    implementation(projects.core.pdf)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
}
