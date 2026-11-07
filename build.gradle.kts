buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10") } }
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
