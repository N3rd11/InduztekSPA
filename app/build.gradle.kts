plugins {
    jacoco
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")

}

val archivoFirma = providers.environmentVariable("INDUZTEK_KEYSTORE")
val claveFirma = providers.environmentVariable("INDUZTEK_STORE_PASSWORD")
val claveAliasFirma = providers.environmentVariable("INDUZTEK_KEY_PASSWORD")

android {
    namespace = "cl.induztek.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cl.induztek.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Las pruebas de acceso autenticado requieren el backend y se ejecutan aparte.
        testInstrumentationRunnerArguments["notAnnotation"] = "cl.induztek.app.RequiereBackend"
    }

    signingConfigs {
        if (archivoFirma.isPresent && claveFirma.isPresent && claveAliasFirma.isPresent) {
            create("entrega") {
                storeFile = rootProject.file(archivoFirma.get())
                storeType = "JKS"
                storePassword = claveFirma.get()
                keyAlias = "induztek-parcial4"
                keyPassword = claveAliasFirma.get()
            }
        }
    }

    buildTypes {
        create("validacion") {
            initWith(getByName("debug"))
            // Instrumentación aislada: nunca usa la instalación ni la base de datos de app.
            applicationIdSuffix = ".validacion"
            matchingFallbacks += listOf("debug")
            enableUnitTestCoverage = true
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
        create("entrega") {
            initWith(getByName("release"))
            applicationIdSuffix = ".entrega"
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.findByName("entrega")
            // La optimización parcial de paquetes rompe el arranque de WorkManager en el APK firmado.
            optimization { enable = false }
        }
    }
    testBuildType = "validacion"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

dependencies {
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
//Viewmodel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    //ROOM
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    testImplementation("org.robolectric:robolectric:4.16.1")


}
