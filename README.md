# BedWars1058-Rush

The **Rush** game mode for [BedWars1058](https://github.com/andrei1058/BedWars1058), by **dev.etran**.
Beds start fortified, generators start maxed, and players can bridge with a left click. Everything is configurable and
reloadable in game.

- Kotlin 2.2.21, Gradle (wrapper included), Java 8 bytecode
- Minecraft **1.8 – 26.3** (Spigot / Paper), one jar
- Requires **BedWars1058**. Optional: PlaceholderAPI

## Install
Drop `BedWars1058-Rush-<version>.jar` into `plugins/`, start the server once, edit `plugins/BedWars1058-Rush/config.yml`,
then use `/bwrush reload`. Put your arenas in the group `rush_solo` (default) with
`/bw arenaGroup create rush_solo` and `/bw arenaGroup set <arena> rush_solo`.

## What you can configure (`config.yml`)
| Section | What it controls |
|---|---|
| `arenas` | Which arenas play Rush: **groups**, **whitelist**, **blacklist** (wildcards `*` `?`, blacklist always wins), `whitelist-only` |
| `game` | Jump-to event, starting team upgrades, permanent team/base potion effects, tutorial |
| `bed-defense` | Any number of concentric layers, each a block or team-coloured block, thickness, replace policy; **gem or box** shape |
| `bridge` | Toggle mode, length, speed, which blocks, item cost, sound, messages |
| `generators.team` / `.global` | Tune BedWars1058's own iron/gold/emerald/diamond: delay, amount, spawn limit, what drops, tier-ups |
| `generators.extra` | Your own generators: any item, amount, **sub-second delay**, spawn limit, at team spots, beds, spawns or map generators |
| `overrides` | Any of the above differently for one group or arena (arena > group > global, per value) |

## Commands (`/bwrush`, aliases `/rushmode`, `/bw-rush`)
| Command | Permission |
|---|---|
| `reload` – reload config/messages/lists and refresh running games | `bwrush.reload` |
| `status`, `list`, `check <arena>` – what is Rush and *why* | `bwrush.info` |
| `group` / `whitelist` / `blacklist` `add\|remove\|list [name]` | `bwrush.manage` |

Entries added by command are stored in `arena-lists.yml` and merged with `config.yml` (so your comments survive on every
Minecraft version). A broken `config.yml` never takes effect: the old settings stay active and the command shows the error.

## Build
```
./gradlew build            # JDK 17+ ; output: build/libs/BedWars1058-Rush-<version>.jar
```
The BedWars1058 API is resolved from `https://repo.andrei1058.dev/releases/` (`bedwarsApiVersion` in `gradle.properties`).
To use a local jar instead: drop it in `libs/` or pass `-PbedwarsApiJar=...`.

## Releasing
1. Bump `pluginVersion` in `gradle.properties` and add an entry to `CHANGELOG.md`.
2. Commit, then tag and push: `git tag -a vX.Y.Z -m "vX.Y.Z" && git push --follow-tags`.
3. The *Release* workflow builds the jar and publishes it on the GitHub Releases page.

## How 1.8 → 26.3 works from one jar
The plugin is compiled against the **1.8.8** API so nothing newer can slip in. Everything that differs between versions is
handled in `compat/`: block names are looked up by name (`WOOL:14` vs `RED_WOOL`), sounds are played by resource name instead
of the `Sound` enum, potion effects accept old and new names, and legacy-only calls are guarded by a version check.

## Developer note
This plugin is coded with AI assistance and architected completely by me. Regardless, use at your own risk.

## License
This plugin is under the MIT License, check the [License](LICENSE) file for more details.
