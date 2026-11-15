# ViveCraftX

VR for Minecraft 1.7.10, as a Forge mod.

Vivecraft has existed for 1.7.10 before, as *minecrift*, but only as a set of patches against
decompiled Minecraft source: you ran a Python script, it downloaded MCP, merged OptiFine into the
jar, decompiled the lot and applied a few hundred diffs. The result could not coexist with other
mods, could not be installed by dropping a file into a folder, and could not be updated without
redoing all of it.

This is the same thing rebuilt as an ordinary Forge mod. It reaches vanilla through mixins rather
than by editing it, so it sits alongside other mods instead of replacing the game; it uses
[Angelica](https://github.com/GTNewHorizons/Angelica) where minecrift used OptiFine, and works
without either.

---

## Requirements

| | |
|---|---|
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 or later |
| Runtime | SteamVR (any headset SteamVR drives, including Quest over Link or Virtual Desktop) |
| Java | 8 |

No other mods are required. Angelica, NotFine and the rest of that family are optional and
supported.

## Installing

Drop the jar in `mods/` and start the game with SteamVR running.

If SteamVR is not running, or no headset is found, the mod stays out of the way and the game runs
flat. There is a **VR on/off** button beside the main menu, so you can switch without restarting —
useful for showing someone the game on the monitor, or for playing an evening without the headset.
The choice is remembered between sessions.

You can also just **take the headset off**. The game comes back to the monitor on its own, with
the keyboard and mouse working, and goes back into the headset when you put it on again, in the
same world and the same session. Nothing is unloaded in between.

SteamVR shows the session as **VivecraftX**, with an icon, rather than as `javaw.exe`.

## Multiplayer

The mod is client-side, with one exception: other players' heads and hands.

Minecraft describes a player with a position and two angles, which is everything someone on a
keyboard has. A player in a headset produces far more, and none of it reaches anybody else — so
without help, two people in one world cannot wave, point, or see where the other is looking.

Putting the mod on the **server** as well turns that on: the server relays each VR player's head
and hands to everyone close enough to see them, and their figure is then posed from it -- head
looking where they are looking, arms reaching where their hands are, leaning out from cover,
armour and held items following the limb they are on.

It also fixes teleporting. A teleport is movement the client decides on, and any server snaps a
player back who moves more than ten blocks in one tick — that check is there for good reason. On
a server with this mod, the client asks and the server does the moving, so a teleport of any
length works. Without it, teleports still work but are capped at nine blocks, which is as far as
a server will accept.

That gives the administrator the say, which is the other half of it. `config/vivecraftx.cfg` on
the server can turn teleporting off entirely, make it cost hunger when it carries someone upward,
and force distance limits over whatever the players have set. Everything defaults to allowed:
players who need teleporting usually need it to be able to play at all rather than to gain
anything.

None of this is required. On a server without the mod everyone still plays normally.

---

## What it does

### Moving

- **Room-scale.** Your own feet move you. The play area follows you through the world, through
  teleports, and onto boats and minecarts.
- **Teleport** with a visible arc: pale where you can land, grey where you cannot, with rings on
  the spot. Separate limits for how far it may carry you up, down and along.
- **Free movement** by stick, aimed by the off hand, the headset, or where you are running on the
  spot — plus inertia and a speed multiplier for people who find full speed unpleasant.
- **Turning** in steps or smoothly, with a turn speed and a stick deadzone.
- **Climbing, swimming, jumping, crouching** with your body rather than with keys.
- **Rowing** a boat by pulling your arms back; one arm steers.
- **Horses** by flicking the reins, and a jump charged by raising a hand.
- **Stepping out** of a boat or cart to leave it.
- **World scale and rotation**, for playing large or small.

### Hands

- Your arms are drawn where the controllers are, wearing your own skin.
- Held items sit in the hand the way they do in modern Vivecraft — the per-item positions,
  rotations and sizes are taken from it, with the display transforms this version of the game
  does not have supplied by hand.
- **Swinging** actually hits things, and mines them, at a speed threshold.
- **Shoving a door** opens it. The same for gates, hatches, levers and buttons.
- **Raising food to your mouth** eats it.
- **Reaching over your shoulder** reaches the backpack.
- A **world crosshair** at the point you are actually aiming at, not on a panel.
- A **ring on the ground** showing where your body is when you have leaned out of it.

### Interface

- The HUD rides on your **wrist**, in your **hand**, or in front of your **face**, with an
  adjustable position along the forearm.
- A **radial menu** of eight actions: hold one button, point with the other hand, let go.
- A **keyboard standing in the world** that you poke with your fingertips, or SteamVR's own
  overlay keyboard if you prefer it.
- **Touch hotbar**: brush the hotbar on your wrist to change slot.
- Menus and screens appear in the world as panels you point at.
- A **menu room** to stand in while no world is loaded.
- The figure in the **inventory** stands the way you are standing: its head looks where you are
  looking and its arms point where your hands are, armour included.

### On the monitor

What anyone watching sees, and what you see yourself when the headset is off. Pick one in
**VR Settings → Stereo Rendering**:

| | |
|---|---|
| **Cropped** | one eye filling the window, with the edges nobody can see through the lens cut off. The default. |
| **Single eye** | the whole of one eye, fitted, with black at the sides |
| **Both eyes** | the pair side by side |
| **Interface only** | the flat game interface on its own, with no world |
| **Off** | a black window |

Which eye, and how much of the edge to throw away, are adjustable. The interface can also be
drawn flat over any of them, for whoever is watching — off by default, since for you it is a
second copy of something already in front of you.

### Comfort

- **Vision narrows** while you are being moved under power — the single most effective thing
  against motion sickness, and it leaves you alone while you are walking on your own feet.
- **Fixed view height**: the view stops following the headset up and down, so you can play lying
  down or slumped back without losing the controllers to seated mode. The whole tracking space
  moves, so your hands come with your head.
- Teleport instead of free movement, step turning instead of smooth, and all the knobs above.

### Felt and heard

- The controllers buzz for what you do — hitting, drawing a bow, grabbing a ladder, eating — and
  for what the world does to you: damage, fire, low health, poison, rain, starving.
- Sound is heard **from your head**, facing where you are facing, rather than from your body.

---

## Controls

Bindings come from **SteamVR Input**. The mod ships default binding profiles for Vive wands,
Index controllers, Oculus Touch, WMR and Cosmos, in both hand orders, and SteamVR's own binding
editor can change any of them — including for controllers the mod has never heard of.

Everything else lives in the in-game VR settings, reachable from the pause menu.

The first thing worth doing is **Calibrate Height**: stand up straight and press it. It tells the
mod how tall you are, which decides how far you can reach and where the fixed view height holds
you.

---

## Building

```
./gradlew build
```

Nothing to download first, no Python, no MCP, no OptiFine. The jar lands in `build/libs/`.

To drop it straight into a game instance while developing:

```
./gradlew build -PmodrinthModsDir="<path to your instance>/mods"
```

### Repository layout

| | |
|---|---|
| `src/main/java/lol/gzmc/vivecraftx/` | the mod: rendering, frame timing, networking, settings glue |
| `src/main/java/com/mtbs3d/minecrift/` | the inherited Vivecraft code: OpenVR provider, trackers, VR settings |
| `src/mixin/java/` | twelve mixins, which are the whole of the mod's contact with vanilla |
| `src/main/resources/assets/vivecraftx/input/` | SteamVR action manifest and default controller bindings |
| `src/main/resources/assets/vivecraftx/shaders/` | GLSL written for this version of the game, kept for the mirror and comfort work that will use it |
| `Refs/` | reference checkouts of other Vivecraft versions, for comparison while porting — not in the repository |

---

## Not here

Honest list, so nobody goes looking.

**Not possible on this version of the game.** Spyglass, shields, tridents, crossbows, maces, goat
horns and the freezing effect — none of those items or mechanics exist in 1.7.10. Dual wielding:
there is no off-hand slot. Item tags and model display transforms: no data packs, no JSON models.
HRTF audio: the OpenAL that ships with LWJGL 2 predates it.

**Possible, not done yet.** Of what other players send and show in modern Vivecraft: their
height, full body tracking, elbows and knees, legs posed from trackers, a headset drawn on their
head, and their cape and elytra following the posed body. The three mirror modes that need a
camera of their own — mixed
reality, the undistorted first-person view and the third-person one; a handheld camera and VR
screenshots; your own torso and legs in first person (you get arms); full body tracking; the live
menu world; the eye stencil, which needs a stencil buffer on the eye targets.

---

## Credits

Vivecraft and minecrift are by **StellaArtois**, **mabrowning** and **jrbudda**. The VR design,
the trackers, the OpenVR provider and most of the numbers in this mod are theirs; this is a port
of their work to a different mod system, not a new one.

JOpenVR comes from the JMonkeyVR project. JNA is by Timothy Wall and contributors.

## Licence

LGPL 3.0 or later, inherited from minecrift. See [LICENSE.md](LICENSE.md).

Minecraft itself is Copyright Mojang AB and is not included here.
