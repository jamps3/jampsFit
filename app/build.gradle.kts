import java.util.Properties
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

@DisableCachingByDefault(because = "Each release build reserves a new, persistent version")
abstract class GenerateReleaseVersion : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val versionState: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val stateFile = versionState.get().asFile
        val current = Properties().apply { stateFile.inputStream().use { load(it) } }
        val previousCode = current.getProperty("versionCode").toInt()
        val parts = current.getProperty("versionName").split('.').map(String::toInt)
        check(parts.size == 3) { "Release versionName must use major.minor.patch" }
        check(previousCode in 1 until 2_100_000_000) { "Release versionCode is out of range" }
        val versionCode = Math.addExact(previousCode, 1)
        val versionName = "${parts[0]}.${parts[1]}.${Math.addExact(parts[2], 1)}"
        val contents = "versionCode=$versionCode\nversionName=$versionName\n"
        val generated = outputFile.get().asFile
        generated.parentFile.mkdirs()
        generated.writeText(contents)
        // Reserve the number before downstream work so interrupted builds never reuse it.
        val temporaryState = stateFile.resolveSibling(".${stateFile.name}.tmp")
        temporaryState.writeText(contents)
        Files.move(temporaryState.toPath(), stateFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        logger.lifecycle("Release version: {} (code {})", versionName, versionCode)
    }
}

val releaseVersionState = rootProject.layout.projectDirectory.file("release-version.properties")
val currentVersion = Properties().apply {
    load(providers.fileContents(releaseVersionState).asText.get().reader())
}
val releaseSigningFile = rootProject.layout.projectDirectory.file("release-signing.properties")
val releaseSigning = if (releaseSigningFile.asFile.isFile) Properties().apply {
    load(providers.fileContents(releaseSigningFile).asText.get().reader())
} else null

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val generateVersion = tasks.register<GenerateReleaseVersion>("generate${variant.name.replaceFirstChar(Char::uppercaseChar)}Version") {
        versionState.set(releaseVersionState)
        outputFile.set(layout.buildDirectory.file("generated/release-version/${variant.name}/version.properties"))
        outputs.upToDateWhen { false }
    }
    val nextVersion = generateVersion.map { task ->
        Properties().apply { task.outputFile.get().asFile.inputStream().use { load(it) } }
    }
    variant.outputs.forEach { output ->
        output.versionCode.set(nextVersion.map { it.getProperty("versionCode").toInt() })
        output.versionName.set(nextVersion.map { it.getProperty("versionName") })
    }
}

android {
    namespace = "com.labbaslabs.jampsfit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.labbaslabs.jampsfit"
        minSdk = 33
        targetSdk = 37
        versionCode = currentVersion.getProperty("versionCode").toInt()
        versionName = currentVersion.getProperty("versionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("localRelease") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("localRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }

    lint {
        disable += setOf(
            "BatteryLife",
            "GradleDependency",
            "IconLocation",
            "NewerVersionAvailable",
            "ObsoleteSdkInt",
            "ThreadConstraint",
            "UnusedResources"
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.splashscreen)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
