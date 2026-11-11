# Feature status — Apex Ludo 1.1 (beta)

Every item from the specification, with its honest status in this build.
Legend: **Done** = shipped and tested · **Partial** = shipped in a reduced form ·
**Planned** = not in 1.1 (see note).

| # | Feature | Status | Notes |
| --- | --- | --- | --- |
| 1 | Dynamic lighting / board themes | Done | 6 themes: Daylight, Midnight, Cyberpunk, Royal Gold, Emerald, High Contrast |
| 2 | Contextual haptic manager | Done | 12–90 ms impulses for roll, land, move, capture, home, win |
| 3 | Multi-layer audio mixing | Done | Separate SFX and music buses with independent volume; 12 synthesized effects |
| 4 | Unlockable token skins | Done | Pawn, Dragon, Racer, Gem — all free during beta |
| 5 | Session telemetry / replay recorder | Done | Every action recorded, human-readable match log, export/import wire format |
| 6 | Network resilience layer | Planned | No networking in 1.1 (offline by design) |
| 7 | Authoritative anti-cheat validation | Partial | Single-device: moves are re-validated against the live board, state checksums recorded. No server in 1.1 |
| 8 | Quick-chat / emote wheel | Planned | Needs multiplayer to mean anything |
| 9 | Cinematic camera framing | Partial | Capture camera shake, confetti burst, screen-level emphasis; no dynamic camera cuts |
| 10 | Localisation framework | Done | English + Hindi, in-app switcher, RTL-ready resources |
| 11 | Battery saver mode | Done | Hard 30 fps cap, reduced particle load |
| 12 | Cloud profile sync | Planned | Deliberately excluded — app has no network permission |
| 13 | Daily streak & rewards | Done | Local streak counter, escalating coin reward |
| 14 | Custom ruleset creator | Done | 12 toggles + timer, saved locally, used by Quick Match |
| 15 | Spectator mode | Planned | Requires multiplayer |
| 16 | Animated onboarding tutorial | Done | 4-step walkthrough from the home screen |
| 17 | Performance profiler dashboard | Done | FPS / frame-time / live particle count overlay |
| 18 | Responsive layout scaling | Done | All geometry derived from view size; safe for 16:9 → 21:9 |
| 19 | Particle density slider | Done | Low / Medium / High, plus automatic adaptive shedding |
| 20 | Encrypted asset bundles | Planned | All assets ship inside the APK (825 KB, so there is nothing to stream) |
| 21 | Bug trace & crash logger | Done | Uncaught exceptions written to app storage, latest shown in About |
| 22 | Rapid matchmaking queue | Planned | Seat setup screen instead; no online queue |
| 23 | High-contrast accessibility palette | Done | Dedicated "High Contrast" theme + switch |
| 24 | In-game event ticker | Done | Rotating tips in the bottom bar |
| 25 | Pathfinding arc visualiser | Done | Dotted preview of the landing path for each selectable token |
| 26 | Consumable streak protection items | Planned | Coins exist; no shop or consumables in beta |
| 27 | Dynamic turn timers with audio cues | Done | Configurable 0–60 s, tick sounds in the last 5 s, auto-play on expiry |
| 28 | Avatar & title customisation | Planned | — |
| 29 | Background music continuity | Done | Single looping bed, pauses/resumes with the lifecycle |
| 30 | CI/CD GitHub Actions pipeline | Done | Tests → signed release APK + debug APK → artifacts |
| 31 | Offline AI model selector | Done | Six profiles, all running locally; no downloads needed |
| 32 | AFK match delegation | Done | Per-seat auto-pilot toggle, resumable at any time |
| 33 | Bot personality profiles | Done | Balanced, Tactical, Aggressive, Defensive, Adaptive, Grandmaster |
| 34 | Custom board skin creator | Partial | 4 board skins + 6 themes selectable; no free-form creator |
| 35 | Automated unit & stress test runner | Done | 4 test classes, 10,000-cycle fuzz, 200-match soak, runs in CI |

## Why some items are "Planned"

Apex Ludo 1.1 is an offline single-device beta with **no network permission**. Anything that
only makes sense with a server (multiplayer, matchmaking, spectator, cloud sync, downloadable
bundles, consumables shop) is deferred rather than shipped half-working. Adding them later
does not require re-architecting the core: `MatchEngine` is deterministic and seeded, so a
host-authoritative session can replay and validate the same move stream.
