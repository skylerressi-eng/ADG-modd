# RoleCall

A client-side Fabric mod for Hypixel SkyBlock that shows what **your** dungeon class should be doing in
Catacombs Floor 7, step by step, as the run progresses.

> **Display-only. Use at your own risk.**
> RoleCall only reads chat, the tab list, the scoreboard and your own position, and draws HUD text,
> fixed-coordinate waypoints and GUI highlights. It never clicks, moves, aims, leaps, mines or sends chat or
> party messages for you, and it does not touch packets. Hypixel's Allowed Modifications rules still apply,
> and any mod can get you banned. You use it at your own risk.

## Status

| Milestone | What | State |
|---|---|---|
| M1 | Scaffold, config, `/rolecall` class picker, class colors, static HUD + editor | ✅ done |
| M2 | Run state machine, chat triggers, party tracker, step engine, `roles.json`, `/rolecall debug`, `/rolecall sim` | next |
| M3 | `objectives.json` waypoints, completion hiding, `/rolecall here`, routes | |
| M4 | Titles/sounds, lightning warning, party gaps, Spirit Leap highlight, final README | |
| M5 | Clear-phase tips, M7 P5 | stretch |

## Versions

The versions are copied from CollectionClimb: Minecraft **26.1.2**, Fabric Loader 0.19.3, Fabric API
0.154.0+26.1.2, Loom 1.17.13, Java **25**. From M2 on, the Hypixel Mod API `1.0.2+build.1+mc26.1` (Modrinth
`hypixel-mod-api`) will also be needed. It supports 26.1–26.2.

## Build and run (Debian)

```bash
sudo apt install openjdk-25-jdk-headless
```

```bash
./gradlew build
```

The jar path is printed at the end of the build: `build/libs/rolecall-<version>.jar`.

```bash
./gradlew runClient
```

If your default `java` isn't 25, set `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64` first.

## Install

1. Install Fabric Loader for Minecraft 26.1.2.
2. Put `rolecall-<version>.jar` and Fabric API into `.minecraft/mods/`.

## Commands (M1)

| Command | What it does |
|---|---|
| `/rolecall` | Opens the class picker (5 classes + Auto, Healer predev toggle, S4 split toggle) |
| `/rolecall class <archer\|berserk\|mage\|healer\|tank\|auto>` | Sets your class without the GUI |
| `/rolecall hud` | HUD editor: drag to move, scroll to scale, ESC to save |

Settings are saved to `config/rolecall/config.json`. User overrides for the role and objective data will
also live in `config/rolecall/` (M2/M3).

## Verify in game (checklist)

- [ ] Mod API location values while in Catacombs (debug overlay)
- [ ] Tab-list regex picks up all 5 classes and your own IGN
- [ ] Boss messages still match (Hypixel sometimes changes text)
- [ ] Terminal numbering matches community numbering per section
- [ ] Which S2 lever is "left" vs "right" from the usual approach
- [ ] Waypoints sit on the right blocks (1-block offsets happen)
- [ ] Spirit Leap menu title still `Spirit Leap`
- [ ] Role text matches the ADG video; edit `config/rolecall/roles.json` where it differs
