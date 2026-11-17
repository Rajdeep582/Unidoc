package com.rajdeep.unidoc.buildlogic

import org.gradle.api.JavaVersion

object UnidocConfig {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 26
    val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_17
}
