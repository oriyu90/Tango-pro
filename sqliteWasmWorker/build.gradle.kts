import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

val workerPackageName = "tango-pro-sqlite-wasm-worker"
val prepareWorkerNpmPackage by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("worker"))
    into(rootProject.layout.buildDirectory.dir("wasm/local-packages/$workerPackageName"))
    mustRunAfter(rootProject.tasks.named("clean"))
}

rootProject.tasks.matching { it.name == "kotlinWasmNpmInstall" }.configureEach {
    dependsOn(prepareWorkerNpmPackage)
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        useEsModules()
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.sqlite.web)
            // Stage the package inside the generated Wasm workspace. Passing a
            // source-tree File here serializes the developer's absolute path into yarn.lock.
            implementation(npm(workerPackageName, "file:../../local-packages/$workerPackageName"))
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}
