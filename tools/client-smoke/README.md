# Client Startup Checks

This isolated run checks resource loading, camera and vanilla-collision access, projected HUD mixins, and client configuration. It stops normally after reaching the title screen or accessibility onboarding screen. The probe source is added only by the initialization script and is excluded from the normal mod JAR.

```powershell
.\gradlew.bat --init-script tools/client-smoke/smoke.gradle runClient
```

The instance uses `build/tmp/camera-smoke-instance`. It does not enter a world. Configuration changes and shutdown saves stay in that directory. With a complete dependency cache, add `--offline`; select a Gradle cache with the standard `-g` option.

## Success Criteria

A passing run logs `C1_CLIENT_SMOKE_PASS` and exits successfully. A successful Gradle result alone is insufficient. Investigate `C1_CLIENT_SMOKE_TIMEOUT`, loading errors, or a missing success marker. The probe allows up to 1,200 client ticks while waiting for the initial screen.

The startup check verifies that client mixins and resources load. In-world F5 input, camera framing, collision behavior, item use, and multiplayer behavior require separate testing.

## View Commands

The probe executes `/nfp view` with a permission-level-zero command source. It checks valid masks, leading zeroes, rejection of an all-zero mask, querying, and configuration saving. Success logs `VIEW_COMMAND_SMOKE_PASS`; the original isolated-instance mask is restored afterward.

This check operates on the isolated configuration without connecting to a world or changing an existing client instance. Switching away from a disabled current view still requires in-world testing.

## Shoulder Reversal Commands

The same command source executes `/nfp reverse`, `/nfp reverse auto`, and `/nfp reverse fix`. It checks independent and combined reversal, custom offset magnitudes, restoration after repeated reversal, centered offsets, and configuration saving. Success logs `REVERSE_COMMAND_SMOKE_PASS`.

A `finally` block restores the original horizontal offsets and view mask. The probe does not exercise movement near walls, wall dashes, or camera transitions inside a world.
