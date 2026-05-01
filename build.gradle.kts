import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException

plugins {
    id("com.android.application") version "9.1.1" apply false
    id("com.android.library") version "9.1.1" apply false
    id("com.android.kotlin.multiplatform.library") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}

val releaseSigningEnvironmentVariables = listOf(
    "HRS_UPLOAD_STORE_FILE",
    "HRS_UPLOAD_STORE_PASSWORD",
    "HRS_UPLOAD_KEY_ALIAS",
    "HRS_UPLOAD_KEY_PASSWORD",
)

subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension>("android") {
            signingConfigs {
                create("release") {
                    providers.environmentVariable("HRS_UPLOAD_STORE_FILE").orNull?.let { storeFile = file(it) }
                    storePassword = providers.environmentVariable("HRS_UPLOAD_STORE_PASSWORD").orNull
                    keyAlias = providers.environmentVariable("HRS_UPLOAD_KEY_ALIAS").orNull
                    keyPassword = providers.environmentVariable("HRS_UPLOAD_KEY_PASSWORD").orNull
                }
            }

            buildTypes {
                getByName("release") {
                    signingConfig = signingConfigs.getByName("release")
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }
        }

        val releaseTaskRequestedForThisProject = gradle.startParameter.taskNames.any { taskName ->
            val normalized = if (taskName.startsWith(":")) taskName else ":$taskName"
            taskName.contains("Release", ignoreCase = true) &&
                (!taskName.contains(":") || normalized.startsWith("${project.path}:"))
        }

        fun missingReleaseSigningEnvironment(): List<String> =
            releaseSigningEnvironmentVariables.filter {
                providers.environmentVariable(it).orNull.isNullOrBlank()
            }

        if (releaseTaskRequestedForThisProject) {
            val missing = missingReleaseSigningEnvironment()
            if (missing.isNotEmpty()) {
                throw GradleException("Missing release signing environment variables: ${missing.joinToString()}")
            }
        }

        tasks.configureEach {
            if (name.contains("Release")) {
                doFirst {
                    val missing = missingReleaseSigningEnvironment()
                    if (missing.isNotEmpty()) {
                        throw GradleException("Missing release signing environment variables: ${missing.joinToString()}")
                    }
                }
            }
        }
    }
}
