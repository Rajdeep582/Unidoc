import com.android.build.api.dsl.ApplicationExtension
import com.rajdeep.unidoc.buildlogic.UnidocConfig
import com.rajdeep.unidoc.buildlogic.configureKotlinCompile
import com.rajdeep.unidoc.buildlogic.configureTests
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                compileSdk = UnidocConfig.COMPILE_SDK
                defaultConfig {
                    minSdk = UnidocConfig.MIN_SDK
                    targetSdk = UnidocConfig.TARGET_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = UnidocConfig.JAVA_VERSION
                    targetCompatibility = UnidocConfig.JAVA_VERSION
                }
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
                packaging {
                    resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
                }
            }
            configureKotlinCompile()
            configureTests()
        }
    }
}
