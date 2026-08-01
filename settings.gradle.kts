rootProject.name = "update-versions-plugin"
include("update-versions")

rootProject.children.forEach { subproject ->
    subproject.buildFileName = "${subproject.name}.gradle.kts"
}
