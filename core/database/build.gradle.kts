plugins {
    alias(libs.plugins.unidoc.android.library)
    alias(libs.plugins.unidoc.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.rajdeep.unidoc.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
