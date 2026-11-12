# Changelog

## 1.2 (beta)

New
- Coin shop: unlock themes, board skins and token styles with coins earned in play.
- Profile studio: 6 avatars and 6 titles, equipped from the home screen.
- Emote wheel during a match (4 quick-chats) plus occasional bot reactions.
- Spectator mode: watch four different bots fight it out from the front row.
- Streak shields: one shield protects a day streak that would otherwise break.
- Two HD music beds (menu + in-match) that switch with the screen.
- Four procedural board textures (walnut, marble, neon grid, paper).
- A complete hand-drawn icon set (23 glyphs) replacing text-only buttons.

Changed
- Home screen rebuilt as an icon-card grid with a profile strip.
- Release APK is ~6.8 MB (was 825 KB) because of the real music and textures.


## 1.1 (beta) — first public build

### Engine
- Deterministic xorshift64* PRNG, seeded per match; identical results on every device.
- Pure-Kotlin rules core with zero Android imports (unit + fuzz testable on the JVM).
- Turn FSM: TurnStart → DiceRoll → MovementResolution → Vfx → VictoryEvaluation.
- 12 configurable rules, 4 presets, exact-roll home, blockades, battle mode.
- Move re-validation on apply: a stale or tampered move is dropped instead of applied.
- Per-state checksum for desync detection.

### AI (on-device, offline)
- Three cooperating agents: board analyst, strategic planner, probability agent.
- Monte-Carlo rollout evaluator with a hard ply/rollout budget for phone CPUs.
- Six personalities including Adaptive and Grandmaster.

### Presentation
- Hand-drawn canvas board: 6 themes, 4 board skins, 4 token silhouettes.
- "Powder Reincarnation" capture: bezier particle arc, reform shockwave, camera shake.
- Animated dice with tumble, squash and settle; path-preview dots for legal moves.
- 12 synthesized audio effects + ambient loop, separate SFX/music buses.
- Contextual haptics, spring-eased overlays, confetti victory burst.

### Quality
- 10,000-cycle fuzz suite, 200-match soak, 60+ assertions across 4 test classes.
- FPS governor with adaptive particle shedding; battery saver caps at 30 fps.
- Crash logger writes to app storage; the About screen surfaces the latest report.

### App
- Single activity, programmatic overlays (no XML layouts to inflate and fail).
- English + Hindi, daily streak rewards, statistics, match log, AFK auto-pilot.

### Fixed during development
- Mixed-stack capture: two different opponents on one cell were treated as a blockade and
  left two players illegally sharing a square.
- Seat-index vs colour-index bug: captures silently stopped working in 2-player matches.
- Finished players no longer receive an extra roll.
- Release builds no longer risk losing audio samples to resource shrinking.
- Theme picker silently dropped half of its buttons (a reused row view was re-parented).
