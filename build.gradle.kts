// Top-level build file where you can add configuration options common to all sub-projects/modules.
// 版本号统一维护在 gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt) apply false
}
