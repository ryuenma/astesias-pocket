plugins {
    org.jetbrains.kotlin.plugin.compose
    id("ivy.module")
    id("ivy.compose")
    id("ivy.paparazzi")
}

// `?attr/colorControlNormal` has to resolve wherever the shared/ui/core drawables
// are compiled, and they are compiled into every module's TEST APK as well as
// into the app. The app carries material/appcompat so it resolves there; a
// feature module carries neither, so assembling its androidTest APK failed with
// 19 AAPT errors ("resource attr/colorControlNormal not found") and the root
// `connectedDebugAndroidTest` could not build at all - which is how a 32-test
// instrumented suite stayed invisible.
//
// Fixed HERE rather than per module because 29 modules share this plugin, and
// `:feature:features` and `:feature:attributions` both failed with the identical
// error: patching one at a time is whack-a-mole against a shared cause.
// androidTest scope on purpose - the missing attr is a test-compile problem, and
// production code should not inherit a dependency to solve it.
dependencies {
    androidTestImplementation(libs.appcompat.activity)
}
