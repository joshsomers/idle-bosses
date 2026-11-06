# Idle Bosses

Train any skill and fight real bosses while you do it. Every XP drop hits a boss on screen. It takes the hits, dies and
drops loot from its real drop table. Fill collection logs to climb a ladder of 70 bosses, from Obor to the Fractured
Archive.

<p align="center">
  <img src="docs/screenshot.png" width="668" alt="Two on-screen boss boxes, for Brutus and Tempoross, beside the sidebar's Fight and Upgrades tabs">
</p>

## How it works

- **Every XP drop is a hit.** Damage is balanced for each skill, so an hour of Runecraft hits about as hard as an hour
  of Agility. Most kills take 15 seconds to 4 minutes of normal training.
- **Real bosses, real loot.** Each boss uses its game model and animations, and every kill rolls its real drop table at
  the Wiki's rates, pets included.
- **Collection logs unlock bosses.** Each boss needs log slots from across all your logs, plus a few kills of the boss
  before it. A finished log turns gold.
- **Weaknesses.** Every boss takes double damage from one skill, shown by that skill's icon, like Mining for the Giant
  Mole.
- **Upgrades.** Loot is worth coins, which buy amulets, gloves, 16 weapons with their own special effects and a dwarf
  multicannon. The best ones also need the item in your collection log.
- **Streaks and superiors.** Each kill in a row adds 1% damage, up to 25%. About 1 kill in 30 brings out a superior with
  an extra loot roll.

Hitpoints and Slayer XP don't hit, because they only come with combat XP. Progress is saved for each account.

## Getting started

1. Install **Idle Bosses** from the Plugin Hub.
2. Go skilling. Obor, Bryophyta and Brutus are open from the start, and the boss appears in a box on screen. Hold Alt
   and drag it to move it.
3. Open the **Idle Bosses** sidebar panel to pick a boss, see its collection log and spend your coins.

Each sidebar tab opens with a short guide card. The **?** by the heading brings it back.

## Settings

- **Show HUD** and **Show boss in HUD**: the on-screen box, and the animated boss inside it.
- **Walking mini boss**: off by default. A small boss follows you around the game world, only on your screen.
- **Chat messages** and **Rare drop sound**: kills, new log items and unlocks in chat, and a sound for pets and rare log
  items.
- **Reload cannon**: a key that reloads your cannon.

Every boss, unlock and upgrade is in the [full guide](docs/guide.md).

## Data

Drop tables come from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/) under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/). See `docs/drop-sources.md` for the pages and
revisions used. `tools/fetch_drops.py` rebuilds `src/main/resources/com/idlebosses/drops.json`, which stays under the
Wiki's licence. The rest of the plugin is BSD-2 (see `LICENSE`).

Boss models, animations, item icons, hitsplats and sounds are loaded from your game client while it runs. None are
included in the plugin.

## Building

```bash
./gradlew test jar
```

`./gradlew run` starts RuneLite with the plugin loaded.
