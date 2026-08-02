package org.waveskimmer.plugins

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.waveskimmer.plugins.utils.Collections.until
import org.waveskimmer.plugins.utils.Empty
import org.waveskimmer.plugins.utils.Some

/**
 * Reads one or more Gradle version catalog TOML files, checks the configured
 * Maven-layout repositories for newer versions of each cataloged library, and
 * rewrites each catalog in place with any updates that are allowed by the
 * update* flags.
 *
 * Only "[libraries]" entries are considered: the "module" (group:artifact) on a
 * library is used to resolve the group/artifact for whichever [versions] key
 * it references via version.ref. [plugins] entries are not resolved, since a
 * plugin id isn't a Maven coordinate.
 *
 * This assumes the common Gradle-generated catalog style of one entry per line.
 */
abstract class CheckForUpdatesTask : DefaultTask() {

    @get:OutputFiles
    abstract val tomlFiles: ConfigurableFileCollection

    @get:Input
    abstract val artifactRepos: ListProperty<String>

    @get:Input
    abstract val updateMajor: Property<Boolean>

    @get:Input
    abstract val updateMinor: Property<Boolean>

    @get:Input
    abstract val updatePatch: Property<Boolean>

    @get:Input
    abstract val allowPrelease: Property<Boolean>

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    @TaskAction
    fun run() {
        Processor(
            repos = artifactRepos.getOrElse(emptyList()),
            tomls = tomlFiles.files.toList(),
            useMajor = updateMajor.getOrElse(false),
            useMinor = updateMinor.getOrElse(true),
            usePatch = updatePatch.getOrElse(true),
            allowPrelease = allowPrelease.getOrElse(false),
        ).run()
    }

    private data class Coordinate(val group: String, val artifact: String)

    private data class VersionEntry(val key: String, val currentVersion: String, val lineIndex: Int)

    private class Catalog(
        val versionEntries: Map<String, VersionEntry>,
        val coordinatesByVersionKey: Map<String, Coordinate>
    )

    private data class SemVer(val major: Int, val minor: Int, val patch: Int)

    enum class BumpLevel {
        PATCH, MINOR, MAJOR


    }

    private inner class Processor(
        val repos: List<String>,
        val tomls: List<File>,
        val useMajor: Boolean,
        val useMinor: Boolean,
        val usePatch: Boolean,
        val allowPrelease: Boolean,
    ) {

        fun run() = when {
            repos.isEmpty() ->
                logger.warn("No artifactRepos configured on the updateVersions extension; skipping version check.")

            tomls.isEmpty() ->
                logger.warn("No tomlFiles configured on the updateVersions extension; skipping version check.")

            else -> tomlFiles.map {
                when {
                    it.exists() -> processToml(it)
                    else -> logger.warn("Toml file does not exist: $it")
                }
            }
        }

        private fun processToml(tomlFile: File) {

            val lines = tomlFile.readLines().toMutableList()
            val catalog = parseCatalog(lines)
            var changed = false

            for (entry in catalog.versionEntries.values) {
                val coordinate = catalog.coordinatesByVersionKey[entry.key]
                when {
                    coordinate == null -> {
                        // TODO: add to skipped file
                        logger.debug("[${tomlFile.name}] No library in [libraries] references version '${entry.key}' via version.ref; skipping.")
                    }

                    else -> {
                        val latest = findBestUpdate(coordinate, repos, entry.currentVersion)
                        if (latest != null && latest != entry.currentVersion) {
                            logger.lifecycle("[${tomlFile.name}] ${entry.key}: ${entry.currentVersion} -> $latest (${coordinate.group}:${coordinate.artifact})")
                            lines[entry.lineIndex] =
                                replaceQuotedValue(lines[entry.lineIndex], entry.currentVersion, latest)
                            changed = true
                        }
                    }
                }

                if (changed) {
                    tomlFile.writeText(lines.joinToString("\n", postfix = "\n"))
                    logger.lifecycle("Updated ${tomlFile.name}")
                } else {
                    logger.lifecycle("[${tomlFile.name}] All tracked versions are already up to date.")
                }
            }
        }


        private fun parseCatalog(lines: List<String>): Catalog {
            val versionEntries = LinkedHashMap<String, VersionEntry>()
            val coordinatesByVersionKey = HashMap<String, Coordinate>()

            var section = ""
            val sectionHeader = Regex("""^\[(\w+)]\s*$""")
            val versionLine = Regex("""^([\w.\-]+)\s*=\s*"([^"]+)"\s*$""")
            val moduleAttr = Regex("""module\s*=\s*"([^":]+):([^"]+)"\s?""")
            val versionRefAttr = Regex("""version\.ref\s*=\s*"([^"]+)"\s?""")

            lines.forEachIndexed { index, rawLine ->
                val trimmed = rawLine.trim()
                val headerMatch = sectionHeader.matchEntire(trimmed)
                if (headerMatch != null) {
                    section = headerMatch.groupValues[1]
                    return@forEachIndexed
                }

                when (section) {
                    "versions" -> {
                        versionLine.matchEntire(trimmed)?.let { m ->
                            val (key, value) = m.destructured
                            versionEntries[key] = VersionEntry(key, value, index)
                        }
                    }

                    "libraries" -> {
                        val moduleMatch = moduleAttr.find(rawLine)
                        val refMatch = versionRefAttr.find(rawLine)
                        if (moduleMatch != null && refMatch != null) {
                            val (group, artifact) = moduleMatch.destructured
                            val versionKey = refMatch.groupValues[1]
                            coordinatesByVersionKey.putIfAbsent(versionKey, Coordinate(group, artifact))
                        }
                    }
                }
            }

            return Catalog(versionEntries, coordinatesByVersionKey)
        }

        private fun replaceQuotedValue(line: String, oldValue: String, newValue: String): String =
            line.replaceFirst("\"$oldValue\"", "\"$newValue\"")

        private fun findBestUpdate(
            coordinate: Coordinate,
            repos: List<String>,
            currentVersionStr: String,
        ): String? {
            val allVersions = fetchAllVersions(repos, coordinate) ?: return null
            val current = parseSemVer(currentVersionStr)

            val candidates = allVersions
                .filter { allowPrelease || isStable(it) }
                .mapNotNull { candidateStr ->
                    val candidate = parseSemVer(candidateStr)
                    val level = bumpLevel(current, candidate) ?: return@mapNotNull null
                    val allowed = when (level) {
                        BumpLevel.MAJOR -> useMajor
                        BumpLevel.MINOR -> useMajor
                        BumpLevel.PATCH -> usePatch
                    }
                    if (allowed) candidateStr to candidate else null
                }

            return candidates.maxWithOrNull(
                compareBy(
                    { it.second.major },
                    { it.second.minor },
                    { it.second.patch })
            )?.first
        }

        private fun fetchAllVersions(repos: List<String>, coordinate: Coordinate): List<String>? =
            repos.until { repo ->
                fetchVersions(repo, coordinate).let {
                    when {
                        it.isNullOrEmpty() -> Empty
                        else -> Some(it)
                    }
                }
            }.getOrNull()

        private fun fetchVersions(repoBaseUrl: String, coordinate: Coordinate): List<String>? {
            val url = "${repoBaseUrl.trimEnd('/')}/${coordinate.group.replace('.', '/')}/" +
                    "${coordinate.artifact}/maven-metadata.xml"
            return try {
                val request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                when {
                    response.statusCode() == 200 -> parseVersionsFromMetadata(response.body())
                    else -> {
                        logger.debug("$url returned HTTP ${response.statusCode()}")
                        null
                    }
                }
            } catch (e: Exception) {
                logger.debug("Failed to fetch metadata from $url: ${e.message}")
                null
            }
        }

        private fun parseSemVer(version: String): SemVer =
            version.substringBefore('-').split('.').let { parts ->
                SemVer(
                    major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                    minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                    patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                )
            }

        private fun bumpLevel(current: SemVer, candidate: SemVer): BumpLevel? = when {
            candidate.major > current.major -> BumpLevel.MAJOR
            candidate.major < current.major -> null
            candidate.minor > current.minor -> BumpLevel.MINOR
            candidate.minor < current.minor -> null
            candidate.patch > current.patch -> BumpLevel.PATCH
            else -> null
        }
    }
}

private fun parseVersionsFromMetadata(xml: String): List<String> {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = false
    val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
    val nodes = doc.getElementsByTagName("version")
    return (0 until nodes.length).map { nodes.item(it).textContent.trim() }
}

private fun isStable(version: String): Boolean {
    val lower = version.lowercase()
    val markers = listOf(
        "alpha",
        "beta",
        "-rc",
        ".rc",
        "rc1",
        "rc2",
        "snapshot",
        "-dev",
        ".dev",
        "eap",
        "-cr",
        ".cr",
        "-m",
        ".m"
    )
    return markers.none { lower.contains(it) }
}
