plugins {
    id("com.android.application") version "9.4.1" apply false
    // GeckoView 157 depends on kotlin-stdlib 2.4.20. AGP's built-in Kotlin
    // defaults to 2.2.10, whose compiler cannot read 2.4 metadata. Declaring
    // the Kotlin plugin (applied nowhere) selects 2.4.20 for built-in Kotlin.
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin") {
            version { strictly("2.4.20") }
        }
    }
}
