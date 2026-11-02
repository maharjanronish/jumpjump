# Jump Jump

A faithful, featherweight Android remake of WeChat's **跳一跳 (Jump Jump)** —
the 2017 mini-game phenomenon. Same mechanics, same feel, same clean look —
rebuilt as a **100% offline, zero-dependency, zero-permission** native
app in **~164 KB** (now with 10 hand-tuned isometric platform sprites).

```
Pure Java + OpenGL ES 2.0 · no engines · no libraries · no internet
```

## The game

Hold anywhere on the screen to charge, release to jump. The longer you hold,
the farther you fly. Land on the next block — or fall.

Every rule of the original is replicated:

| Mechanic | Behaviour |
| --- | --- |
| Normal landing | **+1** |
| Dead-center landing | **+2**, then **4, 6, 8, …** for consecutive center hits (capped at **32** after 16 in a row) |
| Manhole cover | stay ~2 s → flush sound → **+5** |
| Rubik's cube | stay ~2 s → solve ratchet sound → **+10** |
| Convenience store | stay ~2 s → door chime → **+15** |
| Record player (music box) | stay ~2 s → melody plays → **+30** |
| Direction runs | the chain runs 3-6 blocks one way, then **switches like a level switch**; the background glides to the next warm tint on every switch |
| Fixed top-view camera | the camera never rotates — it pans smoothly with you, exactly like the official composition |
| Perfect-zone marker | a small pulsing ring marks the dead-center zone on the target block |
| Block variety | 5 plain platform styles (stone, grass, wood, dark, face tile) + cylinders + 4 special kiosks — pre-rendered isometric sprites |
| Drop-in targets | the new target block drops in from above right after each landing |
| Landing particles | ivory dust bursts that bounce; **gold sparks** on dead-center hits (no big flash effects) |
| Charge feedback | the block **squashes together with the character** while charging |
| Difficulty ramp | blocks shrink and gaps widen as your score climbs |
| Round blocks | cylinders appear (and get more common) as you progress |
| Adjustment hops | tiny taps hop within the same block, exactly like the original |
| Edge landings | wobble → tip over → game over; **sideways misses** are real too (parallel-lane model) |
| Undershoot | fall into the gap (and clip the wall on the way down) |
| Overshoot | fly past and drop |
| Camera | smooth follow-pan with a lateral guard: the target block is always fully visible |
| Leaderboard | **offline only** — local top-10 with dates, best score, new-best badge |

No online anything: no accounts, no ads, no tracking, **no permissions**.

## Performance by design

- **APK: ~164 KB** (the original WeChat mini-game was ~4 MB; typical Unity clone ≥ 30 MB) — 104 KB of that is the 10 platform sprites
- **RAM: ~25–35 MB** PSS (GL context + ~400 KB of synthesized audio + ~2.6 MB of sprite textures)
- **~20 draw calls / ~40 quads per frame**, no depth buffer, 60 fps on 2015-era hardware
- Render loop pauses automatically on the game-over screen (battery friendly)
- Blocks, the character and the soft shadow are sprite quads; the piece and
  shadow textures are drawn at runtime, all audio is synthesized PCM, the
  font is rendered at runtime

## Changelog

**1.3.0** — the "official look & feel" update:

- **Pre-rendered isometric sprites**: the renderer was rewritten from 3D
  meshes to a fixed-angle top-view 2.5D sprite renderer — exactly how the
  official game presents the world. 10 platform sprites (5 plain styles,
  cylinder, manhole, Rubik's cube, shop, record player) ship in
  `assets/blocks/`, each anchored at its measured top-face center so the art
  and the landing zone coincide.
- **Direction runs instead of zigzag**: the chain keeps one direction for
  3-6 blocks, then switches (a "level switch"). Every switch also glides the
  background through a palette of light warm tints (cream, blush, butter,
  sage, peach…).
- **Fixed camera like the original**: the camera no longer rotates per
  lane — it pans smoothly with the piece, with a lateral guard that keeps
  the target block fully on screen even for the widest gaps.
- **Squash & stretch kept**: the current block still compresses together
  with the character while charging (now around the sprite's base, with the
  shadow riding along); landing settle got a tiny bounce.
- **Calmer effects**: no big flash ring on jumps, no "+1" popup on normal
  landings, no charge gauge disc — just the official-style small dust
  bursts, gold sparks and "+N" popups where they belong.
- **Score readability**: the big score sits clear of notches at 11.5% from
  the top with a soft shadow; combo streak under it.
- **Character sprite redrawn**: wider base (45% of height), ivory head at
  31%, visible collar ring — matching the official proportions.
- **Sounds kept** (synthesized, zero copyright risk); difficulty tuned a
  touch harder: slightly wider air gaps and more cylinders as you climb.

**1.2.0** — the "like the original" update:

- **Zigzag lanes**: targets now alternate up-right / up-left every jump
  (the chain used to run in one straight line); the camera swings with you.
- **Fixed the vanishing score**: text quads were emitted in one buffer but
  drawn with a single color uniform, so after the first landing the big
  score rendered white-on-white and disappeared. Each color group now gets
  its own draw call; the score also gained a soft shadow, an elegant light
  typeface and a combo streak indicator (x2, x4 …).
- **Block variety & difficulty**: tall/short box bodies, round pillars,
  faster shrink + wider air gaps, and lateral (sideways) misses are now real.
- **Only 3 blocks on screen** (current + target + falling-away) instead of a
  long staircase; the new target **drops in from above** after each landing.
- **Perfect-zone marker**: a small pulsing ring on the target block shows
  exactly where the 2/4/6… combo zone is.
- **Landing particles**: world-space dust bursts with gravity + bounce;
  gold sparks on dead-center hits.
- **Charge squash**: the block compresses together with the character.
- Font texture switched to RGBA (some drivers mishandle ALPHA_8 uploads),
  game-over card restyled (thin score font, SCORE caption).

**1.1.0** — fixed the blank/white gameplay screen some devices showed (sounds
played and the game-over card appeared, but no world rendered): every mesh
was created but never uploaded to the GPU, so all block/piece geometry drew
nothing. Meshes now upload at surface creation (with a lazy-upload guard),
the EGL config is RGB888 for maximum driver compatibility, screen-space
quads use one persistent dynamic VBO, and multi-digit score text is no longer
squished. CI now signs with the committed keystore so every build updates
in place.

**1.0.0** — initial release.

## Install

Grab `JumpJump.apk` from [Releases](../../releases) or
[Actions artifacts](../../actions) (or build below) and sideload it. It is
signed, so it installs directly — enable "install unknown apps" when Android
asks.

## Build

Three interchangeable ways:

**1. One script, no Gradle** (identical to what CI does):

```bash
ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk scripts/build_apk.sh
```

Requires any SDK with build-tools ≥ 34 and a JDK 17+. Produces a signed
`JumpJump.apk`. The committed `signing.keystore` keeps every build's
signature identical (password `jumpjump` — replace it for anything serious).

**2. Android Studio:** open the project and press Run. (Gradle + AGP 7.4.2,
no dependencies to download beyond the plugin itself.)

**3. GitHub Actions:** every push builds the APK and uploads it as an artifact;
push a tag `v*` to publish a Release.

## Project layout

```
app/src/main/assets/blocks/   10 isometric platform sprites (PNG, ~104 KB)
app/src/main/java/io/github/maharjanronish/jumpjump/
├── core/            pure Java game logic — runs on the desktop JVM too
│   ├── Game.java        state machine, scoring, specials, camera follow
│   ├── Blocks.java      level generation, direction runs + difficulty ramp
│   ├── Config.java      every tunable constant (in one place)
│   ├── Camera.java      fixed-angle 2.5D camera (shared by renderer & tests)
│   ├── Piece/Vec3/GameEvent
├── GameRenderer.java    OpenGL ES 2.0 sprite renderer
├── Sprites.java          texture loading + procedural piece/shadow art
├── ShaderUtil.java      the 3 tiny shader programs
├── FontAtlas.java       runtime bitmap font for score / +points popups
├── Sfx.java             synthesized sound effects (no audio files)
├── Storage.java         offline leaderboard (SharedPreferences)
├── GameView.java        GLSurfaceView + touch input
└── MainActivity.java    fullscreen activity + game-over / records UI
test/LogicTest.java      34 desktop JVM tests (physics, scoring, camera, levels)
test/DebugSpecial.java   special-block bonus debugging
scripts/build_apk.sh     dependency-free release build
```

## Tests

The whole core is pure Java, so the rules are verified without a device:

```bash
javac -d out $(find app/src/main/java -name "*.java" -path "*/core/*") test/LogicTest.java
java -cp out LogicTest
```

34 checks cover: combo scoring (2-4-6-8…, cap 32, reset), special-block
bonuses (one-shot, forfeit on early jump), fall detection (overshoot /
undershoot / edge-tip), in-block adjustment hops, level invariants (no
overlaps, always reachable, clean intro, all block types appear, blocks
shrink with score), direction runs (both sides occur, no per-block zigzag,
background phase advances) and fixed-camera composition (current block low,
target higher and always fully visible on a simulated 1080×2340 screen).
A "perfect bot" also survives 6,000 jumps across random level seeds.

## Tuning

Everything lives in `core/Config.java`: charge time, jump distances, block
sizes, difficulty curves, spawn probabilities, palette, camera. Change a
number, rebuild.

## Legal / credits

- Game **mechanics** are not copyrightable and are reimplemented from scratch.
- **No Tencent assets, name branding, or audio** are used; the platform
  sprites are newly generated concept illustrations supplied by the app's
  user (NOT extracted from the official game), all sounds are original
  synthesized PCM, and the character/font art is drawn at runtime.
- Research references that informed the mechanics:
  [wangshub/wechat_jump_game](https://github.com/wangshub/wechat_jump_game) (press-time↔distance linearity),
  [potato47/jump](https://github.com/potato47/jump) (landing-zone logic),
  [NightsWatchGames/jump-jump](https://github.com/NightsWatchGames/jump-jump) (scene proportions), and
  public documentation of the scoring rules.
- Code: MIT License (see LICENSE).
