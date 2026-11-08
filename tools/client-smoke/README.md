# Client Startup Checks

This isolated run checks resource loading, camera and vanilla-collision access, projected HUD mixins, and client configuration. It stops normally after reaching the title screen or accessibility onboarding screen. The probe source is added only by the initialization script and is excluded from the normal mod JAR.

```powershell
.\gradlew.bat -p platforms/neoforge-1.21.1 --init-script ../../tools/client-smoke/smoke.gradle runClient
```

The instance uses `platforms/neoforge-1.21.1/build/tmp/camera-smoke-instance`. It does not enter a world. Configuration changes and shutdown saves stay in that directory. With a complete dependency cache, add `--offline`; select a Gradle cache with the standard `-g` option.

## Success Criteria

A passing run logs `C1_CLIENT_SMOKE_PASS` and exits successfully. A successful Gradle result alone is insufficient. Investigate `C1_CLIENT_SMOKE_TIMEOUT`, loading errors, or a missing success marker. The probe allows up to 1,200 client ticks while waiting for the initial screen.

The startup check verifies that client mixins and resources load. In-world F5 input, camera framing, collision behavior, item use, and multiplayer behavior require separate testing.

## View Commands

The probe executes `/nfp view` with a permission-level-zero command source. It checks valid masks, leading zeroes, rejection of an all-zero mask, querying, and configuration saving. Success logs `VIEW_COMMAND_SMOKE_PASS`; the original isolated-instance mask is restored afterward.

This check operates on the isolated configuration without connecting to a world or changing an existing client instance. Switching away from a disabled current view still requires in-world testing.

## Shoulder Reversal Commands

The same command source executes `/nfp reverse`, `/nfp reverse auto`, and `/nfp reverse fix`. It checks independent and combined reversal, custom offset magnitudes, restoration after repeated reversal, centered offsets, and configuration saving. Success logs `REVERSE_COMMAND_SMOKE_PASS`.

A `finally` block restores the original horizontal offsets and view mask. The probe does not exercise movement near walls, wall dashes, or camera transitions inside a world.

## Forge

Run the separate Forge probe with Java 17:

```powershell
.\platforms\forge-1.20.1\gradlew.bat -p platforms/forge-1.20.1 --init-script ../../tools/client-smoke/forge-smoke.gradle runClient
```

The instance uses `platforms/forge-1.20.1/build/tmp/foundation-smoke-instance`. Success requires `FORGE_CLIENT_SMOKE_PASS`, `FORGE_MODEL_SMOKE_PASS`, `VIEW_COMMAND_SMOKE_PASS`, `REVERSE_COMMAND_SMOKE_PASS`, and a normal exit. The original foundation-only marker belongs to the earlier bootstrap check.

The probe checks movement/collision and camera bridges, the twenty animation clips, standard/slim skin-layer bend copying, finite bent-mesh vertices, and the item-hand transform. It also executes the same permission-zero view and shoulder commands as NeoForge. It stops at the initial screen without entering a world; screenshots, actual input combinations, full equipment rendering, and multiplayer behavior remain separate checks.

## Forge World and Input Check

Start a disposable flat, peaceful Forge server on `127.0.0.1` with the matching mod JAR, default movement settings, and at least 50 blocks of clear ground. The development client uses the name `Dev`; offline mode is required for this loopback fixture. Keep this test world separate from regular instances.

```powershell
.\platforms\forge-1.20.1\gradlew.bat -p platforms/forge-1.20.1 --init-script ../../tools/client-smoke/forge-gameplay.gradle -PnfpLoopbackPort=25565 runClient
```

The client uses `build/tmp/gameplay-smoke-instance` inside the Forge project. It waits for terrain loading, enters the automatic view, and requires a visible projected reticle before starting its action sequence. It then checks authoritative crouch, bullet jump, momentum-preserving air roll, glide, landing, full-speed walking into a continuous slide, a held-C flying kick landing into another slide, releasing C after each slide, and the fixed-view reticle.

Success requires `FORGE_GAMEPLAY_SMOKE_PASS`, a normal client exit, and `passed: true` in the root `build/tmp/forge-gameplay-result.json`. The report includes server acknowledgements, both rendered-reticle flags, slide correction counts, and connection attempts. Login disconnects allow at most three attempts; this does not test reconnecting an active gameplay session. The initialization script excludes probe classes from ordinary builds. Stop the disposable server after testing.

This check covers one real client/server connection and visible reticle projection state. It does not judge animation quality, equipment composition, pixel alignment, observers, or weak-network behavior.
