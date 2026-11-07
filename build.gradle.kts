group = "org.limine.snapper"
version = "1.32.1"

plugins {
    application
    id("org.graalvm.buildtools.native") version "1.1.14"
}

application {
    mainClass.set("org.limine.snapper.Main")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("tools.jackson.core:jackson-databind:3.2.3")
}

graalvmNative {
    toolchainDetection.set(true)
    binaries {
        named("main") {
            verbose.set(false)
            resources.autodetect()

            buildArgs.add("--gc=serial")
            buildArgs.add("-march=compatibility")
            buildArgs.add("-R:MaxHeapSize=128m")
            buildArgs.add("-Os") //Reduce binary size

            buildArgs.add("-H:+UnlockExperimentalVMOptions")
            buildArgs.add("-H:ReflectionConfigurationFiles=${file("src/main/resources/reflect-config.json").absolutePath}")
            buildArgs.add("-H:IncludeResourceBundles=messages")
            buildArgs.add("-H:IncludeLocales=en,ru")

            imageName.set("limine-snapper-sync")
            mainClass.set(application.mainClass)
        }
    }
    tasks.test {
        enabled = false
    }
}

// Turn the gettext catalogues into binary ones inside the install tree, so
// every packaging path gets them with the files it already copies. Without
// this, `po/*.po` is only useful to whoever remembers to run msgfmt by hand,
// and the shell scripts stay untranslated in the packages that ship them.
val translationCatalogues = tasks.register("translationCatalogues") {
    description = "Compiles po/*.po into the install tree with msgfmt"
    val poDir = layout.projectDirectory.dir("po")
    val localeDir = layout.projectDirectory.dir("install/arch-linux/usr/share/locale")
    inputs.dir(poDir)
    outputs.dir(localeDir)
    doLast {
        val catalogues = poDir.asFile.listFiles { f -> f.extension == "po" }?.sortedBy { it.name }.orEmpty()
        if (catalogues.isEmpty()) return@doLast

        val msgfmt = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, "msgfmt") }
            .firstOrNull { it.canExecute() }
        if (msgfmt == null) {
            // Not fatal: the binary still builds, only the shell scripts keep
            // speaking English. Packagers need gettext for translated output.
            logger.warn("msgfmt not found, skipping translations: install gettext to include them")
            return@doLast
        }

        catalogues.forEach { po ->
            val target = localeDir.file("${po.nameWithoutExtension}/LC_MESSAGES/limine-snapper-sync.mo").asFile
            target.parentFile.mkdirs()
            val process = ProcessBuilder(msgfmt.path, "--check", po.path, "-o", target.path)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0) {
                throw GradleException("msgfmt failed on ${po.name}: $output")
            }
        }
    }
}

tasks.named("build") { dependsOn(translationCatalogues) }
tasks.matching { it.name == "nativeCompile" }.configureEach { dependsOn(translationCatalogues) }
