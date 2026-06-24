import java.util.zip.ZipFile

plugins {
    java
    application
    // Fat/shaded JAR: `./gradlew shadowJar` -> build/libs/vgi-grammar-<ver>-all.jar
    id("com.gradleup.shadow") version "9.4.2"
}

group = "farm.query"
version = "0.1.0-SNAPSHOT"

repositories {
    // The VGI Java SDK is published to Maven Central as farm.query:vgi /
    // farm.query:vgirpc, and LanguageTool (org.languagetool) is on Central too,
    // so the build is fully self-contained — no mavenLocal, no sibling checkout,
    // no composite build.
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all,-serial,-processing",
            "-parameters", // ScalarFn reflects parameter names off @Vector/@Const/@Setting
        )
    )
    options.encoding = "UTF-8"
}

val languageToolVersion = "6.6"

dependencies {
    // VGI Java SDK from Maven Central. `vgi` is the worker/catalog API and pulls
    // in farm.query:vgirpc transitively; vgirpc is declared explicitly because
    // the code imports farm.query.vgirpc.* directly.
    implementation("farm.query:vgi:0.5.0")
    implementation("farm.query:vgirpc:0.10.2")

    // LanguageTool — LGPL-2.1. Used here as an UNMODIFIED, standard, swappable
    // Maven dependency (the LGPL relink/replace obligation is satisfied by it
    // being an ordinary dependency you can replace with your own build). The
    // worker's own code is MIT. See README "Licensing".
    //
    // language-en pulls in languagetool-core transitively and ships the English
    // (en-US / en-GB / en-CA / ...) rule sets and resources. Adding more
    // language-* modules balloons the JAR (each carries large n-gram-free rule
    // resources), so this worker ships English only — see README "Languages".
    implementation("org.languagetool:language-en:$languageToolVersion")

    implementation("org.slf4j:slf4j-simple:2.0.16")
    // LanguageTool and its deps use the Log4j 2 API and SLF4J. Without a Log4j
    // provider, Log4j's StatusLogger prints "could not find a logging provider"
    // to System.out — which corrupts the stdio Arrow-IPC transport and hangs the
    // worker. Bridge Log4j 2 -> SLF4J -> slf4j-simple (stderr) so NOTHING ever
    // writes to stdout. (slf4j-simple defaults all output to System.err.)
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.24.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("farm.query.vgi.grammar.Main")
    applicationDefaultJvmArgs = listOf("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

// Concatenate every dependency's META-INF/services/* SPI file into a generated
// resources dir. LanguageTool registers rules/tokenizers/etc. via SPI across
// languagetool-core and the language-* modules; shadow's mergeServiceFiles()
// alone can collapse providers that declare the SAME service interface.
// Pre-merging into project resources is deterministic and version-agnostic
// (mirrors vgi-tika, where collapsing Tika's parser SPI silently broke output).
val generatedSpiDir = layout.buildDirectory.dir("generated/spi")
val generateMergedSpi = tasks.register("generateMergedSpi") {
    val runtime = configurations.named("runtimeClasspath")
    inputs.files(runtime)
    outputs.dir(generatedSpiDir)
    doLast {
        val servicesByName = linkedMapOf<String, LinkedHashSet<String>>()
        runtime.get().files.filter { it.name.endsWith(".jar") }.forEach { jar ->
            ZipFile(jar).use { zf ->
                zf.entries().asSequence()
                    .filter { e ->
                        !e.isDirectory && e.name.startsWith("META-INF/services/") &&
                            e.name.removePrefix("META-INF/services/").isNotEmpty()
                    }
                    .forEach { e ->
                        val svc = e.name.removePrefix("META-INF/services/")
                        val lines = zf.getInputStream(e).bufferedReader().readLines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                        if (lines.isNotEmpty()) {
                            servicesByName.getOrPut(svc) { linkedSetOf() }.addAll(lines)
                        }
                    }
            }
        }
        val outRoot = generatedSpiDir.get().dir("META-INF/services").asFile
        outRoot.deleteRecursively()
        outRoot.mkdirs()
        servicesByName.forEach { (svc, impls) ->
            outRoot.resolve(svc).writeText(impls.joinToString("\n", postfix = "\n"))
        }
        logger.lifecycle("generateMergedSpi: merged ${servicesByName.size} service files")
    }
}

tasks.shadowJar {
    archiveBaseName.set("vgi-grammar")
    archiveClassifier.set("all")
    dependsOn(generateMergedSpi)
    // The pre-merged SPI files (see generateMergedSpi) override the per-jar ones;
    // mergeServiceFiles() still concatenates anything they miss.
    from(generatedSpiDir)
    mergeServiceFiles()
    manifest {
        attributes(
            "Main-Class" to "farm.query.vgi.grammar.Main",
            "Multi-Release" to "true",
            // Arrow's off-heap MemoryUtil needs java.nio reflectively opened. Bake
            // it into the manifest so a bare `java -jar vgi-grammar-all.jar` works
            // as a VGI LOCATION without the caller having to pass --add-opens.
            "Add-Opens" to "java.base/java.nio",
        )
    }
}

// Make `build` produce the fat jar.
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}
