plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Every app is signed with one key, for debug and release builds alike: the apps share a signature
// permission, and Android refuses to install an app that declares it with a different key from the
// apps already installed, or to update an app with a different key from its own.
// Set SEREN_KEYSTORE_FILE and SEREN_KEYSTORE_PASSWORD (and SEREN_KEY_ALIAS, if not "seren") to use
// the suite's key; without them each machine's own debug key is used.
subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            val keystore = System.getenv("SEREN_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let(::file)
            val suiteSigning = if (keystore != null) {
                signingConfigs.create("seren") {
                    storeFile = keystore
                    storePassword = System.getenv("SEREN_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("SEREN_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "seren"
                    keyPassword = System.getenv("SEREN_KEYSTORE_PASSWORD")
                }
            } else {
                signingConfigs.getByName("debug")
            }
            buildTypes.getByName("debug").signingConfig = suiteSigning
            buildTypes.getByName("release").signingConfig = suiteSigning
        }
    }
}
