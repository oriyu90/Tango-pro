// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.room3) apply false
  alias(libs.plugins.roborazzi) apply false
}

// Kotlin/Wasm 2.4.10's production linker cannot reopen unpacked klibs when
// their absolute build path contains non-ASCII characters. Keep normal builds
// in-project, but move generated output to the OS temp directory for such
// workspaces. Sources and all repository data remain in the checkout.
if (!rootDir.absolutePath.all { it.code in 0..127 }) {
  val workspaceKey = rootDir.absolutePath.hashCode().toString().replace('-', 'n')
  val safeBuildRoot = file("${System.getProperty("java.io.tmpdir")}/tango-pro-gradle-$workspaceKey")
  allprojects {
    layout.buildDirectory.set(safeBuildRoot.resolve(if (this == rootProject) "root" else name))
  }
}
