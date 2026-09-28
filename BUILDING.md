# Building antichud

antichud is a [Stonecutter](https://stonecutter.kikugie.dev/) project. One checkout holds the
sources for every supported Minecraft version and `gradle build` produces **one jar per version**.

## Supported versions

| Minecraft | Java | Obfuscated | Fabric API |
|---|---|---|---|
| 1.21.11 | 21 | yes | 0.141.6+1.21.11 |
| 26.1 | 25 | no | 0.155.3+26.1.2 |
| 26.1.1 | 25 | no | 0.155.3+26.1.2 |
| 26.1.2 | 25 | no | 0.155.3+26.1.2 |
| 26.2 | 25 | no | 0.161.0+26.2 |
| 26.3 | 25 | no | 0.161.0+26.3 |

## Layout

```
src/                         the sources, written against 1.21.11
  main/java, main/resources
  client/java, client/resources
versions/<minecraft version>/
  gradle.properties          loader / fabric api / java version for that one version
build.gradle                 shared build script, evaluated once per version
settings.gradle              the list of versions, and the Stonecutter setup
.sc_active_version           the version an IDE compiles straight from src/
```

The list of versions lives in one place, `settings.gradle`:

```groovy
stonecutter {
    kotlinController = false
    centralScript = 'build.gradle'

    create(rootProject) {
        versions '1.21.11', '26.1', '26.1.1', '26.1.2', '26.2', '26.3'
    }
}
```

Adding a version means adding a directory under `versions/` with a `gradle.properties` and adding
the version to that list. Nothing else has to change unless the game API moved.

## Building

```sh
gradle build                  # every version
gradle :26.3:build            # one version
gradle :26.3:runClient        # launch the dev client for that version
```

Output lands in `versions/<minecraft version>/build/libs/`, named after the version it targets:

```
versions/1.21.11/build/libs/antichud-0.2+1.21.11.jar
versions/26.3/build/libs/antichud-0.2+26.3.jar
```

Each jar declares `"minecraft": "<its own version>"` in `fabric.mod.json`, so the loader only
accepts it on the Minecraft release it was built for. Players download the matching file.

### The daemon JVM

`gradle/gradle-daemon-jvm.properties` pins the daemon to Java 25. This is not a preference: Gradle
9.7's Groovy DSL compiler cannot read Java 27 (class file major version 71) bytecode, so starting a
build from a Java 27 `java` fails with

```
BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_' Unsupported class file major version 71
```

before any task runs. Gradle provisions Java 25 itself, so the JDK your `gradle` command runs on
does not matter.

## How the version differences are expressed

Sources are written against 1.21.11. Two mechanisms handle newer versions:

1. **Text replacements** in `build.gradle`, for pure renames. Each block is gated on a version
   range, so 26.1, 26.2 and 26.3 each get only what they need. The version ranges are named at the
   top of `build.gradle` (`unobfuscated`, `guiScreens`, `splitPacks`):

   ```groovy
   def splitPacks = sc.current.parsed.matches('>=26.3')

   stonecutter {
       replacements.string(splitPacks) {
           replace 'com.mojang.blaze3d.pipeline.RenderPipeline',
                   'com.mojang.renderpearl.api.pipeline.RenderPipeline'
       }
   }
   ```

2. **`//?` conditional comments** in the source, for structural differences. The default (plain
   Java) branch is the 1.21.11 code, so the file still compiles as-is in an IDE pointed at `src/`:

   ```java
   //? if >=26.3 {
   /*Blaze3D.openUri(URI.create(url));
   *///?} else {
   Util.getPlatform().openUri(url);
   //?}
   ```

Prefer a replacement over a `//?` block; only reach for `//?` when the code really has to differ
in shape.

## The active version

`src/` is written against 1.21.11, so `.sc_active_version` says `1.21.11` and an IDE compiles
`src/` directly with no preprocessing. Change that file when you want to edit against a different
version; Stonecutter regenerates it.

## Unobfuscated versions

Minecraft 26.1 and later ship unobfuscated, so Loom has nothing to remap. Those versions set

```properties
fabric.loom.disableObfuscation=true
```

in their `gradle.properties`, and `build.gradle` then skips `mappings`/`modImplementation` and
hooks the checksum task onto `jar` instead of `remapJar`.
