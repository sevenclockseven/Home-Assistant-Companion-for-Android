import com.android.build.api.dsl.ApplicationExtension
import io.homeassistant.companion.android.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import java.text.SimpleDateFormat

private const val APPLICATION_ID = "io.homeassistant.companion.android"

/**
 * A convention plugin that applies common configurations to Android application modules.
 * This centralizes configuration, preventing duplication across multiple modules.
 *
 * This plugin applies several Gradle plugins that are commonly used in all application modules,
 * including the [AndroidCommonConventionPlugin].
 *
 * After applying this plugin, the configured values can be overridden if necessary. However,
 * if extensive overrides are required, it may indicate that the configuration should be moved
 * out of this convention plugin.
 *
 * The application's `versionCode` can be set via the `VERSION_CODE` environment variable.
 * The `versionName` defaults to `v` plus the current date and can be pinned to a pushed release
 * tag via the `RELEASE_TAG` Gradle property (`-PRELEASE_TAG=v...`).
 *
 * A `release` signing configuration is automatically created. The keystore information can beexo_controls_playback_speeds
 * provided through the following environment variables:
 * - `KEYSTORE_PATH`: The path to the keystore file.
 * - `KEYSTORE_PASSWORD`: The password for the keystore.
 * - `KEYSTORE_ALIAS`: The alias for the key within the keystore.
 * - `KEYSTORE_ALIAS_PASSWORD`: The password for the key alias.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.android.application.getPluginId())
            apply(plugin = libs.plugins.ksp.getPluginId())
            apply(plugin = libs.plugins.hilt.getPluginId())
            apply(plugin = libs.plugins.aboutlibraries.getPluginId())
            apply(plugin = libs.plugins.google.services.getPluginId())
            AndroidCommonConventionPlugin().apply(target)
            AndroidComposeConventionPlugin().apply(target)

            extensions.configure<ApplicationExtension> {
                namespace = APPLICATION_ID

                defaultConfig {
                    applicationId = APPLICATION_ID
                    targetSdk = libs.versions.androidSdk.target.get().toInt()

                    versionName = getVersionName(project)
                    versionCode = getVersionCode()
                    val noStrictMode = project.findProperty("noStrictMode")?.toString()?.ifEmpty { "true" }
                        ?.toBoolean() ?: false
                    buildConfigField("Boolean", "NO_STRICT_MODE", noStrictMode.toString())
                }

                buildFeatures {
                    viewBinding = true
                }

                val NESTOR_KEYSTORE_PASSWORD = System.getenv("NESTOR_KEYSTORE_PASSWORD")
                val NESTOR_KEYSTORE_ALIAS = System.getenv("NESTOR_KEYSTORE_ALIAS")
                // val PGY_API_KEY = System.getenv("PGY_API_KEY")

                signingConfigs {
                    create("release") {
                        storeFile = file("../nestor.keystore")
                        storePassword = NESTOR_KEYSTORE_PASSWORD
                        keyAlias = NESTOR_KEYSTORE_ALIAS
                        keyPassword = NESTOR_KEYSTORE_PASSWORD
                        enableV1Signing = true
                        enableV2Signing = true
                    }
                }

                buildTypes {
                    named("debug").configure {
                        applicationIdSuffix = ".debug"
                    }
                    named("release").configure {
                        isDebuggable = false
                        isJniDebuggable = false
                        signingConfig = signingConfigs.getByName("release")
                    }
                }
            }
        }
    }
}
fun getVersionCode(): Int {
    val time = System.currentTimeMillis()
    return (time / 1000).toInt()
}

fun getVersionName(project: Project): String {
    // Read as a Gradle property (not an env var) so the value reaches the build even when the
    // Gradle daemon was started by an earlier step without this property set.
    val releaseTag = project.findProperty("RELEASE_TAG")?.toString()
    if (!releaseTag.isNullOrBlank()) {
        return releaseTag
    }
    return "v" + SimpleDateFormat("yyyy-MM-dd").format(System.currentTimeMillis())
}
