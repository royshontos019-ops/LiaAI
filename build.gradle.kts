// AGP 9+ compiles Kotlin natively: org.jetbrains.kotlin.android is intentionally NOT applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
