package org.waveskimmer.plugins

import io.kotest.assertions.AssertionErrorBuilder.Companion.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder

/**
 * A simple unit test for the 'org.example.greeting' plugin.
 */
class UpdateVersionsPluginTest: FunSpec({

    test("plugin registers task with defaults") {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.waveskimmer.update-versions")

        val task = project.tasks.findByName("checkLibsForUpdates") ?: fail("null task found")
        val checkTask = task as CheckForUpdatesTask
        checkTask.artifactRepos.get() shouldBe listOf("https://repo1.maven.org/maven2")
        checkTask.updateMajor.get() shouldBe false
        checkTask.updateMinor.get() shouldBe true
        checkTask.updatePatch.get() shouldBe true
        checkTask.allowPrelease.get() shouldBe false
    }
})
