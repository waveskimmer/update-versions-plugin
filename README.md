### Grade plugin org.waveskimmer.update-versions

## TL;DR

This plugin automates the discovery of version updates for dependencies. Usually,
this is a manual process that often gets overlooked such that technical debt and
security issues creep into the code base.

## Using

Add the plugin to the build gradle file:

Kotlin:

```kotlin
plugins {
    id("org.waveskimmer.update-versions") version "0.1.0-SNAPSHOT"
}
```

Groovy:

```groovy 
plugins {
    id('org.waveskimmer.update-versions') version "0.1.0-SNAPSHOT"
}
```

And, you can run the check versions task:

```bash
./gradlew checkForUpdates
```

By default, it will check the versions in the https://repo1.maven.org/maven2 repository
and update the "gradle/libs.versions.toml" file. These (and more) can be figured
as discussed in the next section.

## Configuration

An extension block can be added as: 

