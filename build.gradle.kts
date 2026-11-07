// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.android.legacy-kapt") version "9.5.1" apply false
    id("io.objectbox") version "5.4.2" apply false
}