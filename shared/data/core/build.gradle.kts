plugins {
    id("ivy.feature")
    id("ivy.room")
    id("ivy.integration.testing")
}

android {
    namespace = "com.ivy.data"
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
    androidTestImplementation(libs.bundles.integration.testing)
}
