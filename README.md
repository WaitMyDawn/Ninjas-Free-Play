# Ninjas Free Play

If you're used to going anywhere in Warframe, Minecraft can feel a little grounded. Ninjas Free Play brings that freedom of movement into your world: slide into a bullet jump, glide across a gap, latch onto a wall, and kick off into your next jump.

Slides, jumps, and rolls flow into one another, with bending elbows and knees and your own Minecraft skin. Two shoulder views let you watch yourself move.

## Movement

These are the default controls. You can rebind them in Minecraft's Controls menu. **Ctrl** remains sprint and **Shift** remains vanilla sneak. In Creative Mode, turn off flight to use these moves.

| Move | Controls | What it does |
| --- | --- | --- |
| Crouch | Hold **C** | Lower yourself to half your standing height and move in a low stance. Sprint to stand up when there is room. |
| Slide | Reach full walking speed on the ground, then press and hold **C** | Drop into a slide, gain an initial speed boost, and carry your momentum. Sprinting works during a slide. |
| Bullet jump | Hold **C** and press **Space** | Launch in the direction you're looking, with an extra upward lift. Aim upward to gain height. Your lowered hitbox lets you pass through openings one block high. |
| Double jump | Release **Space**, then press it again in the air | Jump again with a flip. You can use this after a normal jump or a bullet jump. |
| Roll | Press **Caps Lock** | Roll forward, or use **A**, **D**, or **S** to choose a side flip or backward handspring. Available on the ground and in the air. |
| Aim glide | Hold **Right Mouse Button** in the air | Keep moving while slowing your fall. You can still use bows, shields, food, and other items. |
| Wall latch | Hold **Right Mouse Button** in the air beside a wall | Plant your feet against the wall and hold your position, leaving your hands free to use items. |
| Wall dash | While airborne beside a wall, press **Space** again | Kick up, along, or away from the wall. Keep holding Space after the first wall dash to continue dashing. |
| Flying kick | Press **C** while moving fast enough in the air | Kick along your movement direction, damaging targets and transferring your momentum into knockback. |

### Jumps and momentum

You have **two jumps** by default. Bullet jumps use the same jump allowance, with **one bullet jump before landing or wall dashing**. You can jump normally and then bullet jump, or bullet jump and then double jump. Walking off a ledge uses the first jump. Landing restores your jumps.

Slides start at ordinary walking speed; sprinting isn't required. The initial slide boost is **1.5x** by default. Taking damage or being knocked briefly into the air won't cancel your slide. Release **C** to stop. Rolling carries your existing momentum, so you can roll straight out of a fast bullet jump.

Air rolls don't use a jump, but you get one air roll before landing. Forward rolls keep you low; side flips and backward handsprings need more headroom.

### Working the walls

Jump off the ground first, then press Space again when you're close to the wall.

| Where you're looking | Movement input | Wall dash |
| --- | --- | --- |
| Toward the wall | **W** | Up the wall |
| Toward the wall | **A / D** | Left / right along the wall |
| Toward the wall | **S** | Away from the wall |
| Along the wall | **W** | Along the wall in the direction you're looking |
| Along the wall | The movement key pointing into the wall | Up the wall |
| Along the wall | The movement key pointing away from the wall | Away from the wall |

A wall dash refreshes your jumps, bullet jump, and glide/latch time, then uses one jump. Hold C with Space to bullet jump instead of wall dashing.

Wall latching needs a continuous surface beside most of your body. A lone block sticking out of a wall won't give you enough support.

### Staying airborne

Aim glide and wall latch share **4 seconds** of airtime by default. Switching between them or releasing and holding right-click again uses the same remaining time. Landing or wall dashing refreshes it.

You can glide during a roll or flying kick. Starting a glide during a bullet jump ends the bullet-jump animation while keeping your momentum.

Wall latches and wall dashes reset the fall distance you've built up. Landing while gliding or rolling reduces fall damage by **75%**.

### Flying kicks

Release C after a bullet jump, then press it again to kick. You can kick forward, upward, or downward, depending on your movement.

A kick deals **2 damage** by default and can hit several targets, once each. Faster movement gives stronger knockback, and the target's knockback resistance reduces it. During the hit, you're protected from that target's direct touch damage.

Keep holding **C** until you land to flow into a slide, provided you're still moving fast enough horizontally. This also works when the kick animation finishes before you land. Release **C** to end the slide.

Try a chain like this:

**Run → C to slide → C + Space to bullet jump → release C and Space → hold right-click to glide → approach a wall → Space to wall dash.**

## Camera

Press **F5** to cycle through:

**First Person → Vanilla Rear View → NFP Automatic View → NFP Fixed View → Vanilla Front View**

Both NFP views place your character to the side, leaving the center clear. Your character's rolls and flips won't roll the camera.

- **NFP Automatic View:** the crosshair follows your actual aiming point. It can move away from the center at close range.
- **NFP Fixed View:** the crosshair stays centered. Your character keeps a distant aiming direction, so nearby walls don't pull your movement sideways. Close hits can land beside the crosshair.

## Commands

All commands below run locally and work without cheats or operator permissions. Camera settings are saved across worlds and restarts.

### Choose your F5 views

| Command | Effect |
| --- | --- |
| `/nfp view` | Show your current view selection and the order of its five slots. |
| `/nfp view 11111` | Include all five views. |
| `/nfp view 10110` | Include only First Person and the two NFP views. |
| `/nfp view 00010` | Use only NFP Fixed View. |

Each digit enables (`1`) or skips (`0`) a view in this order:

| Slot | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- |
| View | First Person | Vanilla Rear | NFP Automatic | NFP Fixed | Vanilla Front |

If you disable the view you're using, you'll switch to the next enabled view. At least one must stay enabled; `00000` is rejected.

### Switch shoulders

| Command | Effect |
| --- | --- |
| `/nfp reverse` | Swap left and right for both NFP views. |
| `/nfp reverse auto` | Swap shoulders for NFP Automatic View. |
| `/nfp reverse fix` | Swap shoulders for NFP Fixed View. |

Your chosen camera distance is preserved. Run the same command twice to return to the original side.

### Animation previews

Preview poses and animations locally for screenshots. Press **Tab** to browse animation names.

| Command | Effect |
| --- | --- |
| `/nfp animation play <clip>` | Play an animation. |
| `/nfp animation sample <clip> <seconds>` | Hold the pose at a chosen time in an animation. |
| `/nfp animation chain <first> <second> <interval>` | Alternate between two animations; the interval is in seconds. |
| `/nfp animation view <degrees>` | Rotate the previewed character. |
| `/nfp animation aim <pitch>` | Set the aim angle for directional previews: `-90` is straight up, `0` is level, and `90` is straight down. |
| `/nfp animation aim reset` | Use your normal look direction for the preview. |
| `/nfp animation stop` | End the preview and return to normal animations. |

For example:

```mcfunction
/nfp animation play ninjas_free_play:bullet_jump
/nfp animation stop
```

## Installation and settings

For **Minecraft 1.21.1** with **NeoForge 21.1.235 or newer**. Put the mod in the `mods` folder on your client and server. No additional animation mods are required.

Forge 1.20.1 (47.3.22+) is available as an experimental port with the same movement and camera controls.

Camera and animation settings are in `config/ninjas_free_play/ninjas_free_play-client.toml`. Movement defaults are in `ninjas_free_play-server.toml` under the same configuration folder, or the world's `serverconfig/ninjas_free_play` folder when overridden per world. Server settings control jump counts, movement strength, slide friction, airtime, and flying-kick damage and knockback.

## License

Copyright © 2026 Yagen. All rights reserved. See [LICENSE.md](LICENSE.md).

For source builds and platform setup, see [DEVELOPMENT.md](DEVELOPMENT.md).
