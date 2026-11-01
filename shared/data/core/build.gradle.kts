plugins {
    id("ivy.feature")
    id("ivy.room")
    id("ivy.integration.testing")
}

android {
    namespace = "com.ivy.data"

    testOptions {
        // Robolectric needs the merged resources and manifest to build an
        // Application, and Room's in-memory builder needs a real Context.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // `api`, not `implementation`: ProfileRegistry and SharedPrefs-bearing
    // constructors are part of this module's public API, and consumers get them
    // injected via Hilt. With `implementation` the type is absent from a
    // consumer's compile classpath and Hilt's KSP step fails with the opaque
    // "error.NonExistentClass" rather than naming the missing type.
    api(projects.shared.base)
    api(projects.shared.data.model)

    api(libs.datastore)
    implementation(libs.bundles.ktor)

    testImplementation(projects.shared.data.modelTesting)
    // An in-memory Room database in a JVM test. Without it the atomicity of
    // TransferDecisionWriter could only be argued from code reading: the rollback
    // it relies on is Room's, and a mock cannot be rolled back.
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.bundles.integration.testing)
}
