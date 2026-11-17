import com.android.build.api.dsl.LibraryExtension
import com.rajdeep.unidoc.buildlogic.UnidocConfig
import com.rajdeep.unidoc.buildlogic.configureKotlinCompile
import com.rajdeep.unidoc.buildlogic.configureTests
import com.rajdeep.unidoc.buildlogic.libs
import com.rajdeep.unidoc.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                compileSdk = UnidocConfig.COMPILE_SDK
                defaultConfig {
                    minSdk = UnidocConfig.MIN_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = UnidocConfig.JAVA_VERSION
                    targetCompatibility = UnidocConfig.JAVA_VERSION
                }
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
            }
            configureKotlinCompile()
            configureTests()
            dependencies {
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testImplementation", libs.library("turbine"))
            }
        }
    }
}
