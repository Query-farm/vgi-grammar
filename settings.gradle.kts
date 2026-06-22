plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "vgi-grammar"

// The VGI Java SDK (farm.query:vgi, farm.query:vgirpc) and LanguageTool
// (org.languagetool) all resolve from Maven Central — see build.gradle.kts.
// No composite build or mavenLocal needed.
