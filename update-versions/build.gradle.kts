plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use the Kotlin Test integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins.create("greeting") {
        id = "org.example.greeting"
        implementationClass = "org.example.UpdateVersionsPluginPlugin"
    }
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("testFunctional") {
}

configurations["testFunctionalImplementation"].extendsFrom(configurations["testImplementation"])
configurations["testFunctionalRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Add a task to run the functional tests
val testFunctional = tasks.register<Test>("testFunctional") {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

gradlePlugin.testSourceSets.add(functionalTestSourceSet)

tasks.named<Task>("check") {
    // Run the functional tests as part of `check`
    dependsOn(testFunctional)
}

tasks.named<Test>("test") {
    // Use JUnit Jupiter for unit tests.
    useJUnitPlatform()
}
