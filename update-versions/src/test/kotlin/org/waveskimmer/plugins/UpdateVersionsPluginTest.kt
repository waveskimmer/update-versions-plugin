package org.waveskimmer.plugins

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import org.gradle.testfixtures.ProjectBuilder

/**
 * A simple unit test for the 'org.example.greeting' plugin.
 */
class UpdateVersionsPluginTest: FunSpec({

    test("plugin registers task") {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.waveskimmer.update-versions")

        project.tasks.findByName("greeting") shouldNotBe null
    }
})
