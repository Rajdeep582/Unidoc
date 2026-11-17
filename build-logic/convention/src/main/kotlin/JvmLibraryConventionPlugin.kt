import com.rajdeep.unidoc.buildlogic.UnidocConfig
import com.rajdeep.unidoc.buildlogic.configureKotlinCompile
import com.rajdeep.unidoc.buildlogic.configureTests
import com.rajdeep.unidoc.buildlogic.libs
import com.rajdeep.unidoc.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = UnidocConfig.JAVA_VERSION
                targetCompatibility = UnidocConfig.JAVA_VERSION
            }
            configureKotlinCompile()
            configureTests()
            dependencies {
                add("testImplementation", libs.library("junit"))
            }
        }
    }
}
