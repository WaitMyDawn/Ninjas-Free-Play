# Development

The repository contains independent platform builds and a shared Java 17 library. NeoForge 1.21.1 is the primary platform. The Forge 1.20.1 gameplay port is implemented and undergoing platform-specific acceptance.

## Projects

| Directory | Contents | Toolchain |
| --- | --- | --- |
| `common/` | Movement rules, animation playback/codecs, camera math, action-state types, assets, and unit tests | Java 17 |
| `platforms/neoforge-1.21.1/` | Minecraft and NeoForge integration, rendering, networking, and GameTests | Java 21, Gradle 9.2.1 |
| `platforms/forge-1.20.1/` | Minecraft 1.20.1 / Forge integration, rendering, networking, and GameTests | Java 17, ForgeGradle 6.0.54, Gradle 8.14.3 |
| `animations/` | The single Blockbench source project and manifest | Shared by both platforms |
| `tools/` | Exporter, optional authoring tools, and client probes | See each tool's README |

Each platform includes `common` as a plain Java project. Shared classes compile to Java 17 bytecode and join the platform's development mod roots and final JAR. Players do not install a separate common mod. Shared outputs live under `platforms/<platform>/build/shared/common`, preventing concurrent builds from overwriting each other.

The shared compile and test classpaths pin Gson to 2.10, the version supplied by Minecraft 1.20.1. Keep runtime calls compatible with that API. Platform builds use the game's Gson and do not replace or bundle it.

## IDEA and Java

Install JDK 17 and JDK 21. Link the `build.gradle` file in each platform directory as an independent Gradle project in IDEA. Keep builds and tests delegated to Gradle. Run configurations belong to their platform.

IDEA's **Gradle JVM** runs Gradle itself. The Java toolchains declared in the build scripts select the compilers and test runtimes. Selecting a project SDK such as `ms-25` for the Gradle JVM does not change the mod's Java target.

| Build | Gradle JVM | Project toolchain |
| --- | --- | --- |
| Root orchestrator | Java 21 recommended; Java 25 supported by Gradle 9.2.1 | Selects Java 21 for the NeoForge wrapper and Java 17 for Forge |
| NeoForge 1.21.1 | Java 21 recommended to match CI; Java 25 supported by Gradle 9.2.1 | Java 21; shared code and exporter use Java 17 |
| Forge 1.20.1 | Use Java 17; Gradle 8.14.3 does not support running on Java 25 | Java 17 |

An existing NeoForge Gradle JVM set to Java 25 can be retained. JDK 17 and JDK 21 must still be available for the declared toolchains. Successful synchronization confirms project import; build, GameTest, and client startup checks remain separate validation steps. The Java 21/17 build and game-runtime baselines remain unchanged.

See Gradle's [Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html) and [toolchain guide](https://docs.gradle.org/current/userguide/toolchains.html).

Gradle can discover installations through `JAVA_HOME_17_X64` and `JAVA_HOME_21_X64`. For other installation layouts, set `org.gradle.java.installations.paths` in local Gradle user properties or pass it as a project property. Keep machine-specific paths out of the repository.

## Building

From the repository root on Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat buildNeoForge
.\gradlew.bat buildForge
.\gradlew.bat generateAnimations
```

The root orchestrator invokes each platform wrapper with its required JDK and propagates failures. `check` checks both platforms; `clean` clears platform outputs. Neither command removes the legacy root-level run instances.

Run platform tasks explicitly:

```powershell
.\gradlew.bat -p platforms/neoforge-1.21.1 runClient
.\gradlew.bat -p platforms/neoforge-1.21.1 runGameTestServer
.\platforms\forge-1.20.1\gradlew.bat -p platforms/forge-1.20.1 runClient
.\platforms\forge-1.20.1\gradlew.bat -p platforms/forge-1.20.1 runGameTestServer
```

On Linux, enter a platform directory and run `bash ./gradlew <task>`. Test another Forge 47.x version with `-Pforge_version=<version>`.

Mod JARs are written to each platform's `build/libs`. Filenames include the loader, Minecraft version, and shared mod version. Root `gradle.properties` maintains shared mod metadata; platform properties maintain Minecraft and loader versions.

The Forge distributable includes MixinExtras 0.4.1 through Jar-in-Jar. Players install the single JAR in `build/libs`; unbundled intermediate JARs in `build/devlibs` are development artifacts. MixinGradle generates the refmap and remaps both distributable and intermediate artifacts. Keep both mixin configurations, the refmap, and the nested dependency metadata in the distributable.

## Runtime Directories

Development worlds, configs, and logs live under `platforms/<platform>/run/<run-name>`. Existing root `run/` data is preserved but is no longer the active platform instance. Back up worlds before copying them to a compatible instance; never share a world directory between Minecraft versions.

The first `runServer` launch creates `run/server/eula.txt` and exits until the Minecraft EULA is accepted. After reviewing the agreement, set `eula=true` in that platform's instance and restart `runServer`. Acceptance is local to each instance and is not generated by the build. Look for the server's `Done` message to confirm startup; Gradle can report `BUILD SUCCESSFUL` when the server exits normally without starting a world.

The isolated NeoForge client probe is documented in [tools/client-smoke/README.md](tools/client-smoke/README.md). Startup probes do not establish in-world or multiplayer acceptance.

## Forge Platform Adapters

Forge implements the nine movement actions, real collision sizes, prediction/reconciliation, action events, bendable player rendering, equipment and item transforms, wall particles, both NFP views, and client commands. Movement rules and the twenty animation clips remain shared.

The adapters preserve Minecraft 1.20.1 behavior at the version boundary:

- `SimpleChannel` uses protocol 10 with fixed packet directions and main-thread consumers. Serverbound input uses the connection's player identity. Both platforms synchronize held-C landing-slide intent independently of the flying-kick clip's lifetime; clients and servers must update together.
- Accepted position packets supply server movement samples. Each packet records one displacement rather than accumulating all arrivals in a server tick. Rotation-only packets leave that sample intact; samples expire after three ticks and exclude teleports. Client-supplied prediction power never determines authoritative impulses.
- Slide launch-speed differences allow sampling tolerance. Significant speed-only differences correct velocity without teleporting; action, attribute, and resource disagreements still use positional reconciliation.
- Glide gravity modifies Forge's effective entity-gravity attribute, including native slow-falling adjustments. Step height uses Forge's native step-height addition, and vanilla sneak speed includes Swift Sneak.
- Compact poses adapt `getDimensions` and eye height. Minecraft 1.20.1 has no vanilla scale attribute; collision sizes use the standing-dimension reference. Compatibility with third-party resizing mods requires its own verification.
- Player skin model selection, float RGBA vertex output, camera rotation, and GUI/render injection signatures follow Minecraft 1.20.1 APIs.
- Automatic-view reticle projection uses the actual world-render view/projection matrices after `Camera.setup`. Minecraft 1.20.1 sets projection before the camera and its camera quaternion uses +Z forward, so the newer camera-quaternion shortcut cannot be reused here.

Use `MovementActionEvent.getActionPhase()` on both platforms. Forge's inherited `Event.getPhase()` represents event dispatch priority. NeoForge retains its earlier action-phase `getPhase()` accessor for compatibility.

Automated checks cover shared rules, real movement/collision/damage behavior, event lifecycle, camera geometry, packet codecs, resource loading, skin-layer bend transfer, bent mesh output, item-hand transforms, and permission-zero view commands. In-world composition, full equipment combinations, actual multiplayer connections, and latency behavior still require Forge-specific acceptance.

Local loopback verification also exercised a real client connection to the distributable running on a stock Forge 47.3.22 server: crouch, bullet jump, air roll with carried momentum, glide, walking into a slide, and a held-C flying kick landing into a slide received authoritative acknowledgements. Both NFP reticles were visible after world rendering, each slide ended on C release, and the sequence required no movement corrections. The isolated probe retried an initial FML login timeout before completing the check. This covers one client and one disposable server; observer synchronization, full equipment combinations, visual quality, dimension changes, in-world reconnects, and latency remain separate acceptance work.

## Verification and Releases

Run shared tests and platform GameTests before publishing. Compare generated animations when changing the export pipeline. Check dedicated-server startup and keep client-only classes out of server initialization. Visual and multiplayer acceptance remain platform-specific.

CI builds NeoForge and minimum/newer Forge versions separately. Keep shared changes and their platform adapters together on `main`; use temporary feature branches for incomplete work. Generated outputs, caches, local instances, and authoring backups remain ignored.
