plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {

    testImplementation(libs.bundles.kotest)

    testImplementation("org.jetbrains.kotlin:kotlin-test")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins.create("greeting") {
        id = "org.waveskimmer.update-versions"
        implementationClass = "org.waveskimmer.plugins.UpdateVersionsPlugin"
    }
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("testFunctional") {
}

configurations["testFunctionalImplementation"].extendsFrom(configurations["testImplementation"])
configurations["testFunctionalRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Add a task to run the functional tests
val testFunctional = tasks.register<Test>("testFunctional") {
    description = "functional tests"
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

gradlePlugin.testSourceSets.add(functionalTestSourceSet)

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Task>("check") {
    dependsOn(testFunctional)
}
