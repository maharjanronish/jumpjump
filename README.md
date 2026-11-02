# Jump Jump

A faithful, featherweight Android remake of WeChat's **跳一跳 (Jump Jump)** —
the 2017 mini-game phenomenon. Same mechanics, same feel, same clean pastel
look — rebuilt as a **100% offline, zero-dependency, zero-permission** native
app in **~64 KB**.

```
Pure Java + OpenGL ES 2.0 · no engines · no libraries · no assets · no internet
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
| Rubik's cube | stay ~2 s → it rotates to solve itself → **+10** |
| Convenience store | stay ~2 s → door slides open → **+15** |
| Music box (vinyl) | stay ~2 s → melody plays, record spins → **+30** |
| Clock block | decorative: shows your real local time |
| Difficulty ramp | blocks shrink and gaps widen as your score climbs |
| Round blocks | cylinders appear (and get more common) as you progress |
| Adjustment hops | tiny taps hop within the same block, exactly like the original |
| Edge landings | wobble → tip over → game over |
| Undershoot | fall into the gap (and clip the wall on the way down) |
| Overshoot | fly past and drop |
| Camera | smooth follow; pans with your jump, subtle zoom-out on far targets |
| Leaderboard | **offline only** — local top-10 with dates, best score, new-best badge |

No online anything: no accounts, no ads, no tracking, **no permissions**.

## Performance by design

- **APK: ~64 KB** (the original WeChat mini-game was ~4 MB; typical Unity clone ≥ 30 MB)
- **RAM: ~25–35 MB** PSS (GL context + ~400 KB of synthesized audio)
- **~25 draw calls / < 10k triangles per frame**, RGB565 framebuffer, 60 fps on 2015-era hardware
- Render loop pauses automatically on the game-over screen (battery friendly)
- All geometry is procedural (cube/cylinder/sphere), all audio is synthesized
  PCM, the font is rendered at runtime — **zero asset files**

## Install

Grab `JumpJump.apk` from [Actions artifacts](../../actions) (or build below) and
sideload it. It is signed, so it installs directly — enable "install unknown
apps" when Android asks.

## Build

Three interchangeable ways:

**1. One script, no Gradle** (identical to what CI does):

```bash
ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk scripts/build_apk.sh
```

Requires any SDK with build-tools ≥ 34 and a JDK 17+. Produces a signed
`JumpJump.apk`. A signing keystore is generated on first run (password
`jumpjump` — replace it for anything serious).

**2. Android Studio:** open the project and press Run. (Gradle + AGP 7.4.2,
no dependencies to download beyond the plugin itself.)

**3. GitHub Actions:** every push builds the APK and uploads it as an artifact;
push a tag `v*` to publish a Release.

## Project layout

```
app/src/main/java/io/github/maharjanronish/jumpjump/
├── core/            pure Java game logic — runs on the desktop JVM too
│   ├── Game.java        state machine, scoring, specials, camera follow
│   ├── Blocks.java      level generation + difficulty ramp
│   ├── Config.java      every tunable constant (in one place)
│   ├── Camera.java      camera rig shared by renderer & tests
│   ├── Piece/Vec3/Mat4/GameEvent
├── GameRenderer.java    OpenGL ES 2.0 renderer
├── Meshes.java          procedural geometry
├── ShaderUtil.java      the 4 tiny shader programs
├── FontAtlas.java       runtime bitmap font for score / +points popups
├── Sfx.java             synthesized sound effects (no audio files)
├── Storage.java         offline leaderboard (SharedPreferences)
├── GameView.java        GLSurfaceView + touch input
└── MainActivity.java    fullscreen activity + game-over / records UI
test/LogicTest.java      27 desktop JVM tests (physics, scoring, camera, levels)
scripts/build_apk.sh     dependency-free release build
```

## Tests

The whole core is pure Java, so the rules are verified without a device:

```bash
javac -d out $(find app/src/main/java -name "*.java" -path "*/core/*") test/LogicTest.java
java -cp out LogicTest
```

27 checks cover: combo scoring (2-4-6-8…, cap 32, reset), special-block
bonuses (one-shot, forfeit on early jump), fall detection (overshoot /
undershoot / edge-tip), in-block adjustment hops, level invariants (no
overlaps, always reachable, clean intro, all block types appear, blocks
shrink with score), and camera composition on a simulated phone screen.
A "perfect bot" also survives 6,000 jumps across random level seeds.

## Tuning

Everything lives in `core/Config.java`: charge time, jump distances, block
sizes, difficulty curves, spawn probabilities, palette, camera. Change a
number, rebuild.

## Legal / credits

- Game **mechanics** are not copyrightable and are reimplemented from scratch.
- **No Tencent assets, name branding, or audio** are used; all art, colors,
  sounds and code here are original recreations in the style of the original.
- Research references that informed the mechanics:
  [wangshub/wechat_jump_game](https://github.com/wangshub/wechat_jump_game) (press-time↔distance linearity),
  [potato47/jump](https://github.com/potato47/jump) (landing-zone logic),
  [NightsWatchGames/jump-jump](https://github.com/NightsWatchGames/jump-jump) (3D scene proportions), and
  public documentation of the scoring rules.
- Code: MIT License (see LICENSE).
