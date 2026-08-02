package org.waveskimmer.plugins

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class UpdateVersionsExtension @Inject constructor(layout: ProjectLayout) {

    /** Base URLs of Maven-layout repositories to check for newer versions, e.g. "https://repo1.maven.org/maven2". */
    abstract val artifactRepos: ListProperty<String>

    abstract val updateMajor: Property<Boolean>
    abstract val updateMinor: Property<Boolean>
    abstract val updatePatch: Property<Boolean>
    abstract val allowPrelease: Property<Boolean>

    /** The Gradle version catalog TOML file(s) to read and update. Defaults to just gradle/libs.versions.toml. */
    abstract val tomlFiles: ConfigurableFileCollection

    init {
        artifactRepos.convention(listOf("https://repo1.maven.org/maven2"))
        updateMajor.convention(false)
        updateMinor.convention(true)
        updatePatch.convention(true)
        allowPrelease.convention(false)
        tomlFiles.from(layout.projectDirectory.file("gradle/libs.versions.toml"))
    }
}