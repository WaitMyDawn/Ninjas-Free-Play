# Animation Sources and Ground-Contact Baking

The editable source is `animations/player_movement.bbmodel`. Its twenty native Blockbench animations define limb poses, orientation, tucking, and extension. Normal Gradle builds export these resources through Java without running the Python tools in this directory.

## Rebuilding Crouches and Rolls

`refine_actions.py` is an optional authoring tool that uses the Blockbench document codec. Its default mode rebuilds the crouch and four directional roll baselines, replacing their keyframes. To preserve manual pose edits, use `--bake-only`, which rebuilds supported ROOT position tracks while retaining limb and rotation keyframes:

```text
python tools/animation-authoring/refine_actions.py --mcp-root PATH_TO_BLOCKBENCH_MCP --bake-only --review-dir build/reports/animation-review
```

The tool requires a separately installed `blockbench_mcp` document-codec module. Point `--mcp-root` to the checkout containing that module. Offline review images also require Pillow. The mod, Java exporter, and Java tests do not depend on this tool.

Use `--actions crouch_idle crouch_walk` to rebuild only those actions; all other animations and ROOT tracks remain unchanged. This option cannot be combined with `--bake-only`. Limit the selected actions when changing a crouch baseline to avoid replacing manually edited rolls or bullet jumps.

### Crouch Geometry

The crouch baseline places the torso at 37.5 degrees above the ground, keeps the head connected to the neck, and defines relaxed arm positions in the entity's horizontal facing coordinates. Hand positions are measured ahead of the player's feet, rather than along the tilted torso's local forward axis. Forearms bend approximately 40 degrees without forming an embracing pose.

The half-height collision box and eye position are independent of this visual silhouette. The raised head can extend beyond the collision box, so low ceilings need visual testing.

Forearms must bend toward the entity's horizontal forward direction. Check that each palm is ahead of both the feet and its corresponding elbow. The exporter negates the authoring control bone's X rotation to obtain the runtime bend; the current control-bone value is approximately positive 40 degrees. A one-pixel lateral shoulder offset and slight arm abduction provide clearance for standard armor. Check the entire forearm rather than only the wrist center, excluding the normal shoulder attachment region from overlap checks.

### Contact Baking

Baking uses the actual vertex bounds of the standard-arm player mesh. The palm endpoint is ten model pixels below the shoulder pivot, rather than the arm's full twelve-pixel length. Side and backward flips rebuild ROOT height during hand support; forward rolls rebuild height from the ground envelope of the deformed mesh. Generated tracks retain time handles and Bezier tangents for velocity continuity and remain editable in Blockbench.

Three JSON files under `common/src/main/resources/assets/ninjas_free_play/ninja_contacts/` define hand-support weights independently of limb poses. Time is expressed as normalized action phase. Rebake after changing contact timing; runtime duration changes retime contacts using the same phase.

The offline `geometry-contact-sheet.png` visualizes the actual skeleton, mesh dimensions, and bending algorithm with simplified colors. It is a geometry inspection artifact, not a gameplay screenshot or promotional image; it does not include player skins, equipment, or game lighting.

## Rebuilding Parkour Actions

By default, `author_parkour.py --mcp-root PATH` rebuilds twelve baseline actions: `aim_glide`, `wall_latch`, `wall_latch_left`, `wall_latch_back`, `wall_latch_right`, `wall_dash`, `wall_dash_left`, `wall_dash_right`, `wall_dash_away`, `slide`, `air_flip`, and `flying_kick`. Select actions explicitly when changing only part of the source:

```text
python tools/animation-authoring/author_parkour.py --mcp-root PATH --actions wall_latch aim_glide slide
python tools/animation-authoring/refine_actions.py --mcp-root PATH --actions roll_backward
```

These commands rebuild authoring baselines and overwrite the selected actions. Use Blockbench directly for routine artistic adjustments. Selecting a backward roll also runs its hand-support ROOT bake; other actions remain unchanged.

Wall-latch poses solve a shared wall plane from foot endpoint corners. The bent leg uses the actual length of the curved mesh deformation to solve its bend, rather than moving the hips forward to fill the gap. Changes to foot support, skin overlays, or standard boot clearance require checks of both endpoint orientation and joint attachment.

Speed-dependent tilt, view-direction layers, wall endpoint constraints, independent contact smoothing, and item blending belong to the shared runtime layer. Limb keyframes remain in the source project. Dynamic rendering layers do not grant movement actions or change the player's physical position.

### Hands, Items, and Direction

For lateral wall dashes, define reach and pull endpoint directions in a shared wall coordinate system, then convert them into upper-arm keyframes under the current torso and ROOT hierarchy. Changes to a single Euler component do not establish the hand's actual movement direction. `ParkourStyleTest` checks horizontal and vertical displacement of both hand endpoints.

Side-facing and back-facing wall-latch keyframes are separate source animations. Runtime blending uses quaternions between adjacent view sectors and an independent continuous channel for joint bends.

Eating and drinking use a head-relative target and the curved-joint solver in `ConsumptionArmPose`. This is item/rig adaptation rather than a separate static body animation. After changing mouth targets or elbow solving, check both arm widths, both hands, and glide tilt. This layer preserves the underlying body animation.

### Forward Rolls and Flying Kicks

Use `refine_actions.py --actions roll` to rebuild the forward dive, shoulder/back roll, recovery keyframes, and ROOT height. Arms and legs retain extension and separation. The dive's ground clearance closes once the shoulder/back can make contact. Offline images generated by `--review-dir` inspect action phases and the contact envelope; they do not replace in-game visual checks.

`author_parkour.py --actions flying_kick` maintains the right-side flying-kick silhouette. ROOT lowers the right shoulder; the torso and striking leg are nearly collinear, the other leg remains moderately tucked, and both arms have silhouette clearance. Validate the actual hierarchy and endpoints, not only Euler values.

## Read-Only Geometry Review

Inspect existing actions and phases without rewriting the source project:

```text
python tools/animation-authoring/refine_actions.py --mcp-root PATH --review-only --review-actions flying_kick --review-phases 0 0.09 0.135 0.18 0.5 --review-yaws 0 48 90 180 --review-dir build/reports/kick-extension-review
```

Review images use consistent camera-depth ordering for occlusion and omit game skins and equipment. `--review-yaws` adds a row for each specified viewing angle, defaulting to 48 degrees. It changes only the offline review camera, not the source animation.

`flying_kick` plays once through tuck, extension, and hold. The authoring tool compensates thigh rotation for shin bending so the terminal shin axis retains its direction. ROOT's Z rotation controls overall side tilt: 40 degrees during preparation and 65 degrees after extension. Its 90-degree X rotation lays the body along the force direction and must not be changed as part of a side-tilt adjustment.

Head keyframes provide an approximately 40-degree side glance while runtime view adaptation remains active. When editing the right-side pose, verify that the right shoulder is below the left for horizontal travel. Upward and downward kicks still align with the final foot axis, not the initial tucked-leg axis. Normal builds run only the Java exporter, not these inspection tools.
