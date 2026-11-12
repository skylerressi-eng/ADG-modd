# PROMPT for Claude Code — build "RoleCall", an F7 class-role guide mod

> **How to use (Skyler):** tell Claude Code: `Read PROMPT.md and build Milestone N. Stop and show me before Milestone N+1.`

---

## 0. What you're building

A **client-side Fabric mod for Hypixel SkyBlock** that tells the player what *their* class should be doing in **Catacombs Floor 7 (F7)**, step by step, as the run progresses.

The player picks their dungeon class (or it's auto-detected), and the mod shows a HUD like:

```
RoleCall · HEALER · P1 Maxor
▶ PREDEV: mine into Goldor's arena (follow the route)
  next: set the Lights device so one flick finishes it
  then: Leap → MAGE (aqua) before lightning countdown ends
```

…plus in-world waypoints on **only that class's** terminals, devices and levers, a title + sound when the step changes, and a highlight on the right player in the Spirit Leap menu.

Working name: **RoleCall**, mod id `rolecall`, command `/rolecall`. Fine to rename.

The default role data comes from a written community F7 guide (sources in §11), cross-checked against the ADG "F7 Guide for All Classes" video's structure (Archer 0:00, Berserker 2:15, Mage 3:58, Healer 7:11, Tank 9:19). The video transcript wasn't readable, so **all role text lives in a JSON data file** that Skyler can edit to match ADG's callouts exactly. Never hard-code role text in Java.

---

## 1. Hard rules (Hypixel compliance) — do not break these

Hypixel's Allowed Modifications page bans anything that automates gameplay, changes how the client talks to the server, or gives a significant advantage, and says anything not clearly in an allowed category should be assumed disallowed. Popular dungeon mods (Skyblocker, Odin) ship static-coordinate waypoints and terminal HUDs, but it's still **use at your own risk**. So this mod must be **display-only**:

- ❌ No auto-clicking, auto-leap, auto-terminal, auto-mining, auto-movement, aim help.
- ❌ **No automatic chat or party messages** of any kind (no "positional messages" that send by themselves).
- ❌ No packet changes, no mixins that alter outgoing packets or interaction.
- ❌ No solvers (terminals/Simon Says/arrows) — out of scope; other mods do that.
- ❌ No ESP / seeing through walls for entities. Waypoints are fixed coordinates only.
- ✅ Allowed: reading chat, tab list, scoreboard and the player's own position; drawing HUD text, world boxes/labels at fixed coordinates; playing local sounds; rendering a highlight in a GUI the player opened (render only — never click for them).
- Add a short "Use at your own risk / display-only" note to README.md and the mod description.

---

## 2. Tech stack

1. **Minecraft version:** copy CollectionClimb's Minecraft/Loom/Fabric API/Java versions and language exactly (resolved in M1: MC 26.1.2, Loader 0.19.3, Loom 1.17.13, Fabric API 0.154.0+26.1.2, Java 25, plain Java). Don't guess version numbers.
2. **Mappings:** 26.x ships unobfuscated, so use Mojang names (`net.minecraft.client.Minecraft`, `Component`, `Identifier`, …). Run `./gradlew genSources` to read real signatures instead of guessing APIs.
3. **Scaffold**: client-only mod (`environment: client`).
4. **Dependencies (keep it lean):**
   - Fabric API.
   - **Hypixel Mod API** (`hypixel-mod-api` on Modrinth) for location events: server type / mode. Declare it in `fabric.mod.json` `depends`. (Release `1.0.2+build.1+mc26.1` covers 26.1–26.2.)
   - Config: plain JSON with Gson (already in MC). Optional Mod Menu integration only if it's trivial.
5. Java toolchain: whatever the template says for that version.
6. Terminal-first: everything must work with `./gradlew build` and `./gradlew runClient` on Debian. Print the jar path at the end of the build.

---

## 3. Feature spec

### 3.1 Class selection
- `/rolecall` opens a **picker screen**: five big buttons (Archer, Berserk, Mage, Healer, Tank) in class colors, plus:
  - **Auto** (default): detect from the tab list (§4.4). A manual pick always overrides Auto until reset.
  - **Predev toggle** (default ON when the party has a Healer): if OFF, Healer predev steps are hidden and Archer's "wait at SS for the Healer leap" steps change to "go to levers".
  - **S4 split toggle**: "Berserk = Sharpshooter, Healer = Levers" vs. the reverse (the guide says these two agree on it each run).
- `/rolecall class <archer|berserk|mage|healer|tank|auto>` sets it without the GUI.
- Class colors (match Hypixel/Odin): Archer `§6` gold, Berserk `§4` dark red, Healer `§d` light purple, Mage `§b` aqua, Tank `§2` dark green.

### 3.2 HUD
- A draggable HUD element (`/rolecall hud` opens the move/scale editor). Shows:
  - Header: `RoleCall · <CLASS> · <phase label>` with P3 progress such as `P3 S2 (5/8)`.
  - **Current step** (big), **next step** (small, grey).
  - If the step has a leap target: `Leap → MAGE`, colored, with the teammate's IGN when known from the tab list.
  - **Party gaps**: if a class is missing from the party, list that class's P3 jobs for that section as `Unassigned: S1 Simon Says device`. If a class is duplicated, show `2× Mage: split T2 with your duplicate`.
- Hidden outside F7. Hidden during clear unless the clear steps have content (§5.1).

### 3.3 Step-change cues
- On each step change: on-screen title (short form of the step) + a local "ding" sound. Both toggleable, plus volume.
- Storm giga-lightning warning: big title `LIGHTNING — get under your pillar` (§4.3 regex).

### 3.4 World waypoints
- Draw a box + label + distance **only on the current section's objectives assigned to the player's class** (other objectives optional and dimmed, off by default).
- When a terminal/device/lever completes, hide the waypoint nearest to the player who completed it (the same approach Skyblocker uses: find the named player's position, hide the nearest still-visible waypoint of that kind).
- Coordinates come from `objectives.json` (§5.2). Also support **user waypoints** (predev route, standing spots like "SS wait spot") recorded in game:
  - `/rolecall here <id> [label…]` saves the player's current block position under that id to the user override file.
  - `/rolecall route <name> add|clear|list` builds an ordered route (used for the predev mining route). Render the route as numbered points with a line to the next one. Display only.

### 3.5 Spirit Leap menu highlight
- When the open container title is `Spirit Leap` or `Teleport to Player`, and the current step has a leap target, draw a colored border/glow around the matching player's head slot. **Render only — never click.**

### 3.6 Commands summary
`/rolecall` · `/rolecall class …` · `/rolecall hud` · `/rolecall reload` (reload JSON) · `/rolecall here …` · `/rolecall route …` · `/rolecall debug` (toggle an overlay with detected island/floor/inBoss/phase/section/progress/class/party) · `/rolecall sim <script>` (§7).

---

## 4. Detection (state machine)

Build one `RunState` object updated from events, reset on world change/disconnect. All matching uses `Component.getString()` (formatting stripped), anchored regexes, overlay/action-bar messages ignored. Use the Fabric client chat receive event (Skyblocker uses `ClientReceiveMessageEvents.ALLOW_GAME`; check what exists in the chosen version).

### 4.1 Where am I
- **On SkyBlock / in a dungeon:** Hypixel Mod API location event (server type SkyBlock, mode for dungeons — log the raw values in `/rolecall debug` and confirm; it's probably `dungeon`).
- **Floor:** sidebar scoreboard line matching `The Catacombs \((\w+)\)$` → `F7` or `M7`. v1 supports F7; M7 runs reuse F7 data for P1–P4 and show "P5 not supported yet".
- **In boss (F7/M7):** player `x > -7 && z > -7` (Odin's check), or any `[BOSS] Maxor:` line.

### 4.2 Phase triggers (exact strings, from Odin/Skyblocker sources)
| Event | Chat line (regex) |
|---|---|
| P1 Maxor start | `^\[BOSS\] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$` |
| P2 Storm start | `^\[BOSS\] Storm: Pathetic Maxor, just like expected\.$` |
| P2 end / terminals start | `^\[BOSS\] Storm: I should have known that I stood no chance\.$` |
| P3 Goldor start | `^\[BOSS\] Goldor: Who dares trespass into my domain\?$` |
| Core open (P3 done, Goldor tunnel) | `^The Core entrance is opening!$` |
| P4 Necron start | `^\[BOSS\] Necron: You went further than any human before, congratulations\.$` |
| Necron dead (M7 → P5) | `^\[BOSS\] Necron: All this, for nothing\.\.\.$` |
| M7 Wither King | `^\[BOSS\] Wither King: You\.\.\. again\?$` |

Fallback when a message is missed (joined mid-run, relog): Odin's Y-bands in the F7 boss: `y > 210` P1, `> 155` P2, `> 100` P3, `> 45` P4, else P5.

### 4.3 In-phase triggers
- Storm lightning: `^\[BOSS\] Storm: (ENERGY HEED MY CALL|THUNDER LET ME BE YOUR CATALYST)!$`
- Terminal: `^(\w{1,16}) activated a terminal! \((\d)/(\d)\)$`
- Device: `^(\w{1,16}) completed a device! \((\d)/(\d)\)$`
- Lever: `^(\w{1,16}) activated a lever! \((\d)/(\d)\)$`
- **Section complete:** any of those three with `(7/7)` or `(8/8)`, then section++ (S1→S2→S3→S4). Section sizes: S1 = 4 terminals + device + 2 levers (7), S2 = 5 + 1 + 2 (8), S3 = 7, S4 = 7.
- Gate: `^The gate has been destroyed!$` (set `gateDestroyed`; a step can wait on this).
- Keep a per-section progress counter `(n/total)` from the latest match for the HUD.

### 4.4 Class detection (Auto)
Every ~20 ticks while in a dungeon, read the tab list display names and match Odin's regex:
`^\[(\d+)] (?:\[\w+] )*(\w+) .*?\((\w+)(?: (\w+))*\)$` → groups: SkyBlock level, IGN, class (or `DEAD`), class level (roman).
- Your own IGN → your class (unless manually overridden).
- Build the party map `class → [IGN]` for leap targets and party gaps.
- Log unmatched lines in debug mode so the regex can be fixed if Hypixel changes formatting.

---

## 5. Data files (the heart of the mod)

Ship defaults in `src/main/resources/assets/rolecall/data/`. On load, deep-merge a user override from `config/rolecall/` so edits survive updates. `/rolecall reload` re-reads both. Validate on load and print clear errors (bad JSON shouldn't crash the client).

### 5.1 `roles.json`: steps per class
Schema (adjust if you find something cleaner, but keep it data-driven):
```json
{
  "version": 1,
  "floor": "F7",
  "classes": {
    "HEALER": [
      { "id": "p1_predev_mine", "phase": "P1", "text": "PREDEV: mine into Goldor's arena (follow route)",
        "route": "predev", "until": { "phase": "P2" }, "requires": { "predev": true } },
      { "id": "p3s1_ss", "phase": "P3", "section": 1, "text": "Do Simon Says device",
        "waypoints": ["s1_device"] }
    ]
  }
}
```
- `phase`: CLEAR, P1, P2, P3, P3_TUNNEL (after Core opens), P4, P5.
- `section`: 1–4 for P3.
- `until`: what advances to the next step: `{phase}`, `{section}`, `{event: "lightning"|"gate"|"core"}`, `{objectiveDone: "<id>"}`, or a manual keybind ("next step", default unbound; display-only, it only changes the HUD).
- `leap`: target class (`"MAGE"`), shown as `Leap → MAGE`.
- `requires`: `{predev: true/false}`, `{s4split: "bers_device"|"bers_levers"}`.
- `note`: optional grey sub-line.

**Default content.** Paraphrased from the Sep 2025 "Floor 7 Guide (Advanced)" on hypixel.net. Mark each step in a `"src"` field. Things to re-check against the ADG video are flagged ⚠.

**CLEAR (all classes):** leave empty by default with a TODO comment. ⚠ Skyler fills this in from the ADG video chapters. The HUD shows nothing during clear while it's empty.

**P1 — Maxor**
- Mage: grab and place the **left** crystal.
- Berserk: grab and place the **right** crystal.
- Tank: stand under Maxor to hold aggro (F7 aggro works by proximity, not hits). Lead him under the laser and hit him while it charges.
- Healer (predev ON): **predev**: Dungeonbreaker into Goldor's arena during Maxor. On pace = near or at the Lights (S2) device when Maxor dies. Steps:
  1. Mine in (follow the `predev` route if recorded).
  2. S2 **Lights device**: light every lamp ahead of time so one lever flick during Goldor finishes it.
  3. S3 **Arrow Align device**: align every arrow except one, which stays one click from correct.
- Healer (predev OFF): stay with the party and keep them alive.
- Archer: go to **Simon Says (S1 device)** and wait there for the Healer to leap to you. ⚠ The guide's wording is unclear here; check against the video at 0:00.

**P2 — Storm**
- Mage: kill Withermancers along the middle and the green/yellow pillars. On lightning, get under the **green** pillar. Then aggro Storm from the ledge facing him and lead him under the green pillar (Healer drops it). At half HP, lead him under the **yellow** pillar (Tank drops it). Kill him → **Leap → HEALER**.
- Berserk: kill Withermancers on the purple/red pillar side → **Leap → ARCHER**. Be down in P3 before the lightning; cloak.
- Tank: go to the yellow pad and clear mobs (help Mage/Berserk). Stay near the yellow pad for lightning, then drop the **yellow** pillar when Mage brings Storm under it → **Leap → HEALER**.
- Healer: finish predev → **Leap → MAGE**. Drop the **green** pillar when Storm is about halfway across. Once Storm is crushed → **Leap → ARCHER** (Archer is at SS). If predev isn't done in time, skip it and leap to Mage. The latest leap is when the lightning countdown ends.
- Archer: wait at SS. When the Healer leaps in, go to the S1 levers and wait for Goldor.

**P3 — Goldor terminals** (T# = terminal number in that section)
| | S1 (4T + SS + 2 levers) | S2 (5T + Lights + 2 levers) | S3 (4T + Arrows + 2 levers) | S4 (4T + Sharpshooter + 2 levers) |
|---|---|---|---|---|
| **Mage** | T4 | T2 | T2 | T2 |
| **Berserk** | T3 | T4, then T3 | T3 | T3, then Sharpshooter **or** levers (split with Healer) |
| **Tank** | T2, then T1 | T1, then Leap → BERSERK and help with T3 | T1 | T1 |
| **Healer** | Simon Says device | finish Lights device, left lever, blow gate | finish Arrow Align, then levers → Leap → MAGE or ARCHER | Sharpshooter **or** levers (split with Berserk) ⚠ guide also lists "3rd terminal"; treat as a typo since Berserk owns T3 |
| **Archer** | both levers, blow gate, stand at the gate so others can leap to you | T5, right lever | T4, blow gate, help with levers | T4, levers |

- P3 tunnel (after "The Core entrance is opening!"): **all**: don't melee Goldor (instant death). Mage: left-click mage damage. Archer: shoot. Tank: stay close to them.
- **P4 Necron:** all: DPS Necron.
- **P5 (M7):** stub. "Not supported yet." Milestone 5.

⚠ "Left/right lever" depends on which way you're facing. Store levers by coordinate in `objectives.json` with a `side` label, default the guide's assignment, and verify in game with `/rolecall debug` (shows the nearest objective id).

### 5.2 `objectives.json`: fixed boss coordinates
Floor 7 boss coordinates are fixed. These match Skyblocker's `goldorwaypoints.json`; Odin agrees on the SS button `(110,121,91)` and Arrow Align centre `(0,120,77)`. Treat them as facts and write your own file; don't copy Skyblocker code (LGPL-3.0).

```
S1  device  (110,121,91)  Simon Says start button
S1  T1 (111,113,73)  T2 (111,119,79)  T3 (89,112,92)  T4 (89,122,101)
S1  levers  (106,124,113)  (94,124,113)
S2  device  (60,132,143)  Lights
S2  T1 (68,109,121)  T2 (59,120,122)  T3 (47,109,121)  T4 (40,124,122)  T5 (39,108,143)
S2  levers  (27,124,127)  (23,132,138)
S3  device  (0,120,77)  Arrow Align
S3  T1 (-3,109,112)  T2 (-3,119,93)  T3 (19,123,93)  T4 (-3,109,77)
S3  levers  (14,122,55)  (2,122,55)
S4  device  (63,127,35)  Sharpshooter (stand on the gold plate)
S4  T1 (41,109,29)  T2 (44,121,29)  T3 (67,109,29)  T4 (72,115,48)
S4  levers  (86,128,46)  (84,121,34)
```
Give each an id like `s2_t5`, `s2_lever_a`, `s2_device`. ⚠ Check that Skyblocker's "Terminal #N" order matches the community "Nth terminal" numbering the role table uses. If it doesn't, fix the mapping in data, not code.

Leave `routes` (predev) and custom spots empty by default. Skyler records them with `/rolecall here` and `/rolecall route` (§3.4).

---

## 6. Code layout (suggested)

```
rolecall/
  RoleCallClient.java        // init, register events/commands/HUD
  state/RunState.java        // island, floor, inBoss, phase, section, progress, flags
  state/ChatTriggers.java    // all regexes from §4 in one place
  state/PartyTracker.java    // tab list → class map
  data/RoleData.java         // load + merge + validate roles.json / objectives.json
  data/StepEngine.java       // picks current/next step from RunState + class + toggles
  ui/ClassPickerScreen.java
  ui/RoleHud.java
  ui/LeapHighlight.java      // container render hook, render only
  render/WaypointRenderer.java
  cmd/RoleCallCommands.java
  config/RoleCallConfig.java
```
Keep `RunState` + `StepEngine` free of Minecraft classes so they can be unit-tested with plain JUnit.

---

## 7. Testing

1. **Unit tests** (JUnit, no Minecraft): feed recorded chat lines into `ChatTriggers` → `RunState` → `StepEngine` and assert the current step per class. Include fixtures for a full F7 boss: P1 → P2 → lightning → P3 S1..S4 with progress lines, gate, core → P4. Also test: missed message plus Y-fallback, relog mid-P3, party with no Healer (predev auto-off, gaps shown), duplicate Mage.
2. **`/rolecall sim <script>`:** plays a fixture script **locally** by injecting lines into the mod's own handler (never into the server, never sent as chat) so the HUD/titles can be checked in singleplayer. Also allow faking position and class.
3. `./gradlew build` must pass with tests. Then `./gradlew runClient` and screenshot-check the HUD and picker.

---

## 8. Milestones (stop after each and summarize)

1. **M1:** scaffold, config, `/rolecall` picker, class colors, static HUD showing the picked class and a dummy step. Builds and runs.
2. **M2:** `RunState` + `ChatTriggers` + `PartyTracker` + `StepEngine` loading `roles.json` defaults; unit tests; `/rolecall debug`; `/rolecall sim`.
3. **M3:** `objectives.json`, world waypoints for own objectives, completion hiding, `/rolecall here` and routes.
4. **M4:** titles/sounds, lightning warning, party gaps, Spirit Leap highlight, HUD editor, README with install steps and the display-only/use-at-your-own-risk note.
5. **M5 (stretch):** clear-phase tips once Skyler fills them in; M7 P5 (relics/dragons). Research M7 roles fresh before writing any data.

---

## 9. Verify in game (checklist for Skyler; print it in README)
- [ ] Mod API location values while in Catacombs (debug overlay).
- [ ] Tab-list regex picks up all 5 classes and your own IGN.
- [ ] Boss messages still match (Hypixel sometimes changes text).
- [ ] Terminal numbering matches community numbering per section.
- [ ] Which S2 lever is "left" vs "right" from the usual approach.
- [ ] Waypoints sit on the right blocks (1-block offsets happen).
- [ ] Spirit Leap menu title still `Spirit Leap`.
- [ ] Role text matches the ADG video; edit `config/rolecall/roles.json` where it differs.

---

## 10. Style
- Small classes, clear names, no reflection hacks. Mixins only if Fabric API has no event for something (and never on networking).
- All player-facing strings for roles come from JSON. UI strings go in a lang file.
- No telemetry, no network calls besides the Hypixel Mod API.

## 11. Reference sources (read, don't copy code)
- Odin (BSD-3-Clause): `git clone --depth 1 https://github.com/odtheking/Odin`. Look at `utils/skyblock/dungeon/DungeonUtils.kt` (F7 phase Y-bands, tab regex), `DungeonListener.kt` (floor regex, in-boss check), `features/impl/boss/*` (P3 chat regexes, SS/arrows coords), `features/impl/dungeon/LeapMenu.kt` (leap GUI titles).
- Skyblocker (LGPL-3.0): `git clone --depth 1 https://github.com/SkyblockerMod/Skyblocker`. Look at `skyblock/dungeon/GoldorWaypointsManager.java` (P3 state machine), `assets/skyblocker/dungeons/goldorwaypoints.json` (coordinates), and their world-render helpers for the right 26.x rendering API.
- Hypixel Mod API: https://github.com/HypixelDev/ModAPI
- Role data: "Floor 7 Guide (Advanced)", hypixel.net thread 5984063 (Sep 2025); Hypixel Wiki "Terminal" page (device names per gate); ADG "F7 Guide for All Classes" (YouTube `SPjSzYFbeB8`, Nov 2026).
- Rules: Hypixel Support → "Allowed Modifications".
