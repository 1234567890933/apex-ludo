# Architecture

## Layers

```
┌──────────────────────────────────────────────────────────┐
│ MainActivity  — single activity, programmatic overlays   │
├───────────────────────────┬──────────────────────────────┤
│ Session                   │ GameView                     │
│ turn FSM, timers,         │ canvas renderer, input,      │
│ bot scheduling            │ animations, particles        │
├───────────────────────────┴──────────────────────────────┤
│ com.apexludo.core — pure Kotlin, no Android imports       │
│ Board · Rules · GameState · MoveGen · MatchEngine ·       │
│ Rng/Dice · AiEngine · Telemetry                           │
└──────────────────────────────────────────────────────────┘
```

The core never imports `android.*`. That is deliberate: it means the entire rules engine,
dice, AI and stress suite run as plain JVM unit tests in CI, in seconds, with no emulator.

## Board model

A 15×15 matrix. `Board.ring()` is a 52-entry list walked clockwise in four 13-cell
quadrants; the four corner transitions are diagonal because the path wraps around the 3×3
centre block (this is the real Ludo geometry, and `BoardTest` pins it).

Token progress is a single integer:

| progress | meaning |
| --- | --- |
| 0 | in the yard |
| 1..51 | main ring at `(start + progress - 1) % 52` |
| 52..56 | own home column |
| 57 | home (finished) |

Because progress is relative to the colour's own start, the same absolute ring index maps to
a different progress per colour. Everything that compares two tokens' physical positions goes
through `Board.ringIndexOf(color, progress)` — **using the seat index there instead of the
colour index was a real bug** (2-player matches lost captures) that the fuzz suite caught.

## State

`GameState` keeps `positions: IntArray(playerCount * 4)`, plus rank/finish bookkeeping.
`clone()` is one array copy, which is what makes on-device rollouts cheap: the Adaptive and
Grandmaster bots clone the state, play the candidate move, then run random-legal playouts to
completion within a ply budget.

`checksum()` hashes the playable state — the hook a future networked build would use for
desync detection.

## Turn loop

```
AWAIT_ROLL ──roll──► ROLLING ──dice anim done──► has moves?
     ▲                                            │        │
     │                                            │yes     │no
     │                                            ▼        ▼
     │                                        AWAIT_MOVE  pass → next turn
     │                                            │
     │                          token tapped / bot chose
     │                                            ▼
     └──────── next turn / extra roll ◄──── ANIMATING ──► match over
```

Extra turns come from a six, a capture or reaching home — each individually switchable.
A player who has just finished the race never rolls again. Three sixes forfeits the turn.

Every move is re-validated in `MatchEngine.apply()` against the live board; if the move no
longer matches a legal move (stale UI, tampered state) it is dropped and the turn continues
instead of corrupting the board.

## Rendering

One `View`, one `Canvas`. Nothing is allocated in `onDraw`:

* 225 cell rects precomputed on layout;
* paints, paths and the token gradient shaders are cached fields;
* particles live in a fixed pool (`ParticleSystem`, 720 entries + 24 shockwaves);
* token gradients are built once per colour/radius at the origin and positioned with a
  reused `Matrix` (baking the position into the shader is a classic flicker bug).

Frame pacing: `postInvalidateOnAnimation()` while animating, `postInvalidateDelayed()`
otherwise; battery saver caps at ~30 fps by delaying the next frame rather than skipping
draws (skipping draws on a hardware-accelerated view flickers).

`PerfMonitor` watches average frame time; below 55 fps for three seconds it halves particle
density (`adaptiveQuality`), and restores it above 58 fps.

## Animation

* Token movement: eased `t` across the whole path, per-segment sine arc for lift, shadow stays
  on the board while the token rises.
* Capture: victim is hidden immediately, the attacker slides, then powder is emitted along a
  bezier arc to the victim's yard slot, followed by a reform shockwave and camera shake.
* Dice: rotation + squash during the tumble, then a settle showing the real face.
* Overlays: `OvershootInterpolator` scale-in (spring feel), 240 ms.

## Audio

All samples are synthesized in-house by `tools/gen_assets.py` (numpy + wave): 12 effects and a
12-second ambient loop, 16-bit mono 22.05 kHz, ~660 KB total. `SoundPool` plays effects;
`MediaPlayer` loops the bed. Volumes are separate for SFX and music.

## Persistence

`SharedPreferences` only: settings, custom rule set, statistics, coins/streak, last bot
profile, seat count. No database, no network, no account.
