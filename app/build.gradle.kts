plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
// Optional local output root keeps Windows cloud-sync file locks away from Gradle's test results.
providers.environmentVariable("PULSE_BUILD_ROOT").orNull?.let { layout.buildDirectory.set(file(it)) }
val releaseSigning = listOf(
    "PULSE_KEYSTORE_PATH", "PULSE_KEYSTORE_PASSWORD", "PULSE_KEY_ALIAS", "PULSE_KEY_PASSWORD"
).map { providers.environmentVariable(it).orNull }
check(releaseSigning.all { it.isNullOrBlank() } || releaseSigning.none { it.isNullOrBlank() }) {
    "Provide all four PULSE signing environment variables, or leave all unset."
}
val githubRepositoryVerified = providers.environmentVariable("PULSE_GITHUB_REPOSITORY_VERIFIED").orNull == "true"

android {
    namespace = "com.pulse.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.pulse.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 6
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "GOOGLE_HEALTH_ENABLED",
            (providers.gradleProperty("pulse.googleHealth.enabled").orNull == "true").toString())
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "GITHUB_REPOSITORY_VERIFIED", githubRepositoryVerified.toString())
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "GITHUB_REPOSITORY_VERIFIED", "false")
        }
    }
    if (releaseSigning.none { it.isNullOrBlank() }) {
        signingConfigs.create("distribution") {
            storeFile = file(requireNotNull(releaseSigning[0]))
            storePassword = releaseSigning[1]
            keyAlias = releaseSigning[2]
            keyPassword = releaseSigning[3]
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("distribution")
    }
    sourceSets.getByName("main").assets.directories.add(layout.buildDirectory.dir("generated/pulse-docs").get().asFile.path)
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}

val bundlePulseDocs by tasks.registering(Sync::class) {
    from(rootProject.file("PRIVACY.md"), rootProject.file("TERMS.md"), rootProject.file("NOTICE.md"),
        rootProject.file("RELEASE_NOTES.md"))
    from(rootProject.file("licenses")) { into("licenses") }
    into(layout.buildDirectory.dir("generated/pulse-docs"))
}
tasks.named("preBuild") { dependsOn(bundlePulseDocs) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    "playImplementation"("com.google.android.play:app-update:2.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.json:json:20260814")
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
