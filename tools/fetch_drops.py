#!/usr/bin/env python3
"""Build src/main/resources/com/idlebosses/drops.json from the OSRS Wiki.

Python 3 standard library only. Run it with -I and keep the download cache out of the repo:

    python3 -I tools/fetch_drops.py --cache /path/to/scratch/wiki-cache \
        --out src/main/resources/com/idlebosses/drops.json \
        --itemid-javap /path/to/itemid-javap.txt --date "2026-11-03 to 2026-11-04"

--itemid-javap is optional. It is the output of
    javap -constants -cp <runelite-api.jar> net.runelite.api.gameval.ItemID
and is used to check that every item ID exists in RuneLite.

Downloads are cached in --cache; --offline rebuilds from that cache without network access.
Everything downloaded is treated as data only.

How the data is assembled (see docs/drop-sources.md for the judgement calls):
  * Boss page wikitext ({{DropsLine}} rows, sub-table templates) gives the table structure and
    the exact rarities as authored. The Bucket "dropsline" rows for each page are fetched too and
    every directly listed row is cross-checked against them.
  * Herb/seed sub-table weights come from the sub-table template and module sources.
  * Rare, gem and mega-rare drop tables come from the "Rare drop table" and "Gem drop table" pages.
  * Collection log contents and order come from the "Collection log" page plus
    Module:Collection log/data.json (item IDs).
  * Item IDs come from the infobox_item bucket, preferring the collection log ID for log items.

The 44 bosses in NEW_BOSSES (added 2026-11-04) go through build_boss_v2, which also reads
{{#expr:}} rarities exactly, shared-table rolls (a tertiary {"table": ...} entry), the DT2/Yama
"roll until one succeeds" chain, rows dropped together, the God Wars Dungeon rare/gem tables,
items that open into a table (Unsired, dossier, forgotten lockbox), and collection log tabs shared
by several bosses. Each boss entry also gets "hitpoints" and "combatLevel" from the infoboxes.
The first nine bosses are built exactly as before.

The 13 bosses in CHEST_BOSSES (added later on 2026-11-04) get their loot from a reward chest, waves or a
reward pool. One "kill" is one completed run under a standard setup (docs/drop-sources.md): the Moons of
Peril (one Moon each), Barrows, the Gauntlet and Corrupted Gauntlet, the Fight Caves, the Inferno, the
Fortis Colosseum, the Doom of Mokhaiotl, Wintertodt, Tempoross and Zalcano. They are built last, with a
shared-table registry of their own, so every entry above comes out byte-for-byte as before. Run hitpoints
are summed from the Wiki's wave lists where a run has waves.

The 4 raids in RAID_BOSSES (added last, also on 2026-11-04) count one completed raid under a standard setup as a
"kill": the Chambers of Xeric, the Theatre of Blood, the Tombs of Amascut and The Fractured Archive. A unique replaces
all of a raid's common rolls, so each entry has a "preRoll" ({"table", "numerator", "denominator"}) rolled before the
main table: a hit rolls that shared table once and skips the main rolls. Common quantities that depend on points come
from the Wiki's own loot calculators (Module:Chambers of Xeric calculator, Module:Tombs of Amascut loot). The raids
are built after the chest bosses with a shared-table registry and an item lookup of their own, and their pages are a
fetch batch of their own, so every entry above comes out byte-for-byte as before.
"""

import argparse
import datetime
import hashlib
import json
import os
import re
import time
import urllib.parse
import urllib.request
from fractions import Fraction
from math import lcm

API = 'https://oldschool.runescape.wiki/api.php'
USER_AGENT = 'idle-bosses-data-script/0.1 (RuneLite plugin dev tool)'
INT_MAX = 2 ** 31 - 1

# Bosses in output order. dropsHeading is the level-2 section holding the standard solo drops.
BOSSES = [
    {'id': 'obor', 'name': 'Obor', 'page': 'Obor', 'dropsHeading': "Members' worlds drops"},
    {'id': 'bryophyta', 'name': 'Bryophyta', 'page': 'Bryophyta', 'dropsHeading': "Members' worlds drops"},
    {'id': 'scurrius', 'name': 'Scurrius', 'page': 'Scurrius', 'dropsHeading': 'Drops (MVP/Solo)',
     'stats': {'version': 1}},
    {'id': 'giant_mole', 'name': 'Giant Mole', 'page': 'Giant Mole', 'dropsHeading': 'Drops'},
    {'id': 'sarachnis', 'name': 'Sarachnis', 'page': 'Sarachnis', 'dropsHeading': 'Drops'},
    {'id': 'king_black_dragon', 'name': 'King Black Dragon', 'page': 'King Black Dragon', 'dropsHeading': 'Drops'},
    {'id': 'kraken', 'name': 'Kraken', 'page': 'Kraken', 'dropsHeading': 'Drops',
     'stats': {'combat': [('Kraken', 0, 2)]}},
    {'id': 'zulrah', 'name': 'Zulrah', 'page': 'Zulrah', 'dropsHeading': 'Drops'},
    {'id': 'vorkath', 'name': 'Vorkath', 'page': 'Vorkath', 'dropsHeading': 'Drops', 'stats': {'version': 1}},
]

# Level-3 sections whose rows go to "always" / "tertiary". A wiki "Pre-roll" is a separate roll made
# before the main table, so it is listed as tertiary. "Uniques" (Zulrah) is a pre-roll made on
# every main-table roll, so it stays on the main roll (see PER_ROLL_PREROLL_SECTIONS).
ALWAYS_SECTIONS = {'100%'}
TERTIARY_SECTIONS = {'Tertiary', 'Pre-roll', 'Giant key'}
PER_ROLL_PREROLL_SECTIONS = {'Uniques'}

# Drops left out because they only happen in special circumstances.
EXCLUDED = {
    'Brimstone key': "only dropped on a Konar quo Maten slayer task",
    'Key (elite)': "only dropped during the matching elite clue step",
    'Moss giant bone': "only dropped during Rag and Bone Man II",
}

# Rows the wiki lists inside the main table that are really separate rolls (the listed main-table
# rows already add up to the whole table without them).
MOVE_TO_TERTIARY = {
    'kraken': {'Kraken tentacle'},
}

# Rows that each show the shared rate of "one of these" (Sarachnis: "The drop rate to receive any
# page is 1/5", and the player gets the page they have the fewest of). Each row gets an equal share.
SPLIT_SHARED_RATE = {
    'sarachnis': {'Tattered moon page', 'Tattered sun page', 'Tattered temple page'},
}

# The gem drop table's talisman slot gives a chaos talisman underground (inside the chaos region)
# and a nature talisman elsewhere. Every boss page here that says which talisman says nature.
GEM_TABLE_TALISMAN = 'Nature talisman'

# Collection log tab name on the "Collection log" page -> boss id.
LOG_SECTIONS = {b['name']: b['id'] for b in BOSSES}

CLUE_NAMES = {
    'beginner': 'Clue scroll (beginner)', 'easy': 'Clue scroll (easy)', 'medium': 'Clue scroll (medium)',
    'hard': 'Clue scroll (hard)', 'elite': 'Clue scroll (elite)', 'master': 'Clue scroll (master)',
}
# Clue scroll pages have one item per clue step and no single infobox ID. These are the IDs RuneLite
# itself treats as the generic clue of each tier (net.runelite.api.ItemID.CLUE_SCROLL_*).
ITEM_ID_OVERRIDES = {
    'Clue scroll (beginner)': 23182,
    'Clue scroll (easy)': 2677,
    'Clue scroll (medium)': 2801,
    'Clue scroll (hard)': 2722,
    'Clue scroll (elite)': 12073,
    'Clue scroll (master)': 19835,
}

# Obor's and Bryophyta's log items come from the chest in their lair, at 1/118 per open, not from their
# drops. Idle Bosses counts every kill as one chest open, so the chest roll joins their tertiary drops.
CHEST_ROLLS = {
    'obor': ('Hill giant club', Fraction(1, 118)),
    'bryophyta': ("Bryophyta's essence", Fraction(1, 118)),
}

EXTRA_PAGES = [
    'Rare drop table', 'Gem drop table', 'Collection log', 'Module:Collection log/data.json',
    'Template:HerbDropLines', 'Template:UncommonSeedDropLines', 'Template:RareSeedDropLines',
    'Template:TreeHerbSeedDropLines', 'Module:GeneralSeedDropLines',
    "Chest (Obor's lair)", "Chest (Bryophyta's lair)",
]

# --------------------------------------------------------------------------------------------
# Bosses added on 2026-11-04. They go through build_boss_v2, which reads more of the Wiki's drop
# table shapes. The nine bosses above still go through build_boss, unchanged. Keys:
#   page / dropsHeading   the Wiki page and its level-2 drops heading (default "Drops")
#   logTab                the collection log tab, when it isn't the boss name. A tab shared by
#                         several bosses gives each boss the tab's items that boss can drop.
#   sections              level-3/4 section title -> 'always', 'main', 'tertiary', 'skip',
#                         'perRollPreroll' (Zulrah-style: replaces that roll's main drop), or
#                         ('table', name): a separate roll that gives one item from that section.
#                         It becomes a shared table reached by one tertiary roll at the section's
#                         total rate. Untitled defaults: 100% -> always, Tertiary/Pre-roll ->
#                         tertiary (the existing rule), everything else -> main.
#   split                 groups of rows that each show the shared rate of "one of these"
#   companions            rows the Wiki says drop together with another row of the same slot.
#                         The first row keeps the slot; each companion becomes a tertiary roll with
#                         the same chance per kill.
#   chain                 the "rolled until one succeeds" order of DT2 bosses and Yama:
#                         ('section', title, rate) rows already at their per-kill rate,
#                         ('item', name, rate), ('together', title, rate) rows dropped together.
#                         The standard table that follows is scaled by the chance of reaching it.
#   opens                 item -> shared table rolled when the item is used (Unsired -> Font).
#   merge                 [(page, heading, weight)] loot pages the player picks between.
#   exclude               extra [(name, rarity or None, reason)] for this boss.
#   stats                 where hitpoints and combat level come from (see boss_stats).
# --------------------------------------------------------------------------------------------
F = Fraction
DT2_EXCLUDE = [('Ancient blood ornament kit', None, 'only from the awakened encounter, as the last of the four')]
GROUP_BONES = [('Bones', 'Always', 'group fights only: dropped for eligible players who did not deal the most damage')]
NEW_BOSSES = [
    {'id': 'abyssal_sire', 'name': 'Abyssal Sire', 'page': 'Abyssal Sire',
     'opens': {'Unsired': 'font_of_consumption'}},
    {'id': 'alchemical_hydra', 'name': 'Alchemical Hydra', 'page': 'Alchemical Hydra',
     'split': [("Hydra's eye", "Hydra's fang", "Hydra's heart")],
     'companions': {'Mystic water staff', 'Rune platelegs', 'Rune plateskirt', 'Mystic robe bottom (light)',
                    'Super restore(3)'}},
    {'id': 'amoxliatl', 'name': 'Amoxliatl', 'page': 'Amoxliatl', 'stats': {'version': 1},
     'sections': {'Frozen tears': ('table', 'amoxliatl_frozen_tears')}},
    {'id': 'araxxor', 'name': 'Araxxor', 'page': 'Araxxor', 'sections': {'Supplies': 'tertiary'},
     'split': [('Noxious pommel', 'Noxious point', 'Noxious blade')]},
    {'id': 'brutus', 'name': 'Brutus', 'page': 'Brutus', 'stats': {'version': 1}, 'membersOnly': True},
    {'id': 'callisto', 'name': 'Callisto', 'page': 'Callisto', 'logTab': 'Callisto and Artio', 'exclude': GROUP_BONES,
     'sections': {'Unique': ('table', 'callisto_unique_table'),
                  'Secondary Supply roll': ('table', 'wilderness_supply_roll')}},
    {'id': 'artio', 'name': 'Artio', 'page': 'Artio', 'logTab': 'Callisto and Artio',
     'sections': {'Uniques': ('table', 'artio_unique_table'), 'Supplies': ('table', 'wilderness_supply_roll')}},
    {'id': 'cerberus', 'name': 'Cerberus', 'page': 'Cerberus'},
    {'id': 'chaos_elemental', 'name': 'Chaos Elemental', 'page': 'Chaos Elemental',
     'sections': {'Minor drops': ('table', 'chaos_elemental_minor_drops')}},
    {'id': 'chaos_fanatic', 'name': 'Chaos Fanatic', 'page': 'Chaos Fanatic',
     'companions': {'Zamorak monk bottom', 'Uncut sapphire'}},
    {'id': 'commander_zilyana', 'name': 'Commander Zilyana', 'page': 'Commander Zilyana',
     'companions': {'Super restore(4)', 'Magic potion(3)'}},
    {'id': 'corporeal_beast', 'name': 'Corporeal Beast', 'page': 'Corporeal Beast',
     'sections': {'Sigils': ('table', 'corporeal_beast_sigil_table')}},
    {'id': 'crazy_archaeologist', 'name': 'Crazy archaeologist', 'page': 'Crazy archaeologist',
     'companions': {'Uncut sapphire'}},
    {'id': 'dagannoth_rex', 'name': 'Dagannoth Rex', 'page': 'Dagannoth Rex', 'logTab': 'Dagannoth Kings'},
    {'id': 'dagannoth_prime', 'name': 'Dagannoth Prime', 'page': 'Dagannoth Prime', 'logTab': 'Dagannoth Kings'},
    {'id': 'dagannoth_supreme', 'name': 'Dagannoth Supreme', 'page': 'Dagannoth Supreme', 'logTab': 'Dagannoth Kings'},
    {'id': 'deranged_archaeologist', 'name': 'Deranged archaeologist', 'page': 'Deranged archaeologist',
     'logTab': 'Deranged Archaeologist'},
    {'id': 'duke_sucellus', 'name': 'Duke Sucellus', 'page': 'Duke Sucellus', 'quantityPairs': True,
     'exclude': DT2_EXCLUDE,
     'chain': [('section', 'Uniques', F(1, 90)), ('item', "Awakener's orb", F(1, 48)),
               ('item', 'Frozen tablet', F(1, 25)), ('item', 'Ice quartz', F(1, 200)),
               ('together', 'Supplies', F(1, 5))]},
    {'id': 'general_graardor', 'name': 'General Graardor', 'page': 'General Graardor'},
    {'id': 'grotesque_guardians', 'name': 'Grotesque Guardians', 'page': 'Grotesque Guardians',
     'sections': {'Unique': 'perRollPreroll'}, 'companions': {'Ranging potion(2)', 'Super combat potion(2)'},
     'stats': {'hp': [('Dusk', 0, None, 1), ('Dawn', 0, None, 1)],
               'combat': [('Dusk', 0, 1), ('Dusk', 0, 2), ('Dawn', 0, None)]}},
    {'id': 'hespori', 'name': 'Hespori', 'page': 'Hespori',
     'sections': {'Anima seeds': ('table', 'hespori_anima_seeds')}},
    {'id': 'hueycoatl', 'name': 'The Hueycoatl', 'page': 'The Hueycoatl',
     'sections': {'Uniques': ('table', 'hueycoatl_unique_table')},
     'stats': {'hp': [('The Hueycoatl', 0, None, 1), ('The Hueycoatl', 1, None, 5), ('The Hueycoatl', 2, None, 1)]}},
    {'id': 'kalphite_queen', 'name': 'Kalphite Queen', 'page': 'Kalphite Queen',
     'sections': {'Consumables': ('table', 'kalphite_queen_consumables')},
     'stats': {'hp': [('Kalphite Queen', 0, None, 2)]}},
    {'id': 'kreearra', 'name': "Kree'arra", 'page': "Kree'arra", 'companions': {'Super defence(3)'}},
    {'id': 'kril_tsutsaroth', 'name': "K'ril Tsutsaroth", 'page': "K'ril Tsutsaroth",
     'companions': {'Super strength(3)', 'Zamorak brew(3)'}},
    {'id': 'leviathan', 'name': 'The Leviathan', 'page': 'The Leviathan', 'stats': {'version': 1},
     'quantityPairs': True, 'exclude': DT2_EXCLUDE,
     'chain': [('section', 'Uniques', F(1, 96)), ('item', "Awakener's orb", F(1, 53)),
               ('item', 'Scarred tablet', F(1, 25)), ('item', 'Smoke quartz', F(1, 200)),
               ('together', 'Supplies', F(1, 5))]},
    {'id': 'mad_angel', 'name': 'The Mad Angel', 'page': 'Mad Angel', 'logTab': 'The Mad Angel',
     'stats': {'version': 2}, 'companions': {'Prayer potion(2)', 'Super combat potion(1)'}},
    {'id': 'maggot_king', 'name': 'Maggot King', 'page': 'Maggot King', 'dropsHeading': 'Drops (open-stomach)',
     'companions': {'Dull ancient medal'}},
    {'id': 'nex', 'name': 'Nex', 'page': 'Nex', 'numericOnly': True,
     'sections': {'Uniques': ('table', 'nex_unique_table')},
     'stats': {'hp': [('Nex', 0, None, 1), ('Fumus', 0, None, 1), ('Umbra', 0, None, 1), ('Cruor', 0, None, 1),
                      ('Glacies', 0, None, 1)]}},
    {'id': 'nightmare', 'name': 'The Nightmare', 'page': 'The Nightmare',
     'sections': {"Inquisitor's and Nightmare staff table": ('table', 'nightmare_unique_table'),
                  'Orb table': ('table', 'nightmare_orb_table')},
     'stats': {'hpOverride': (7800, "the Wiki's total for the original fight: 3 phases of 2,000 shield and "
                                    "600 totem charge")}},
    {'id': 'phosanis_nightmare', 'name': "Phosani's Nightmare", 'page': "Phosani's Nightmare", 'logTab': 'The Nightmare',
     'normalizeMain': True,
     'sections': {"Inquisitor's and Nightmare staff table": ('table', 'phosanis_nightmare_unique_table'),
                  'Orb table': ('table', 'phosanis_nightmare_orb_table')},
     'stats': {'hpOverride': (2550, "the Wiki's total: 1,350 physical damage and 1,200 totem charge")}},
    {'id': 'phantom_muspah', 'name': 'Phantom Muspah', 'page': 'Phantom Muspah', 'stats': {'version': 1},
     'sections': {'Unique': ('table', 'phantom_muspah_unique_table'), 'Supplies': 'tertiary'}},
    {'id': 'royal_titans', 'name': 'Royal Titans', 'page': 'Royal Titans',
     'merge': [('Branda the Fire Queen', 'Drops', F(1, 2)), ('Eldric the Ice King', 'Drops', F(1, 2))],
     'exclude': [('Desiccated page', 'Always', 'the "Take pages" option, which replaces the loot')],
     'stats': {'hp': [('Branda the Fire Queen', 0, None, 1), ('Eldric the Ice King', 0, None, 1)],
               'combat': [('Branda the Fire Queen', 0, None), ('Eldric the Ice King', 0, None)]}},
    {'id': 'scorpia', 'name': 'Scorpia', 'page': 'Scorpia'},
    {'id': 'shellbane_gryphon', 'name': 'Shellbane Gryphon', 'page': 'Shellbane gryphon', 'logTab': 'Shellbane Gryphon',
     'stats': {'version': 1}},
    {'id': 'skotizo', 'name': 'Skotizo', 'page': 'Skotizo',
     'sections': {'Ancient shards': ('table', 'skotizo_ancient_shards')},
     'split': [('Dark totem base', 'Dark totem middle', 'Dark totem top')]},
    {'id': 'thermonuclear_smoke_devil', 'name': 'Thermonuclear smoke devil', 'page': 'Thermonuclear smoke devil'},
    {'id': 'vardorvis', 'name': 'Vardorvis', 'page': 'Vardorvis', 'stats': {'version': 1}, 'quantityPairs': True,
     'exclude': DT2_EXCLUDE,
     'chain': [('section', 'Uniques', F(1, 136)), ('item', "Awakener's orb", F(1, 80)),
               ('item', 'Strangled tablet', F(1, 25)), ('item', 'Blood quartz', F(1, 200)),
               ('together', 'Supplies', F(1, 5))]},
    {'id': 'venenatis', 'name': 'Venenatis', 'page': 'Venenatis', 'logTab': 'Venenatis and Spindel',
     'exclude': GROUP_BONES,
     'sections': {'Unique': ('table', 'venenatis_unique_table'),
                  'Secondary Supply roll': ('table', 'wilderness_supply_roll')}},
    {'id': 'spindel', 'name': 'Spindel', 'page': 'Spindel', 'logTab': 'Venenatis and Spindel',
     'sections': {'Uniques': ('table', 'spindel_unique_table'), 'Supplies': ('table', 'wilderness_supply_roll')}},
    {'id': 'vetion', 'name': "Vet'ion", 'page': "Vet'ion", 'logTab': "Vet'ion and Calvar'ion", 'exclude': GROUP_BONES,
     'sections': {'Unique': ('table', 'vetion_unique_table'),
                  'Secondary Supply roll': ('table', 'wilderness_supply_roll')},
     'stats': {'hp': [("Vet'ion", 0, None, 2), ("Skeleton Hellhound (Vet'ion)", 0, None, 2),
                      ("Greater Skeleton Hellhound (Vet'ion)", 0, None, 2)]}},
    {'id': 'calvarion', 'name': "Calvar'ion", 'page': "Calvar'ion", 'logTab': "Vet'ion and Calvar'ion",
     'sections': {'Unique': ('table', 'calvarion_unique_table'), 'Supplies': ('table', 'wilderness_supply_roll')},
     'stats': {'hp': [("Calvar'ion", 0, None, 2), ("Skeleton Hellhound (Calvar'ion)", 0, None, 2),
                      ("Greater Skeleton Hellhound (Calvar'ion)", 0, None, 2)]}},
    {'id': 'whisperer', 'name': 'The Whisperer', 'page': 'The Whisperer', 'stats': {'version': 1},
     'quantityPairs': True, 'exclude': DT2_EXCLUDE,
     'chain': [('section', 'Uniques', F(1, 64)), ('item', "Awakener's orb", F(1, 34)),
               ('item', 'Sirenic tablet', F(1, 25)), ('item', 'Shadow quartz', F(1, 200)),
               ('together', 'Supplies', F(1, 5))]},
    {'id': 'yama', 'name': 'Yama', 'page': 'Yama',
     'sections': {'Junk': 'skip', 'Contract': 'skip'},
     'chain': [('section', 'Unique', F(1, 120)), ('item', 'Dossier', F(1, 12)),
               ('item', 'Forgotten lockbox', F(1, 30)), ('item', 'Oathplate shards', F(1, 15))],
     # The standard table is a 78-weight table; the Wiki shows each row as w/95.11, which is w/78 times
     # the chance of reaching it (95.11 = 78 / 0.8201).
     'standardDenominators': {'95.11': 78},
     'companions': {'Prayer potion(3)', 'Super restore mix(2)', 'Super combat potion(1)', 'Zamorak mix(2)'},
     'opens': {'Dossier': 'dossier', 'Forgotten lockbox': 'forgotten_lockbox'},
     'stats': {'hp': [('Yama', 0, None, 1), ('Judge of Yama', 0, None, 2)]}},
]
NEW_BOSS_PAGES = [b['page'] for b in NEW_BOSSES]

# Pages the new bosses need besides their own. Fetched in this order (it fixes the cache keys).
NEW_EXTRA_PAGES = [
    'Template:GWDRDT', 'Template:Uniques/Corporeal Beast', 'Template:TalismanDropLines', 'Template:FossilDropLines',
    'Template:WildernessSlayerDropTable', 'Branda the Fire Queen', 'Eldric the Ice King', 'Unsired',
    'The Font of Consumption', 'Rite of vile transference', 'Forgotten lockbox', 'Dusk', 'Dawn',
    "Skeleton Hellhound (Vet'ion)", "Greater Skeleton Hellhound (Vet'ion)", "Skeleton Hellhound (Calvar'ion)",
    "Greater Skeleton Hellhound (Calvar'ion)", 'Fumus', 'Umbra', 'Cruor', 'Glacies', 'Judge of Yama', 'Dossier',
    'Template:CalculateRDTNaked', 'God Wars Dungeon-variant rare drop table',
]

# Shared tables rolled when an item is used: (page, level-2 heading, rows that share one rate).
OPEN_TABLES = {
    'font_of_consumption': ('Unsired', 'Rewards', [('Bludgeon claw', 'Bludgeon spine', 'Bludgeon axon')]),
    'dossier': ('Dossier', 'Drops', []),
    'forgotten_lockbox': ('Forgotten lockbox', 'Rewards', []),
}

# Rows left out for every new boss because they only drop in special circumstances.
NEW_EXCLUDED_ROWS = [
    ('Brimstone key', None, 'only dropped on a Konar quo Maten slayer task'),
    ('Key (elite)', None, 'only dropped during the matching elite clue step'),
    ('Clue scroll (elite)', 'Always', 'only during an elite clue step that asks for this monster'),
    ('Reward casket (elite)', 'Always', 'only during an elite clue step that asks for this monster'),
    ('Dagannoth ribs', None, 'only dropped during Rag and Bone Man II'),
    ('Frozen key piece (Saradomin)', None, 'only dropped during The Frozen Door miniquest'),
    ('Frozen key piece (Bandos)', None, 'only dropped during The Frozen Door miniquest'),
    ('Frozen key piece (Armadyl)', None, 'only dropped during The Frozen Door miniquest'),
    ('Frozen key piece (Zamorak)', None, 'only dropped during The Frozen Door miniquest'),
    ('Odd key', None, 'only dropped by the quest variant'),
    ('Temple key (Desert Treasure II)', None, 'only dropped by the quest variant'),
    ('Kq head (tattered)', None, 'a one-off reward on the 256th kill, not a drop roll'),
    ('Sunstone crystal', None, 'only while the bank chest is unbuilt and no crystal is owned'),
]
# The gold rings from the DT2 vestige counter and the Wiki's unlisted remainders are left as nothing.

# Drop rows whose Wiki name is a different item version from the one the boss drops. The Royal Titans drop
# the uncharged amulet (the collection log item, 30637); the "Giantsoul amulet" infobox is the charged 30638.
DROP_NAME_ALIASES = {'Giantsoul amulet': 'Giantsoul amulet (uncharged)'}

# --------------------------------------------------------------------------------------------
# Bosses whose loot comes from a reward chest, waves or a reward pool (CHEST_BOSSES, added 2026-11-04 after
# the 44 above). One "kill" is one completed run under the standard setup in docs/drop-sources.md. They are
# built after everything above and only add bosses and shared tables. Keys, besides page / dropsHeading /
# logTab / sections / exclude / companions, which mean what they mean for NEW_BOSSES:
#   kind             'rows': the drop rows of page/dropsHeading, sorted by section as for NEW_BOSSES.
#                    'doom', 'wintertodt', 'tempoross': a builder of its own (build_doom and so on).
#   rowRoles         row name -> role, in place of its section's role ('special': the builder handles it).
#   rolls            main-table rolls for this setup, when the Wiki's 'rolls' are for another setup.
#   perRollTertiary  tertiary rows the Wiki rates per main-table roll, given at most once: 1 - (1 - p)^rolls.
#   always           [(item, page, regex)]: rewards the Wiki gives in prose. A regex group is the quantity.
#   quantities       row name -> (min, max) for this setup.
#   expectTotal      role -> the total its rows must have (the Wiki's stated chance).
#   checks           [(page, regex)]: Wiki statements the setup depends on. The build stops if one is gone.
#   notInLog         log items no run can give -> reason. They are left out of the boss's log.
#   stats            as for NEW_BOSSES, or ('waves', name) (summed from the Wiki's wave lists), or
#                    ('figure', page, regex, what) (the groups are added up; combat level 0).
# --------------------------------------------------------------------------------------------
MOON_SETS = {
    'Blue Moon': ('Blue Moon spear', 'Blue Moon helm', 'Blue Moon chestplate', 'Blue Moon tassets'),
    'Eclipse Moon': ('Eclipse atlatl', 'Eclipse Moon helm', 'Eclipse Moon chestplate', 'Eclipse Moon tassets'),
    'Blood Moon': ('Dual macuahuitl', 'Blood Moon helm', 'Blood Moon chestplate', 'Blood Moon tassets'),
}


def moon_cfg(bid, name):
    """One Moon defeated, then the Lunar Chest opened: a 1/56 roll for that Moon's set, otherwise one
    standard roll."""
    others = [p for moon, pieces in MOON_SETS.items() if moon != name for p in pieces]
    return {'id': bid, 'name': name, 'kind': 'rows', 'page': 'Lunar Chest', 'dropsHeading': 'Rewards',
            'logTab': 'Moons of Peril', 'sections': {'Uniques': 'perRollPreroll'}, 'rolls': 1,
            'exclude': [(p, None, "another Moon's armour set") for p in others],
            'expectTotal': {'perRollPreroll': F(1, 56)},
            'checks': [('Lunar Chest', r'grants the player a non-stackable 1/56 chance at a piece of their equipment'),
                       ('Lunar Chest', r'1 Moon gives 1 roll'),
                       ('Lunar Chest', r'If a unique item is received, the standard loot table will not be rolled')],
            'stats': {'hp': [(name, 0, None, 1)], 'combat': [(name, 0, None)]}}


BARROWS_BROTHERS = ['Ahrim the Blighted', 'Dharok the Wretched', 'Guthan the Infested', 'Karil the Tainted',
                    'Torag the Corrupted', 'Verac the Defiled']
SPIRIT_ANGLER = ('Spirit angler headband', 'Spirit angler top', 'Spirit angler waders', 'Spirit angler boots')
CHEST_BOSSES = [
    moon_cfg('blue_moon', 'Blue Moon'),
    moon_cfg('eclipse_moon', 'Eclipse Moon'),
    moon_cfg('blood_moon', 'Blood Moon'),
    {'id': 'barrows', 'name': 'Barrows', 'kind': 'rows', 'page': 'Chest (Barrows)', 'dropsHeading': 'Rewards',
     'logTab': 'Barrows Chests',
     'sections': {"Ahrim's": 'perRollPreroll', "Dharok's": 'perRollPreroll', "Guthan's": 'perRollPreroll',
                  "Karil's": 'perRollPreroll', "Torag's": 'perRollPreroll', "Verac's": 'perRollPreroll'},
     'perRollTertiary': {'Clue scroll (elite)'},
     'expectTotal': {'perRollPreroll': F(1, 450 - 58 * 6)},
     'checks': [('Chest (Barrows)', r'accurate for a Barrows run with all 6 brothers slain and with full potential '
                                    r'\(1012 points\)'),
                ('Chest (Barrows)', r'P\(N\) &= \\frac\{1\}\{450 - 58 \\times N\}'),
                ('Chest (Barrows)', r'7 individual rolls\. If it succeeded on one, it would skip the clue roll')],
     'stats': {'hp': [(b, 0, None, 1) for b in BARROWS_BROTHERS], 'combat': [(b, 0, None) for b in BARROWS_BROTHERS]}},
    {'id': 'gauntlet', 'name': 'The Gauntlet', 'kind': 'rows', 'page': 'Reward Chest (The Gauntlet)',
     'dropsHeading': 'Regular loot table<span id="Regular"/>', 'logTab': 'The Gauntlet',
     'checks': [('Reward Chest (The Gauntlet)', r'If \[\[Crystalline Hunllef\]\] is defeated, players receive 5-9 '
                                                r'\[\[crystal shards\]\] and roll twice')],
     'stats': {'hp': [('Crystalline Hunllef', 0, None, 1)], 'combat': [('Crystalline Hunllef', 0, None)]}},
    {'id': 'corrupted_gauntlet', 'name': 'The Corrupted Gauntlet', 'kind': 'rows', 'page': 'Reward Chest (The Gauntlet)',
     'dropsHeading': 'Corrupted loot table<span id="Corrupted"/>', 'logTab': 'The Gauntlet',
     'checks': [('Reward Chest (The Gauntlet)', r'If \[\[Corrupted Hunllef\]\] is defeated, players receive 7-12 '
                                                r'\[\[crystal shards\]\] and roll three times')],
     'stats': {'hp': [('Corrupted Hunllef', 0, None, 1)], 'combat': [('Corrupted Hunllef', 0, None)]}},
    {'id': 'tztok_jad', 'name': 'TzTok-Jad', 'kind': 'rows', 'page': 'TzTok-Jad', 'logTab': 'The Fight Caves',
     'always': [('Fire cape', 'TzTok-Jad', r'they will be rewarded with the coveted \[\[fire cape\]\]'),
                ('Tokkul', 'TzTok-Jad', r'\[\[fire cape\]\] along with ([\d,]+) \[\[Tokkul\]\] \(')],
     'checks': [('TzTok-Jad', r'Drop rate is 1/200 for completing the cave off-task')],
     'stats': ('waves', 'fightCaves')},
    {'id': 'tzkal_zuk', 'name': 'TzKal-Zuk', 'kind': 'rows', 'page': 'TzKal-Zuk', 'logTab': 'The Inferno',
     'sections': {'Drops': 'tertiary'},
     'always': [('Infernal cape', 'Inferno', r'they will be rewarded with the \[\[infernal cape\]\]'),
                ('Tokkul', 'Inferno', r'\(([\d,]+) maximum; this is doubled if the player has completed the elite')],
     'checks': [('Inferno', r'There is also a 1/100 chance of receiving a \[\[Jal-Nib-Rek\]\]')],
     'stats': ('waves', 'inferno')},
    {'id': 'sol_heredit', 'name': 'Sol Heredit', 'kind': 'rows', 'page': 'Rewards Chest (Fortis Colosseum)',
     'dropsHeading': 'Rewards', 'logTab': 'Fortis Colosseum',
     'sections': dict({'Wave 1': 'always', 'Wave 12': 'main'},
                      **{'Wave %d' % n: ('table', 'fortis_colosseum_wave_%d' % n) for n in range(2, 12)}),
     'rowRoles': {'Smol Heredit': 'tertiary'},
     'exclude': [('Token (Varlamore)', 'Varies', 'a daily Varlamore token, given only on some days of the week '
                                                 'to some character-name lengths')],
     'checks': [('Rewards Chest (Fortis Colosseum)', r'completing wave 12 will guarantee \[\[Dizana\'s quiver\]\] '
                                                     r'from the rewards chest\. Additionally, a flat 1/200 chance')],
     'stats': ('waves', 'colosseum')},
    {'id': 'doom_of_mokhaiotl', 'name': 'Doom of Mokhaiotl', 'kind': 'doom', 'page': 'Doom of Mokhaiotl',
     'logTab': 'Doom of Mokhaiotl', 'delve': 8,
     'sections': {'Uniques': 'special', 'Tertiary': 'special'},
     'rowRoles': {'Clue scroll (elite)': 'special', 'Dom': 'special'},
     'checks': [('Doom of Mokhaiotl', r'Each delve level rolls once on the regular loot table')],
     'stats': {'hp': [('Doom of Mokhaiotl', 0, v, 1) for v in range(1, 9)], 'combat': [('Doom of Mokhaiotl', 0, 8)]}},
    {'id': 'wintertodt', 'name': 'Wintertodt', 'kind': 'wintertodt', 'page': 'Reward Cart', 'dropsHeading': 'Rewards',
     'logTab': 'Wintertodt', 'points': 750, 'skillLevel': 70,
     'stats': ('figure', 'Wintertodt/Strategies', r'imagine that the Wintertodt is a boss with ([\d,]+) Hitpoints',
               'the Wiki\'s "a boss with 700 Hitpoints" equivalent of its energy bar')},
    {'id': 'tempoross', 'name': 'Tempoross', 'kind': 'tempoross', 'page': 'Reward pool', 'logTab': 'Tempoross',
     'permits': 5, 'fishBand': 'Levels 50–75',
     'notInLog': {n: 'bought from Gita Prymes for 1,200 spirit flakes and the angler piece, not a reward roll'
                  for n in SPIRIT_ANGLER},
     'checks': [('Tempoross/Strategies', r'consistently yields 4,000–5,000 points per round and up to 6 rewards'),
                ("Spirit angler's outfit", r'trading in 1,200 \[\[spirit flakes\]\], along with the corresponding '
                                           r'angler\'s outfit piece')],
     'stats': ('figure', 'Tempoross', r'~(\d+) energy and ~(\d+) essence per player when 40 players embark',
               "one player's share of the energy and essence in a 40-player game")},
    {'id': 'zalcano', 'name': 'Zalcano', 'kind': 'rows', 'page': 'Zalcano', 'logTab': 'Zalcano',
     'rowRoles': {'Crystal tool seed': ('table', 'zalcano_tool_seed_table'),
                  'Uncut onyx': ('table', 'zalcano_tool_seed_table')},
     'quantities': {'Crystal shard': (3, 3)},
     'expectTotal': {('table', 'zalcano_tool_seed_table'): F(1, 200)},
     'checks': [('Zalcano', r'Every Zalcano kill always has a 1/200 chance of rolling the \[\[crystal tool seed\]\] '
                            r'table'),
                ('Zalcano', r'3 Shards - Player is the MVP'),
                ('Zalcano', r'Between 1/750 and 1/1500 depending on contribution')],
     'stats': {'hp': [('Zalcano', 0, 1, 1), ('Zalcano', 0, 2, 1)], 'combat': [('Zalcano', 0, None)]}},
]

# Pages the chest bosses read, in fetch order (it fixes the cache keys).
CHEST_PAGES = [
    'Lunar Chest', 'Blue Moon', 'Eclipse Moon', 'Blood Moon', 'Chest (Barrows)'] + BARROWS_BROTHERS + [
    'Reward Chest (The Gauntlet)', 'Crystalline Hunllef', 'Corrupted Hunllef', 'TzTok-Jad', 'TzHaar Fight Cave',
    'Template:TzHaar Fight Cave Waves', 'TzKal-Zuk', 'Inferno', 'Jal-AkRek-Ket', 'Jal-AkRek-Mej', 'Jal-AkRek-Xil',
    'Rewards Chest (Fortis Colosseum)', 'Fortis Colosseum', 'Fortis Colosseum/Strategies', 'Sol Heredit',
    'Doom of Mokhaiotl', 'Reward Cart', 'Module:Wintertodt supply crate', 'Wintertodt/Strategies', 'Reward pool',
    'Template:Reward pool/Rewards unique', 'Template:Reward pool/Rewards levels 50–75',
    'Template:Reward pool/Rewards other', 'Tempoross', 'Tempoross/Strategies', "Spirit angler's outfit", 'Zalcano',
    'Token (Varlamore)',
]

# Row names that are a different item version from the one the run gives (the collection log item).
CHEST_NAME_ALIASES = {"Dizana's quiver": "Dizana's quiver (uncharged)"}

# --------------------------------------------------------------------------------------------
# The four raids (RAID_BOSSES, added 2026-11-04 after the chest bosses). One "kill" is one completed raid under the
# standard setup in docs/drop-sources.md. A unique replaces all of a raid's common rolls, so each entry has
#   "preRoll": {"table": <shared table>, "numerator": n, "denominator": d}
# rolled first: if it hits, that shared table is rolled once and "main" isn't rolled at all; if it misses, "main" is
# rolled "rolls" times. "always" and "tertiary" are given either way. The raids are built after everything above,
# with a shared-table registry and an item lookup of their own, so every entry above comes out byte-for-byte as
# before. Keys, besides id / name / page / dropsHeading / logTab / exclude / checks / notInLog, which mean what they
# mean for CHEST_BOSSES:
#   kind          'cox', 'tob', 'toa' or 'tfa': the builder (build_cox and so on) and the hitpoints function.
#   uniqueTable   the shared table the pre-roll rolls.
#   sections      section title -> 'unique', 'main', 'tertiary', 'skip' (another mode's rows) or a role the
#                 builder handles ('challenge' for the Tombs of Amascut's challenge rewards).
#   rolls         common-table rolls per run; every main row must be listed at this many rolls.
#   uniqueTotal / mainTotal   what the unique rows and the main rows must add up to.
#   the rest      the setup's numbers (points, teamSize, raidLevel, ...), read by the builder.
# --------------------------------------------------------------------------------------------
RAID_ONE_OFF = ("a one-off item given only when the player owns none, and not a collection log item (as the Mad "
                "Angel's sunstone crystal)")
XERIC_CAPES = ("Xeric's guard", "Xeric's warrior", "Xeric's sentinel", "Xeric's general", "Xeric's champion")
SINHAZA_SHROUDS = tuple('Sinhaza shroud tier %d' % n for n in range(1, 6))
ICTHLARIN_SHROUDS = tuple("Icthlarin's shroud (tier %d)" % n for n in range(1, 6))
LIVING_RECORDS = tuple('Living record (tier %d)' % n for n in range(1, 6))
MILESTONES = (100, 500, 1000, 1500, 2000)


def milestone_checks(page, capes, earned):
    """Checks that each cape is listed on the raid page's accolade table at its milestone (written 1000 or 1,000)."""
    return [(page, r'\{\{plinkt?\|%s\}\}\s*(?:\n\|\s*|\|\|\s*)%s%s' % (re.escape(c), format(n, ',').replace(',', ',?'),
                                                                      earned))
            for c, n in zip(capes, MILESTONES)]


RAID_BOSSES = [
    {'id': 'chambers_of_xeric', 'name': 'Chambers of Xeric', 'kind': 'cox', 'page': 'Ancient chest',
     'dropsHeading': 'Loot table', 'logTab': 'Chambers of Xeric', 'uniqueTable': 'chambers_of_xeric_unique_table',
     'points': 33000, 'rolls': 2, 'uniqueTotal': F(1), 'mainTotal': F(1),
     'sections': {'Normal mode': 'unique', 'Challenge mode': 'skip', 'Runes and ammunition': 'main', 'Herbs': 'main',
                  'Ores and gems': 'main', 'Other': 'main', 'Tertiary': 'tertiary'},
     'exclude': [('Ancient tablet', None, RAID_ONE_OFF), ('Dark journal', None, RAID_ONE_OFF),
                 ('Twisted ancestral colour kit', None, 'Challenge Mode only (a timed Challenge Mode raid)'),
                 ('Metamorphic dust', None, 'Challenge Mode only (a timed Challenge Mode raid)')],
     'notInLog': dict({c: 'a cape from Captain Rimor for %s Challenge Mode completions, not a chest reward'
                          % format(n, ',') for c, n in zip(XERIC_CAPES, MILESTONES)},
                      **{n: 'Challenge Mode only (a timed Challenge Mode raid)'
                         for n in ('Metamorphic dust', 'Twisted ancestral colour kit')}),
     'checks': [('Money making guide/Chambers of Xeric (Normal Mode)',
                 r'with the player earning 33,000 points in each'),
                ('Ancient chest', r'For every 8,676 total points obtained, a 1% chance to obtain a unique loot is '
                                  r'given\. If one of these items is received, no common rewards are given'),
                ('Ancient chest', r'In groups, the player\'s points will also determine who is more likely to obtain '
                                  r'the unique drop'),
                ('Ancient chest', r'they will make two rolls on the common drop table\. Unlike ToA and ToB, the two '
                                  r'rolls cannot end on the same drop'),
                ('Ancient chest', r'Players will always find a \[\[dark journal\]\] inside unless it is stored in '
                                  r'their bank'),
                ('Ancient chest', r'\{\{Calculator:Chambers of Xeric loot\}\}'),
                ('Ancient chest', r'Only obtainable in \[\[Chambers of Xeric/Challenge Mode\|Challenge Mode\]\] and if '
                                  r'the raid was completed within a certain time'),
                ('Ancient chest', r'The Ancient tablet does not drop if one is already owned'),
                ('Calculator:Chambers of Xeric loot', r'template=Calculator:Chambers of Xeric/Template'),
                ('Calculator:Chambers of Xeric/Template', r'\{\{#invoke:Chambers of Xeric calculator\|main\}\}'),
                ('Chambers of Xeric', r'Common loot awarded at the end of the raid scales up to 131,071 points'),
                ('Chambers of Xeric', r'Players can earn Xerician capes through repeated completions of the Chambers '
                                      r'of Xeric, claimed from \[\[Captain Rimor\]\]'),
                ('Chambers of Xeric/Strategies', r'The only exception is Tekton\'s rare \[\[onyx\]\] drop'),
                ('Money making guide/Chambers of Xeric (Normal Mode)',
                 r'\|Output55 = Onyx\s*\|Output55num = \{\{#expr:1/450\}\}')] +
     milestone_checks('Chambers of Xeric', XERIC_CAPES, ' completions of Challenge mode'),
     'stats': 'cox'},
    {'id': 'theatre_of_blood', 'name': 'Theatre of Blood', 'kind': 'tob', 'page': 'Monumental chest',
     'dropsHeading': 'Loot table', 'logTab': 'Theatre of Blood', 'uniqueTable': 'theatre_of_blood_unique_table',
     'teamSize': 3, 'rolls': 3, 'uniqueTotal': F(1), 'mainTotal': F(1),
     'sections': {'Normal mode': 'unique', 'Hard mode': 'skip', 'Common rewards': 'main',
                  'Tertiary rewards': 'tertiary'},
     'exclude': [('Cabbage', 'Always', 'only for a player who ends the raid with 0 contribution points'),
                 ('Message (Theatre of Blood)', 'Always', 'only for a player who ends the raid with 0 contribution '
                                                          'points'),
                 ('Holy ornament kit', None, 'Hard Mode only'), ('Sanguine ornament kit', None, 'Hard Mode only'),
                 ('Sanguine dust', None, 'Hard Mode only')],
     'notInLog': dict({c: 'a cape from the Mysterious Stranger for %s Normal or Hard Mode completions, not a chest '
                          'reward' % format(n, ',') for c, n in zip(SINHAZA_SHROUDS, MILESTONES)},
                      **{n: 'Hard Mode only' for n in ('Holy ornament kit', 'Sanguine ornament kit', 'Sanguine dust')}),
     'checks': [('Money making guide/Theatre of Blood', r'in trios with 0 deaths'),
                ('Money making guide/Theatre of Blood', r'\(1/172\.9\)\*\(1/3\)'),
                ('Monumental chest', r'The drop rate is the same regardless of the number of players completing the '
                                     r'raid\. The item is allocated to one player, with the weighting based on deaths '
                                     r'and damage dealt to each boss'),
                ('Monumental chest', r'If the player obtains a unique reward, it will replace all common rewards\. If '
                                     r'not, the common reward table is rolled three times'),
                ('Monumental chest', r'Only obtained if a player ends the raid with 0 individual contribution points'),
                ('Monumental chest', r'name=hardmode\|Only obtainable in Hard Mode\.'),
                ('Monumental chest', r'Drop rate scales down based on the individual\'s performance during the raid'),
                ('Theatre of Blood', r'Players can earn Sinhaza shrouds through repeated completions of the Theatre, '
                                     r'claimed from the \[\[Mysterious Stranger\]\]')] +
     milestone_checks('Theatre of Blood', SINHAZA_SHROUDS, ' completions of Normal/Hard mode'),
     'stats': 'tob'},
    {'id': 'tombs_of_amascut', 'name': 'Tombs of Amascut', 'kind': 'toa', 'page': 'Chest (Tombs of Amascut)',
     'dropsHeading': 'Loot table', 'logTab': 'Tombs of Amascut', 'uniqueTable': 'tombs_of_amascut_unique_table',
     'teamSize': 1, 'rolls': 3, 'uniqueTotal': F(1), 'mainTotal': F(1),
     'sections': {'Pre-roll': 'skip', 'Uniques': 'unique', 'Common rewards': 'main', 'Tertiary rewards': 'tertiary',
                  'Challenge rewards': 'challenge'},
     'exclude': [('Fossilised dung', 'Always', 'only for a player who ends the raid with under 1,500 reward points')],
     'notInLog': dict({c: 'a cape from the lobby chest for %s Normal or Expert completions, not a chest reward'
                          % format(n, ',') for c, n in zip(ICTHLARIN_SHROUDS, MILESTONES)}),
     'checks': [('Money making guide/Tombs of Amascut (Expert)',
                 r'Completing Expert solo \[\[Tombs of Amascut\]\] \(lvl 300\)'),
                ('Money making guide/Tombs of Amascut (Expert)', r'The profit rate assumes \{\{#var:kph\}\} kills per '
                                                                  r'hour in solos with 0 deaths'),
                ('Money making guide/Tombs of Amascut (Expert)', r'\|Output1num = \{\{#expr:1\*\(1/540\)\}\}'),
                ('Chest (Tombs of Amascut)', r'At the end of the raid, the starting 5,000 points are subtracted when '
                                             r'calculating loot and purple percentage'),
                ('Chest (Tombs of Amascut)', r'the player who receives the item will have their corresponding chest '
                                             r'emptied, as the common drop rolls are not made for them'),
                ('Chest (Tombs of Amascut)', r'Three rolls are also made on the common drop table'),
                ('Chest (Tombs of Amascut)', r'If one of these items is received, no common rewards are given\. '
                                             r'Tertiary rewards are still obtainable'),
                ('Chest (Tombs of Amascut)', r'\\frac\{\\text\{Points\}\}\{\\text\{ItemDivisor\}\} \\times \(1\.15 \+ '
                                             r'0\.01\\times\\frac\{\\text\{RaidLevel\}-300\}\{5\}\) & \\text\{if '
                                             r'RaidLevel\} \\geq 300'),
                ('Chest (Tombs of Amascut)', r'The common drop table also includes \[\[cache of runes\]\], but instead '
                                             r'of following the above formula, the quantity is set to one'),
                ('Chest (Tombs of Amascut)', r'The base rate for the thread is 1/10'),
                ('Chest (Tombs of Amascut)', r'the base rate for a specific jewel is 1/50'),
                ('Chest (Tombs of Amascut)', r'Only obtained if a player ends the raid with less than 1,500 total '
                                             r'reward points'),
                ('Calculator:Tombs of Amascut loot', r'module=Tombs of Amascut loot'),
                ('Tombs of Amascut', r'Players can earn Icthlarin\'s shrouds through repeated completions of the Tombs '
                                     r'of Amascut, claimed from a chest in the lobby')] +
     milestone_checks('Tombs of Amascut', ICTHLARIN_SHROUDS[:4], ' completions of Normal/Expert mode') +
     [('Tombs of Amascut', r'\{\{plink\|Icthlarin\'s shroud \(tier 5\)\}\}\s*\n\|2000 completions of Normal/Expert '
                           r'mode')],
     'stats': 'toa'},
    {'id': 'fractured_archive', 'name': 'The Fractured Archive', 'kind': 'tfa', 'page': 'Chest (The Fractured Archive)',
     'dropsHeading': 'Drops', 'logTab': 'The Fractured Archive', 'uniqueTable': 'fractured_archive_unique_table',
     'teamSize': 3, 'rolls': 3, 'uniqueTotal': F(1), 'mainTotal': F(1),
     'sections': {'Uniques': 'unique', 'Weapons and armour': 'main', 'Runes': 'main', 'Herbs': 'main', 'Seeds': 'main',
                  'Ores and gems': 'main', 'Other': 'main', 'Tertiary': 'tertiary', 'Junk': 'skip'},
     'exclude': [('Cabbage', 'Always', 'the junk table: only for a poor performance, and it replaces all other loot')],
     'notInLog': {c: 'a cape for %s completions, not a chest reward' % format(n, ',')
                  for c, n in zip(LIVING_RECORDS, MILESTONES)},
     'checks': [('Money making guide/The Fractured Archive (Trio)', r'the rate of receiving a unique drop per raid is '
                                                                     r'roughly 1/13\.46 for a deathless trio'),
                ('Money making guide/The Fractured Archive (Trio)', r'#vardefine:unique\|\{\{#expr:1/14\*1\.04\}\}'),
                ('Money making guide/The Fractured Archive (Trio)',
                 r'#vardefine:regchance\|\{\{#expr:1-\(\{\{#var:unique\}\}/3\)\}\}'),
                ('Chest (The Fractured Archive)', r'At the end of the raid, there is a 1/14 chance of successfully '
                                                  r'rolling for a unique drop'),
                ('Chest (The Fractured Archive)', r'each additional player will increase the drop rate of a unique by '
                                                  r'a multiplicative 4%\. This means in a trio, the drop rate becomes '
                                                  r'~1/13\.46 \(1/14 x 1\.04\)'),
                ('Chest (The Fractured Archive)', r'the player\'s contribution is rolled against the total '
                                                  r'contribution obtained by the team to determine if the drop will be '
                                                  r'given to them'),
                ('Chest (The Fractured Archive)', r'If a unique item is not received, the standard loot table is '
                                                  r'rolled three times'),
                ('Chest (The Fractured Archive)', r'If a cabbage is rolled, the player is not eligible for any other '
                                                  r'tertiary drops'),
                ('Chest (The Fractured Archive)', r'name=dupe\|Does not drop if one is already owned'),
                ('Chest (The Fractured Archive)', r'name=fragDupe\|The rate of obtaining an elemental fragment is '
                                                  r'1/20, with a duplicate-avoidance system'),
                ('The Fractured Archive', r'Cosmetic \[\[raid cape\]\]s are awarded after reaching milestone numbers '
                                          r'of completions')] +
     milestone_checks('The Fractured Archive', LIVING_RECORDS, r'\s*\n'),
     'stats': 'tfa'},
]

# Pages the raids read, in fetch order (it fixes the cache keys). A batch's cache key is its first 150 characters, so a
# page added to a batch must go where it changes them.
RAID_PAGES = [
    'Ancient chest', 'Money making guide/Chambers of Xeric (Normal Mode)', 'Calculator:Chambers of Xeric loot',
    'Calculator:Chambers of Xeric/Template', 'Module:Chambers of Xeric calculator', 'Chambers of Xeric',
    'Chambers of Xeric/Strategies', 'Great Olm', 'Tekton',
    'Monumental chest', 'Money making guide/Theatre of Blood', 'Theatre of Blood', 'The Maiden of Sugadinti',
    'Pestilent Bloat', 'Nylocas Vasilias', 'Sotetseg', 'Xarpus', 'Verzik Vitur',
    'Chest (Tombs of Amascut)', 'Money making guide/Tombs of Amascut (Expert)', 'Calculator:Tombs of Amascut loot',
    'Module:Tombs of Amascut loot', 'Tombs of Amascut', 'Tombs of Amascut/Strategies', 'Ba-Ba', 'Kephri', 'Zebak',
    'Akkha', 'Obelisk (Tombs of Amascut)', "Tumeken's Warden", "Elidinis' Warden",
    'Chest (The Fractured Archive)', 'Money making guide/The Fractured Archive (Trio)', 'The Fractured Archive',
    'The Fractured Archive/Strategies', 'Archive Guardian', 'Bork', 'Dreadnoth the Jellied', 'Xarth', 'Ocellus',
]

# The Theatre of Blood's bosses: (page, infobox index, normal-mode versions). Xarpus and Verzik have one infobox per
# mode.
TOB_BOSSES = [('The Maiden of Sugadinti', 0, (2,)), ('Pestilent Bloat', 0, (2,)), ('Nylocas Vasilias', 0, (2,)),
              ('Sotetseg', 0, (2,)), ('Xarpus', 1, (None,)), ('Verzik Vitur', 1, (1, 2, 3))]
# The Fractured Archive's run: each boss page and the columns of its team-size hitpoints table that are counted.
# At each fork the most-fought boss of the raid's first week is taken (The Fractured Archive, Trivia).
TFA_RUN = [('Archive Guardian', ('Flying', 'Grounded')), ('Bork', ('Bork', 'Smorks')),
           ('Dreadnoth the Jellied', ('Hitpoints',)), ('Xarth', ('Hitpoints',)),
           ('Ocellus', ('Phase 1 (per team member)', 'Remainder (per team member)'))]
TFA_FORKS = [('Ezekiel', 'Bork and the Smorks'), ('Moia', 'Dreadnoth the Jellied'), ("Dagon'hai Trio", 'Xarth')]


# --------------------------------------------------------------------------------------------
# HTTP with a file cache
# --------------------------------------------------------------------------------------------

class Wiki:
    def __init__(self, cache_dir, offline):
        self.cache_dir = cache_dir
        self.offline = offline
        os.makedirs(cache_dir, exist_ok=True)
        self.last_request = 0.0

    def get(self, key, params):
        path = os.path.join(self.cache_dir, re.sub(r'[^A-Za-z0-9._-]+', '_', key) + '.json')
        if os.path.exists(path):
            with open(path, encoding='utf-8') as f:
                return json.load(f)
        if self.offline:
            raise SystemExit('offline and not cached: ' + key)
        wait = 1.0 - (time.monotonic() - self.last_request)
        if wait > 0:
            time.sleep(wait)
        url = API + '?' + urllib.parse.urlencode(params)
        req = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.load(resp)
        self.last_request = time.monotonic()
        if 'error' in data:
            raise SystemExit('API error for %s: %s' % (key, data['error']))
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
        return data

    def pages(self, titles):
        """Return {title: (revid, timestamp, wikitext)} for the given titles."""
        out = {}
        for start in range(0, len(titles), 10):
            batch = titles[start:start + 10]
            params = {'action': 'query', 'format': 'json', 'formatversion': '2', 'prop': 'revisions',
                      'rvprop': 'ids|timestamp|content', 'rvslots': 'main', 'titles': '|'.join(batch)}
            data = self.get('pages_' + '|'.join(batch)[:150] + '_%d' % start, params)
            for page in data['query']['pages']:
                if 'revisions' not in page:
                    raise SystemExit('missing page: ' + page['title'])
                rev = page['revisions'][0]
                out[page['title']] = (rev['revid'], rev['timestamp'], rev['slots']['main']['content'])
        missing = [t for t in titles if t not in out]
        if missing:
            raise SystemExit('pages not returned: %s' % missing)
        return out

    def bucket(self, key, query):
        key = 'bucket_%s_%s' % (key[:40], hashlib.sha1(query.encode('utf-8')).hexdigest()[:12])
        data = self.get(key, {'action': 'bucket', 'format': 'json', 'query': query})
        if 'bucket' not in data:
            raise SystemExit('bucket query failed (%s): %s' % (key, data))
        return data['bucket']


def lua_str(s):
    if "'" in s:
        if '"' in s:
            raise SystemExit('cannot quote %r' % s)
        return '"' + s + '"'
    return "'" + s + "'"


# --------------------------------------------------------------------------------------------
# Wikitext parsing
# --------------------------------------------------------------------------------------------

def strip_comments(text):
    return re.sub(r'<!--.*?-->', '', text, flags=re.S)


def find_templates(text):
    """Yield (start, inner_text) for every top-level {{...}} template."""
    i, n = 0, len(text)
    while i < n:
        j = text.find('{{', i)
        if j < 0:
            return
        depth, k = 0, j
        while k < n:
            if text.startswith('{{', k):
                depth += 1
                k += 2
            elif text.startswith('}}', k):
                depth -= 1
                k += 2
                if depth == 0:
                    break
            else:
                k += 1
        yield j, text[j + 2:k - 2]
        i = k


def split_params(inner):
    """Split template text on top-level pipes. Returns (name, positional list, named dict)."""
    parts, buf, depth_t, depth_l, i = [], [], 0, 0, 0
    while i < len(inner):
        if inner.startswith('{{', i):
            depth_t += 1
            buf.append('{{')
            i += 2
        elif inner.startswith('}}', i):
            depth_t -= 1
            buf.append('}}')
            i += 2
        elif inner.startswith('[[', i):
            depth_l += 1
            buf.append('[[')
            i += 2
        elif inner.startswith(']]', i):
            depth_l -= 1
            buf.append(']]')
            i += 2
        elif inner[i] == '|' and depth_t == 0 and depth_l == 0:
            parts.append(''.join(buf))
            buf = []
            i += 1
        else:
            buf.append(inner[i])
            i += 1
    parts.append(''.join(buf))
    name = parts[0].strip()
    positional, named = [], {}
    for p in parts[1:]:
        m = re.match(r'^\s*([A-Za-z0-9_ ]+?)\s*=(.*)$', p, flags=re.S)
        if m:
            named[m.group(1).strip()] = m.group(2).strip()
        else:
            positional.append(p.strip())
    return name, positional, named


def headings(text):
    """Return a list of (pos, level, title)."""
    return [(m.start(), len(m.group(1)), m.group(2).strip())
            for m in re.finditer(r'^(=+)\s*(.*?)\s*\1\s*$', text, flags=re.M)]


def section_text(text, level, title):
    """Text of the first heading with this level and title, up to the next heading of <= level."""
    hs = headings(text)
    for idx, (pos, lvl, t) in enumerate(hs):
        if lvl == level and t == title:
            end = len(text)
            for pos2, lvl2, _ in hs[idx + 1:]:
                if lvl2 <= level:
                    end = pos2
                    break
            return text[pos:end]
    raise SystemExit('section not found: %s %s' % ('=' * level, title))


def templates_by_subsection(text):
    """Yield (subsection title, template name, positional, named) in document order."""
    hs = headings(text)
    for start, inner in find_templates(text):
        sub = None
        for pos, lvl, t in hs:
            if pos <= start:
                sub = t
        name, positional, named = split_params(inner)
        yield sub, name, positional, named


def parse_fraction(s):
    s = s.replace(',', '').strip()
    m = re.fullmatch(r'([\d.]+)\s*/\s*([\d.]+)', s)
    if not m:
        raise ValueError('not a rarity: %r' % s)
    return Fraction(m.group(1)) / Fraction(m.group(2))


def parse_quantity(s):
    s = re.sub(r'<ref[^>]*/>|<ref.*?</ref>', '', s, flags=re.S).strip()
    noted = '(noted)' in s
    s = s.replace('(noted)', '').strip().replace('–', '-').replace(',', '')
    m = re.fullmatch(r'(\d+)\s*-\s*(\d+)', s)
    if m:
        return int(m.group(1)), int(m.group(2)), noted
    m = re.fullmatch(r'(\d+)', s)
    if m:
        return int(s), int(s), noted
    raise ValueError('not a quantity: %r' % s)


# --------------------------------------------------------------------------------------------
# Sub-table definitions parsed from the wiki's own template/module sources
# --------------------------------------------------------------------------------------------

def parse_weighted_template(src, base_var):
    """Parse a *DropLines template: returns (outOf, [(name, weight, qty_factor)])."""
    m = re.search(r'#vardefine:%s\|\{\{#expr:\{\{\{1\}\}\}/(\d+)\}\}' % base_var, src)
    if not m:
        raise SystemExit('cannot find base for ' + base_var)
    out_of = int(m.group(1))
    rows = []
    # Template sources use {{{param}}} syntax, so split on the row start instead of matching braces.
    for inner in re.split(r'\{\{DropsLine', src)[1:]:
        nm = re.search(r'name=([^|]+)\|', inner)
        wt = re.search(r'\(([\d.]+)\*\{\{#var:%s\}\}\)' % base_var, inner)
        if not nm or not wt:
            raise SystemExit('cannot parse row in %s: %s' % (base_var, inner[:120]))
        qf = 1
        qm = re.search(r'#expr:(\d+)\*\{\{\{(?:2\|1|multiplier\|1)\}\}\}', inner)
        if qm:
            qf = int(qm.group(1))
        rows.append((nm.group(1).strip(), int(wt.group(1)), qf))
    return out_of, rows


def parse_general_seed_module(src):
    """Return sorted [(threshold, outOf, [(name, quantity, weight)])]."""
    groups = []
    for m in re.finditer(r'\[(\d+)\]\s*=\s*\{(.*?)\n\t\}', src, flags=re.S):
        threshold = int(m.group(1))
        rows, denom = [], None
        for r in re.finditer(r"item = '([^']+)', quantity = (\d+), rate = '(\d+)/(\d+)'", m.group(2)):
            rows.append((r.group(1), int(r.group(2)), int(r.group(3))))
            d = int(r.group(4))
            if denom not in (None, d):
                raise SystemExit('mixed denominators in general seed group %d' % threshold)
            denom = d
        groups.append((threshold, denom, rows))
    if len(groups) != 6:
        raise SystemExit('expected 6 general seed groups, got %d' % len(groups))
    return sorted(groups)


def general_seed_table(groups, level):
    """Flatten the level-dependent general seed drop table into [(name, qty, Fraction)]."""
    calc = level * 10
    prev, out = 0, []
    for threshold, denom, rows in groups:
        top = min(threshold, calc)
        if top <= prev:
            break
        share = Fraction(top - prev, calc)
        for name, qty, w in rows:
            out.append((name, qty, share * Fraction(w, denom)))
        prev = threshold
        if threshold >= calc:
            break
    return out


# --------------------------------------------------------------------------------------------
# Building
# --------------------------------------------------------------------------------------------

class Entry:
    def __init__(self, name, lo, hi, noted, prob):
        self.name, self.lo, self.hi, self.noted, self.prob = name, lo, hi, noted, prob


def drops_line_entry(named, boss_id, warnings, exact_refs=None):
    """exact_refs maps a named footnote to the exact rarity it states, so rows that only reuse the
    footnote (<ref name=x/> or {{Refn|name=x}}) get the same exact rarity."""
    name = named.get('name', '').strip()
    rarity = strip_comments(named.get('rarity', '')).strip()
    qty = named.get('quantity', '1')
    lo, hi, noted = parse_quantity(strip_comments(qty)) if name != 'Nothing' else (0, 0, False)
    if rarity == 'Always':
        prob = Fraction(1)
    else:
        prob = parse_fraction(rarity)
    notes = named.get('raritynotes', '')
    exact = re.search(r'exact rarity is ([\d/ *.]+?) per roll', notes)
    ref = re.search(r'name\s*=\s*"?([\w-]+)"?', notes)
    text = None
    if exact:
        text = exact.group(1).strip()
        if ref and exact_refs is not None:
            exact_refs[ref.group(1)] = text
    elif ref and exact_refs and ref.group(1) in exact_refs:
        text = exact_refs[ref.group(1)]
    if text:
        val = Fraction(1)
        for factor in text.split('*'):
            val *= parse_fraction(factor)
        warnings.append('%s: %s uses the exact rarity from its note (%s) instead of the rounded %s'
                        % (boss_id, name, text, rarity))
        prob = val
    if named.get('approx', '').lower() in ('yes', 'y', 'true'):
        warnings.append('%s: %s rarity %s is marked approximate on the wiki' % (boss_id, name, rarity))
    return Entry(name, lo, hi, noted, prob)


def build_boss(cfg, wikitext, subtables, gsdt_groups, notes, shared):
    drops = section_text(strip_comments(wikitext), 2, cfg['dropsHeading'])
    always, main, preroll, tertiary, tables = [], [], [], [], []
    rolls_seen = set()
    exact_refs = {}
    for sub, tname, pos, named in templates_by_subsection(drops):
        if tname in ('DropsLine', 'DropsLineClue'):
            if tname == 'DropsLineClue':
                named = dict(named, name=CLUE_NAMES[named['type'].strip().lower()], quantity='1')
            name = named['name'].strip()
            if name == 'Nothing':
                continue
            if name in EXCLUDED:
                notes['excluded'].append((cfg['id'], name, named.get('rarity', ''), EXCLUDED[name]))
                continue
            e = drops_line_entry(named, cfg['id'], notes['warnings'], exact_refs)
            kind = 'main'
            if sub in ALWAYS_SECTIONS or e.prob == 1:
                kind = 'always'
            elif sub in TERTIARY_SECTIONS:
                kind = 'tertiary'
                if sub == 'Pre-roll':
                    notes['warnings'].append('%s: %s is a wiki pre-roll (rolled before the main table); '
                                             'listed as tertiary' % (cfg['id'], name))
            elif sub in PER_ROLL_PREROLL_SECTIONS:
                kind = 'preroll'
            if name in MOVE_TO_TERTIARY.get(cfg['id'], set()) and kind == 'main':
                kind = 'tertiary'
                notes['warnings'].append('%s: %s is listed in the main table on the wiki but the other '
                                         'rows already fill it; treated as a separate roll' % (cfg['id'], name))
            if kind in ('main', 'preroll') and named.get('rolls'):
                rolls_seen.add(int(named['rolls']))
            {'always': always, 'main': main, 'preroll': preroll, 'tertiary': tertiary}[kind].append(e)
        elif tname == 'GemDropTable':
            tables.append(('gem_drop_table', parse_fraction(pos[0])))
            if named.get('chaostalisman') and named.get('naturetalisman'):
                notes['warnings'].append('%s: wiki lists both chaos and nature talisman for the gem table; '
                                         'the shared gem table uses %s' % (cfg['id'], GEM_TABLE_TALISMAN))
        elif tname == 'RareDropTable':
            pos = [p for p in pos if p]
            tables.append(('rare_drop_table', parse_fraction(pos[0])))
            if len(pos) > 1:
                tables.append(('gem_drop_table', parse_fraction(pos[1])))
            if named.get('rolls'):
                rolls_seen.add(int(named['rolls']))
        elif tname in ('HerbDropLines', 'UncommonSeedDropLines', 'RareSeedDropLines', 'TreeHerbSeedDropLines'):
            access = parse_fraction(pos[0])
            mult = int(pos[1]) if len(pos) > 1 and pos[1] else int(named.get('multiplier', '1') or 1)
            out_of, rows = subtables[tname]
            for name, w, qf in rows:
                main.append(Entry(name, qf * mult, qf * mult, False, access * Fraction(w, out_of)))
            notes['subtables'].append((cfg['id'], tname, pos[0], mult, out_of))
            # Tree-herb seed rows whose rarity is overridden with "main roll + sub-table" also have a
            # direct main-table roll; the override expression holds that direct rate.
            for key, val in named.items():
                m = re.fullmatch(r'(\w+?)SeedRarity', key)
                if m:
                    add = re.search(r'#expr:1/\((\d+/\d+)\+', val)
                    if not add:
                        raise SystemExit('cannot parse %s override: %s' % (key, val))
                    seed = m.group(1) + ' seed'
                    main.append(Entry(seed, 1, 1, False, parse_fraction(add.group(1))))
                    notes['warnings'].append('%s: %s also has a direct %s main-table roll (from the wiki\'s '
                                             '%s override)' % (cfg['id'], seed, add.group(1), key))
            if named.get('rolls'):
                rolls_seen.add(int(named['rolls']))
        elif tname == 'GeneralSeedDropLines':
            access = parse_fraction(pos[0])
            level = int(pos[1])
            key = 'general_seed_drop_table_lvl%d' % level
            shared[key] = [Entry(n, q, q, False, p) for n, q, p in general_seed_table(gsdt_groups, level)]
            tables.append((key, access))
            notes['subtables'].append((cfg['id'], tname, pos[0], level, None))
    split = SPLIT_SHARED_RATE.get(cfg['id'], set())
    if split:
        group = [e for e in tertiary if e.name in split]
        if len(group) != len(split) or len({e.prob for e in group}) != 1:
            raise SystemExit('%s: unexpected shared-rate rows %s' % (cfg['id'], [e.name for e in group]))
        for e in group:
            e.prob = e.prob / len(group)
        notes['warnings'].append('%s: %s share one %s roll; each is listed at %s' % (
            cfg['id'], ', '.join(sorted(split)), group[0].prob * len(group), group[0].prob))
    if len(rolls_seen) > 1:
        raise SystemExit('%s: inconsistent rolls %s' % (cfg['id'], rolls_seen))
    rolls = rolls_seen.pop() if rolls_seen else 1

    # Pre-roll made on every main roll: it replaces that roll's main-table drop when it hits.
    keep = 1 - sum((e.prob for e in preroll), Fraction(0))
    if preroll:
        main = preroll + [Entry(e.name, e.lo, e.hi, e.noted, e.prob * keep) for e in main]
        tables = [(t, w * keep) for t, w in tables]
        notes['warnings'].append('%s: %s are a %s pre-roll on each main roll; the rest of the main table is '
                                 'scaled by %s' % (cfg['id'], ', '.join(e.name for e in preroll),
                                                   sum(e.prob for e in preroll), keep))
    return {'always': always, 'main': main, 'tertiary': tertiary, 'tables': tables, 'rolls': rolls}


def build_shared_tables(rdt_text, gdt_text, notes):
    rdt_text, gdt_text = strip_comments(rdt_text), strip_comments(gdt_text)
    tables = {}

    def collect(text):
        entries, nested = [], []
        for sub, tname, pos, named in templates_by_subsection(text):
            if tname not in ('DropsLine', 'DropsLineReward'):
                continue
            name = named['name'].strip()
            if name == 'Nothing':
                continue
            if name == 'Gem drop table':
                nested.append(('gem_drop_table', parse_fraction(named['rarity'])))
                continue
            if name == 'Mega-rare drop table':
                nested.append(('mega_rare_drop_table', parse_fraction(named['rarity'])))
                continue
            if name in ('Chaos talisman', 'Nature talisman') and name != GEM_TABLE_TALISMAN:
                notes['warnings'].append('gem_drop_table: dropped %s (same slot as %s)' % (name, GEM_TABLE_TALISMAN))
                continue
            entries.append(drops_line_entry(named, 'shared', notes['warnings']))
        return entries, nested

    tables['rare_drop_table'] = collect(section_text(rdt_text, 2, 'Rare drop table'))
    tables['gem_drop_table'] = collect(section_text(gdt_text, 2, 'Gem Drop Table'))
    tables['mega_rare_drop_table'] = collect(section_text(gdt_text, 2, 'Mega-rare drop table'))
    return tables


# --------------------------------------------------------------------------------------------
# Building the bosses added on 2026-11-04 (NEW_BOSSES)
# --------------------------------------------------------------------------------------------

ROW_TEMPLATES = ('DropsLine', 'DropsLineClue', 'DropsLineReward')


class TableRoll:
    """A tertiary roll that, on success, rolls a shared table once."""

    def __init__(self, table, prob):
        self.table, self.prob = table, prob
        self.name = None


def row_entry(name, lo, hi, noted, prob, section=None, raw='', notes=''):
    e = Entry(name, lo, hi, noted, prob)
    e.section, e.raw, e.notes = section, raw, notes
    return e


def with_prob(e, prob):
    return row_entry(e.name, e.lo, e.hi, e.noted, prob, getattr(e, 'section', None), getattr(e, 'raw', ''),
                     getattr(e, 'notes', ''))


def nget(named, key, default=''):
    """Template parameter, ignoring case (Yama's rows use Rarity= and Quantity=)."""
    for k, v in named.items():
        if k.lower() == key:
            return v
    return default


def eval_arith(s):
    """Evaluate + - * / and parentheses exactly, as a Fraction."""
    s = s.replace(',', '')
    tokens = re.findall(r'\d+(?:\.\d+)?|\.\d+|[-+*/()]', s)
    if ''.join(tokens) != re.sub(r'\s+', '', s):
        raise ValueError('cannot evaluate %r' % s)
    pos = [0]

    def peek():
        return tokens[pos[0]] if pos[0] < len(tokens) else None

    def take():
        pos[0] += 1
        return tokens[pos[0] - 1]

    def expr():
        v = term()
        while peek() in ('+', '-'):
            v = v + term() if take() == '+' else v - term()
        return v

    def term():
        v = factor()
        while peek() in ('*', '/'):
            v = v * factor() if take() == '*' else v / factor()
        return v

    def factor():
        t = take()
        if t == '(':
            v = expr()
            if take() != ')':
                raise ValueError('unbalanced %r' % s)
            return v
        if t == '-':
            return -factor()
        return Fraction(t)

    v = expr()
    if pos[0] != len(tokens):
        raise ValueError('cannot evaluate %r' % s)
    return v


def expand_wikiexpr(s, variables):
    """Replace {{#var:x}} and {{#expr:...}} with exact values. "round N" is dropped, so a rarity the Wiki
    shows rounded (1/{{#expr:180/(...) round 1}}) comes out exact."""
    for _ in range(200):
        m = re.search(r'\{\{#var:([^{}|]+)\}\}', s)
        if m:
            s = s[:m.start()] + '(' + variables[m.group(1).strip()] + ')' + s[m.end():]
            continue
        m = re.search(r'\{\{#expr:([^{}]*)\}\}', s)
        if m:
            v = eval_arith(re.sub(r'\bround\s+\d+', '', m.group(1)))
            s = s[:m.start()] + '(%d/%d)' % (v.numerator, v.denominator) + s[m.end():]
            continue
        return s
    raise ValueError('expression too deep: %r' % s)


def eval_rarity(raw, variables):
    s = re.sub(r'<ref[^>]*/>|<ref.*?</ref>', '', strip_comments(raw), flags=re.S).strip()
    if s == 'Always':
        return Fraction(1)
    return eval_arith(expand_wikiexpr(s, variables))


def parse_quantity_v2(s, pairs):
    """DT2 bosses list "normal,perfect" quantities (perfect kills give 50% more); the first is used."""
    s = re.sub(r'<ref[^>]*/>|<ref.*?</ref>', '', strip_comments(s), flags=re.S).strip()
    if pairs:
        noted = '(noted)' in s
        s = re.split(r'\s*[,;]\s*', s.replace('(noted)', '').strip())[0] + (' (noted)' if noted else '')
    return parse_quantity(s or '1')


def excluded_row(cfg, name, rarity):
    for nm, rar, reason in list(cfg.get('exclude', [])) + NEW_EXCLUDED_ROWS:
        if nm == name and (rar is None or rar == rarity.strip()):
            return reason
    if name in EXCLUDED:
        return EXCLUDED[name]
    return None


def section_role_v2(cfg, sub):
    roles = cfg.get('sections', {})
    if sub in roles:
        return roles[sub]
    if sub in ALWAYS_SECTIONS:
        return 'always'
    if sub in TERTIARY_SECTIONS or sub == 'Tertiary drops':
        return 'tertiary'
    if sub == 'Wilderness Slayer tertiary':
        return 'skip'
    return 'main'


def multiplier_range(text):
    m = re.fullmatch(r'\s*(\d+)\s*(?:-\s*(\d+))?\s*', text or '1')
    if not m:
        raise SystemExit('cannot read multiplier %r' % text)
    return int(m.group(1)), int(m.group(2) or m.group(1))


def fossil_rows(src, named, section):
    """Template:FossilDropLines, evaluated for its fossil / numulite / access parameters."""
    fossil = int(named.get('fossil') or 1)
    numulite = int(named.get('numulite') or 10)
    access = parse_fraction(named['access']) if named.get('access') else Fraction(1)
    rows = []
    for inner in re.split(r'\{\{DropsLine', src)[1:]:
        nm = re.search(r'\|name=([^|]+)\|', inner).group(1).strip()
        den = re.search(r'\{\{\{access\|1\}\}\}\)\*(\d+) round 2', inner)
        if not den:
            raise SystemExit('cannot read FossilDropLines row %s' % nm)
        num = 9 * fossil if '{{#expr:9*{{{fossil|1}}}}}' in inner else fossil
        lo, hi = (5, numulite + 4 if numulite > 1 else 5) if nm == 'Numulite' else (1, 1)
        rows.append(row_entry(nm, lo, hi, False, Fraction(num) * access / int(den.group(1)), section))
    return rows


def build_part_v2(cfg, ctx, page, heading):
    """Rows of one Wiki drops section, sorted into always / main / tertiary / shared-table rolls."""
    notes, bid = ctx['notes'], cfg['id']
    text = re.sub(r'</?onlyinclude>', '', strip_comments(ctx['pages'][page][2]))
    drops = section_text(text, 2, heading)
    variables = {}
    rows = []  # (role, Entry)
    tables = []  # (shared table, chance per roll)
    rolls_seen = set()
    exact_refs = {}

    def add_row(sub, role, tname, named):
        if tname == 'DropsLineClue':
            named = dict(named, name=CLUE_NAMES[named['type'].strip().lower()], quantity='1')
        name = nget(named, 'name').strip()
        if not name or name == 'Nothing':
            return
        raw = strip_comments(nget(named, 'rarity')).strip()
        if role == 'skip':
            notes['skipped'].append((bid, name, raw, 'section "%s" is not part of a standard kill' % sub))
            return
        reason = excluded_row(cfg, name, raw)
        if reason:
            notes['excluded'].append((bid, name, raw, reason))
            return
        if cfg.get('membersOnly') and '{{(f)}}' in nget(named, 'namenotes'):
            notes['skipped'].append((bid, name, raw, "free-to-play worlds only (members' table used)"))
            return
        try:
            prob = eval_rarity(raw, variables)
        except (ValueError, KeyError):
            if cfg.get('numericOnly'):
                notes['skipped'].append((bid, name, raw, 'the Wiki gives no numeric rate'))
                return
            raise SystemExit('%s: cannot read rarity %r of %s' % (bid, raw, name))
        rnotes = nget(named, 'raritynotes')
        exact = re.search(r'exact rarity is ([\d/ *.]+?) per roll', rnotes)
        if exact:
            prob = eval_arith(exact.group(1))
            notes['warnings'].append('%s: %s uses the exact rarity from its note (%s)' % (bid, name, exact.group(1)))
        lo, hi, noted = parse_quantity_v2(nget(named, 'quantity', '1'), cfg.get('quantityPairs'))
        if nget(named, 'approx').lower() in ('yes', 'y', 'true'):
            notes['warnings'].append('%s: %s rarity %s is marked approximate on the Wiki' % (bid, name, raw))
        if nget(named, 'rolls') and role in ('main', 'perRollPreroll'):
            rolls_seen.add(int(nget(named, 'rolls')))
        if name in DROP_NAME_ALIASES:
            notes['warnings'].append('%s: Wiki row %s is stored as %s' % (bid, name, DROP_NAME_ALIASES[name]))
            name = DROP_NAME_ALIASES[name]
        rows.append((role, row_entry(name, lo, hi, noted, prob, sub, raw, rnotes)))

    for sub, tname, pos, named in templates_by_subsection(drops):
        role = section_role_v2(cfg, sub)
        if tname.startswith('#vardefine:'):
            variables[tname.split(':', 1)[1].strip()] = pos[0] if pos else ''
        elif tname in ROW_TEMPLATES:
            add_row(sub, role, tname, named)
        elif tname.startswith('Uniques/'):
            src = re.sub(r'</?includeonly>', '', strip_comments(ctx['pages']['Template:' + tname][2]))
            for _, tn2, _, named2 in templates_by_subsection(src):
                if tn2 in ROW_TEMPLATES:
                    add_row(sub, role, tn2, named2)
            notes['subtables'].append((bid, tname, sub, '-', '-'))
        elif tname == 'WildernessSlayerDropTable':
            notes['excluded'].append((bid, "Larran's key, Slayer's enchantment", '-',
                                      'Wilderness Slayer table: only on a Krystilia task'))
        elif role == 'skip':
            continue
        elif tname == 'GemDropTable':
            tables.append(('gem_drop_table', parse_fraction(pos[0])))
            if named.get('rolls') and int(named['rolls']) not in rolls_seen | {1}:
                notes['warnings'].append('%s: the Wiki rolls the gem drop table %s times when its %s slot hits; '
                                         'here the slot rolls it once' % (bid, named['rolls'], pos[0]))
            if named.get('chaostalisman') and named.get('naturetalisman'):
                notes['warnings'].append('%s: wiki lists both chaos and nature talisman for the gem table; '
                                         'the shared gem table uses %s' % (bid, GEM_TABLE_TALISMAN))
        elif tname == 'RareDropTable':
            p = [x for x in pos if x]
            suffix = ''
            if named.get('multiplier'):
                suffix = '_x%s' % named['multiplier']
                ctx['multiplied'].add(int(named['multiplier']))
                notes['warnings'].append('%s: rolls rare_drop_table%s, which doubles every quantity on the rare, gem '
                                         'and mega-rare tables ("%s")' % (bid, suffix, named.get('override', '')))
            tables.append(('rare_drop_table' + suffix, parse_fraction(p[0])))
            if len(p) > 1:
                tables.append(('gem_drop_table', parse_fraction(p[1])))
            if named.get('rolls'):
                rolls_seen.add(int(named['rolls']))
            if named.get('chaostalisman') and named.get('naturetalisman'):
                notes['warnings'].append('%s: wiki lists both chaos and nature talisman for the rare drop table; '
                                         'the shared gem table uses %s' % (bid, GEM_TABLE_TALISMAN))
        elif tname in ('HerbDropLines', 'UncommonSeedDropLines', 'RareSeedDropLines', 'TreeHerbSeedDropLines',
                       'TalismanDropLines'):
            access = parse_fraction(pos[0])
            lo_m, hi_m = multiplier_range(pos[1] if len(pos) > 1 and pos[1] else named.get('multiplier', '1'))
            out_of, srows = ctx['subtables'][tname]
            for name, w, qf in srows:
                lo, hi = qf * lo_m, qf * hi_m
                if name == 'Watermelon seed' and named.get('OverrideWatermelonQuantity'):
                    lo, hi, _ = parse_quantity(named['OverrideWatermelonQuantity'])
                rows.append(('main', row_entry(name, lo, hi, False, access * Fraction(w, out_of), sub)))
            notes['subtables'].append((bid, tname, pos[0], named.get('multiplier', pos[1] if len(pos) > 1 else 1),
                                       out_of))
            for key, val in named.items():
                m = re.fullmatch(r'(\w+?)SeedRarity', key)
                if m:
                    add = re.search(r'#expr:1/\((\d+/\d+)\+', val)
                    if not add:
                        raise SystemExit('cannot parse %s override: %s' % (key, val))
                    rows.append(('main', row_entry(m.group(1) + ' seed', 1, 1, False, parse_fraction(add.group(1)), sub)))
            if named.get('rolls'):
                rolls_seen.add(int(named['rolls']))
            if named.get('approx'):
                notes['warnings'].append('%s: %s is marked approximate on the Wiki' % (bid, tname))
        elif tname == 'GWDRDT':
            for t, p in ctx['gwd']['access']:
                tables.append((t, p))
            notes['subtables'].append((bid, 'GWDRDT', '/'.join(str(p) for _, p in ctx['gwd']['access']), '-', '-'))
        elif tname == 'FossilDropLines':
            for e in fossil_rows(ctx['pages']['Template:FossilDropLines'][2], named, sub):
                rows.append((role, e))
            notes['subtables'].append((bid, tname, sub, named.get('fossil'), named.get('numulite')))
    return rows, tables, rolls_seen


def check_listed(bid, e, exact, notes):
    """The Wiki shows chain rates rounded (1/25.8); check they round from the exact chain value."""
    m = re.fullmatch(r'1/([\d.]+)', e.raw.replace(',', ''))
    if not m:
        notes['warnings'].append('%s: %s has rarity %r; replaced by the exact chain rate %s' % (bid, e.name, e.raw, exact))
        return
    shown = m.group(1)
    decimals = len(shown.split('.')[1]) if '.' in shown else 0
    if abs(float(1 / exact) - float(shown)) > 0.5 * 10 ** -decimals + 1e-9:
        notes['warnings'].append('%s: %s is 1/%s on the Wiki but the chain order gives 1/%.4f; the chain value '
                                 'is used' % (bid, e.name, shown, 1 / exact))


def build_chain(cfg, main, notes):
    """DT2 bosses and Yama: "a drop is rolled for until the roll results in a success". Each step only happens
    if the earlier ones fail, so it is a pre-roll made on the single main roll (rolls = 1)."""
    bid = cfg['id']
    item_names = {key for kind, key, _ in cfg['chain'] if kind == 'item'}
    chain_sections = {key for kind, key, _ in cfg['chain'] if kind != 'item'}
    consumed, out, companions = set(), [], []
    reach = Fraction(1)
    for kind, key, rate in cfg['chain']:
        step = reach * rate
        if kind == 'item':
            found = [e for e in main if e.name == key]
            if len(found) != 1:
                raise SystemExit('%s: chain item %s found %d times' % (bid, key, len(found)))
            check_listed(bid, found[0], step, notes)
            out.append(with_prob(found[0], step))
            consumed.add(id(found[0]))
        else:
            rs = [e for e in main if e.section == key and e.name not in item_names]
            if not rs:
                raise SystemExit('%s: chain section %s is empty' % (bid, key))
            if kind == 'section':
                total = sum((e.prob for e in rs), Fraction(0))
                if total > step:
                    raise SystemExit('%s: %s rows add up to %s, more than the step chance %s' % (bid, key, total, step))
                if total < step:
                    notes['warnings'].append('%s: %s rows add up to %s of the %s step; the rest (%s) is nothing' % (
                        bid, key, total, step, step - total))
                out.extend(rs)
            else:
                for e in rs:
                    check_listed(bid, e, step, notes)
                out.append(with_prob(rs[0], step))
                companions.extend(with_prob(e, step) for e in rs[1:])
            consumed.update(id(e) for e in rs)
        notes['warnings'].append('%s: chain step %s %s: %s per kill' % (bid, kind, key, step))
        reach *= 1 - rate
    denoms = cfg.get('standardDenominators', {})
    standard, std_companions = [], []
    for e in main:
        if id(e) in consumed or e.section in chain_sections:
            continue
        p = e.prob
        m = re.fullmatch(r'\s*([\d.]+)\s*/\s*([\d.]+)\s*', e.raw)
        if m and m.group(2) in denoms:
            if abs(denoms[m.group(2)] / float(reach) - float(m.group(2))) > 0.005:
                raise SystemExit('%s: %s does not match %s / %.5f' % (bid, m.group(2), denoms[m.group(2)], reach))
            p = Fraction(m.group(1)) / denoms[m.group(2)]
        target = std_companions if e.name in cfg.get('companions', set()) else standard
        target.append(with_prob(e, p * reach))
    notes['warnings'].append('%s: standard table reached with chance %s (%.4f); its rows are scaled by that' % (
        bid, reach, reach))
    return out + standard, companions + std_companions


def register_shared(ctx, name, entries, nested=()):
    sig = ([(e.name, e.lo, e.hi, e.noted, e.prob) for e in entries], list(nested))
    if name in ctx['shared_v2']:
        if ctx['shared_sig'][name] != sig:
            raise SystemExit('shared table %s defined twice with different contents' % name)
        return
    ctx['shared_v2'][name] = (entries, list(nested))
    ctx['shared_sig'][name] = sig


def build_part_tables(cfg, ctx, rows, tables, rolls_seen):
    notes, bid = ctx['notes'], cfg['id']
    for group in cfg.get('split', []):
        members = [e for _, e in rows if e.name in group]
        if len(members) != len(group) or len({e.prob for e in members}) != 1:
            raise SystemExit('%s: unexpected shared-rate rows %s' % (bid, [(e.name, e.prob) for e in members]))
        for e in members:
            e.prob = e.prob / len(group)
        notes['warnings'].append('%s: %s share one %s rate; each is listed at %s' % (
            bid, ', '.join(group), members[0].prob * len(group), members[0].prob))
    always = [e for role, e in rows if role == 'always' or (role in ('main', 'tertiary') and e.prob == 1)]
    tertiary = [e for role, e in rows if role == 'tertiary' and e.prob != 1]
    preroll = [e for role, e in rows if role == 'perRollPreroll']
    main = [e for role, e in rows if role == 'main' and e.prob != 1]
    table_rows = {}
    for role, e in rows:
        if isinstance(role, tuple):
            table_rows.setdefault(role[1], []).append(e)

    if cfg.get('chain'):
        main, companions = build_chain(cfg, main, notes)
        rolls = 1
    else:
        companions = [e for e in main if e.name in cfg.get('companions', set())]
        main = [e for e in main if e.name not in cfg.get('companions', set())]
        if len(rolls_seen) > 1:
            raise SystemExit('%s: inconsistent rolls %s' % (bid, rolls_seen))
        rolls = rolls_seen.pop() if rolls_seen else 1

    # GWD bosses: the Wiki's coin row "from all loot tables, including GDT and RDT" adds the coins the GWD
    # rare and gem tables give. Those tables are shared tables here, so their coins come off the row.
    for e in main:
        if e.name == 'Coins' and 'GDT and RDT' in getattr(e, 'notes', ''):
            listed = e.prob
            left = (listed - ctx['gwd']['coins']) * 508
            snapped = Fraction(round(left), 508)
            if abs(left - round(left)) > Fraction(1, 4):
                raise SystemExit('%s: GWD coin row %s does not leave a clean main-table weight' % (bid, e.raw))
            e.prob = snapped
            notes['warnings'].append('%s: coin row %s includes the GWD rare/gem tables\' coins (%.5f/127); the main '
                                     'table keeps %s/127' % (bid, e.raw, float(ctx['gwd']['coins'] * 127),
                                                             snapped * 127))

    if preroll:
        keep = 1 - sum((e.prob for e in preroll), Fraction(0))
        main = preroll + [with_prob(e, e.prob * keep) for e in main]
        companions = [with_prob(e, e.prob * keep) for e in companions]
        tables = [(t, w * keep) for t, w in tables]
        notes['warnings'].append('%s: %s are a %s pre-roll on each main roll; the rest of the main table is '
                                 'scaled by %s' % (bid, ', '.join(e.name for e in preroll),
                                                   sum(e.prob for e in preroll), keep))
    total = sum((e.prob for e in main), Fraction(0)) + sum((w for _, w in tables), Fraction(0))
    if total > 1:
        if not cfg.get('normalizeMain'):
            raise SystemExit('%s: main table adds up to %s' % (bid, total))
        main = [with_prob(e, e.prob / total) for e in main]
        companions = [with_prob(e, e.prob / total) for e in companions]
        tables = [(t, w / total) for t, w in tables]
        notes['warnings'].append('%s: the Wiki\'s main-table rows add up to %s (%.4f); each is divided by that' % (
            bid, total, total))
    for e in companions:
        p = 1 - (1 - e.prob) ** rolls
        tertiary.append(with_prob(e, p))
        notes['warnings'].append('%s: %s drops together with another item of its slot; listed as a separate %s '
                                 'roll (same chance per kill)' % (bid, e.name, p))
    for name, rs in table_rows.items():
        access = sum((e.prob for e in rs), Fraction(0))
        register_shared(ctx, name, [with_prob(e, e.prob / access) for e in rs])
        tertiary.append(TableRoll(name, access))
        notes['warnings'].append('%s: section "%s" is one %s roll on shared table %s' % (
            bid, rs[0].section, access, name))
    return {'always': always, 'main': main, 'tables': tables, 'tertiary': tertiary, 'rolls': rolls}


def merge_parts_v2(cfg, parts, weights, notes):
    """Royal Titans: the player loots one titan. Each titan is weighted by its share."""
    rolls = {p['rolls'] for p in parts}
    if len(rolls) != 1:
        raise SystemExit('%s: titans roll a different number of times' % cfg['id'])
    out = {'always': [], 'main': [], 'tables': [], 'tertiary': [], 'rolls': rolls.pop()}
    for key in ('always', 'main', 'tertiary'):
        merged = {}
        for part, w in zip(parts, weights):
            for e in part[key]:
                k = ('table', e.table) if isinstance(e, TableRoll) else (e.name, e.lo, e.hi, e.noted)
                p = e.prob if key == 'always' else e.prob * w
                if k in merged:
                    merged[k].prob = merged[k].prob if key == 'always' else merged[k].prob + p
                else:
                    merged[k] = TableRoll(e.table, p) if isinstance(e, TableRoll) else with_prob(e, p)
        out[key] = list(merged.values())
    tables = {}
    for part, w in zip(parts, weights):
        for t, p in part['tables']:
            tables[t] = tables.get(t, Fraction(0)) + p * w
    out['tables'] = list(tables.items())
    notes['warnings'].append('%s: the player loots one of %d pages, each weighted %s' % (
        cfg['id'], len(parts), ', '.join(str(w) for w in weights)))
    return out


def per_kill(b, name):
    """Chance per kill that a boss drops the named item from its own rows."""
    p_main = sum((e.prob for e in b['main'] if e.name == name), Fraction(0))
    miss = (1 - p_main) ** b['rolls']
    for e in b['tertiary']:
        if not isinstance(e, TableRoll) and e.name == name:
            miss *= 1 - e.prob
    return 1 - miss


def build_boss_v2(cfg, ctx):
    sources = cfg.get('merge') or [(cfg['page'], cfg.get('dropsHeading', 'Drops'), Fraction(1))]
    parts = []
    for page, heading, _ in sources:
        rows, tables, rolls_seen = build_part_v2(cfg, ctx, page, heading)
        parts.append(build_part_tables(cfg, ctx, rows, tables, rolls_seen))
    b = parts[0] if len(parts) == 1 else merge_parts_v2(cfg, parts, [w for _, _, w in sources], ctx['notes'])
    for item, table in cfg.get('opens', {}).items():
        p = per_kill(b, item)
        if p == 0:
            raise SystemExit('%s: %s opens %s but never drops' % (cfg['id'], item, table))
        build_open_table(ctx, table)
        b['tertiary'].append(TableRoll(table, p))
        ctx['notes']['warnings'].append('%s: using a %s (%s per kill) rolls shared table %s; it is a separate '
                                        'tertiary roll at the same chance' % (cfg['id'], item, p, table))
    return b


def build_open_table(ctx, name):
    page, heading, split = OPEN_TABLES[name]
    text = strip_comments(ctx['pages'][page][2])
    entries = []
    for sub, tname, pos, named in templates_by_subsection(section_text(text, 2, heading)):
        if tname not in ROW_TEMPLATES or nget(named, 'name').strip() in ('', 'Nothing'):
            continue
        lo, hi, noted = parse_quantity(nget(named, 'quantity', '1'))
        entries.append(row_entry(nget(named, 'name').strip(), lo, hi, noted, parse_fraction(nget(named, 'rarity'))))
    for group in split:
        members = [e for e in entries if e.name in group]
        if len(members) != len(group) or len({e.prob for e in members}) != 1:
            raise SystemExit('%s: unexpected shared-rate rows' % name)
        for e in members:
            e.prob = e.prob / len(group)
        ctx['notes']['warnings'].append('%s: %s share one %s rate; each is listed at %s' % (
            name, ', '.join(group), members[0].prob * len(group), members[0].prob))
    register_shared(ctx, name, entries)


def build_gwd_tables(ctx):
    """The God Wars Dungeon generals' own rare, gem and mega-rare tables, and how their bosses reach them."""
    text = strip_comments(ctx['pages']['God Wars Dungeon-variant rare drop table'][2])
    links = {'God Wars Dungeon-variant Gem Drop Table': 'gwd_gem_drop_table',
             'God Wars Dungeon-variant Mega-rare Drop Table': 'gwd_mega_rare_drop_table'}
    sections = [('gwd_rare_drop_table', 'God Wars Dungeon-variant Rare Drop Table'),
                ('gwd_gem_drop_table', 'God Wars Dungeon-variant Gem Drop Table'),
                ('gwd_mega_rare_drop_table', 'God Wars Dungeon-variant Mega-rare Drop Table')]
    built = {}
    for key, title in sections:
        entries, nested = [], []
        for sub, tname, pos, named in templates_by_subsection(section_text(text, 2, title)):
            if tname != 'DropsLine':
                continue
            name = named['name'].strip()
            if name in links:
                nested.append((links[name], parse_fraction(named['rarity'])))
                continue
            lo, hi, noted = parse_quantity(named.get('quantity', '1'))
            entries.append(row_entry(name, lo, hi, noted, parse_fraction(named['rarity'])))
        built[key] = (entries, nested)
        register_shared(ctx, key, entries, nested)
    tmpl = strip_comments(ctx['pages']['Template:GWDRDT'][2])
    m = re.search(r"There is an? (\d+/\d+) chance of rolling the \[\[God Wars Dungeon-variant rare drop table#"
                  r"[^]]*\]\] and an? (\d+/\d+) chance of rolling", tmpl)
    if not m:
        raise SystemExit('cannot read the GWD table chances from Template:GWDRDT')
    access = [('gwd_rare_drop_table', parse_fraction(m.group(1))), ('gwd_gem_drop_table', parse_fraction(m.group(2)))]

    def item_prob(table, name):
        entries, nested = built[table]
        return sum((e.prob for e in entries if e.name == name), Fraction(0)) + \
            sum((p * item_prob(t, name) for t, p in nested), Fraction(0))

    coins = sum((p * item_prob(t, 'Coins') for t, p in access), Fraction(0))
    # Cross-check every row of Template:GWDRDT (the Wiki's own flattened rates) against these tables, using
    # Template:CalculateRDTNaked's formula: gem table G = R*20/128 + G_chance, mega-rare M = R*15/128 + G/128.
    calc_src = re.sub(r'\s+', '', ctx['pages']['Template:CalculateRDTNaked'][2])
    for part in ('{{{R_chance|0}}}*20/128+{{{G_chance|0}}}', '{{{R_chance|0}}}*15/128+{{#var:G}}/128',
                 '{{{R_chance|0}}}*{{{R_rate|0}}}/128+{{#var:G}}*{{{G_rate|0}}}/128+{{#var:M}}*{{{M_rate|0}}}/128'):
        if part not in calc_src:
            raise SystemExit('Template:CalculateRDTNaked no longer has %s' % part)
    r_c, g_c = access[0][1], access[1][1]
    for _, tname, _, named in templates_by_subsection(tmpl):
        if tname != 'DropsLine':
            continue
        calc = re.search(r'\{\{CalculateRDTNaked\|(.*?)\}\}', named.get('rarity', ''))
        params = dict(kv.split('=') for kv in calc.group(1).split('|'))
        if parse_fraction(params['R_chance']) != r_c or parse_fraction(params['G_chance']) != g_c:
            raise SystemExit('GWDRDT row %s uses other table chances' % named['name'])
        g = r_c * Fraction(20, 128) + g_c
        mm = r_c * Fraction(15, 128) + g / 128
        expect = (r_c * int(params.get('R_rate', 0)) + g * int(params.get('G_rate', 0)) +
                  mm * int(params.get('M_rate', 0))) / 128
        got = sum((p * item_prob(t, named['name'].strip()) for t, p in access), Fraction(0))
        if expect != got:
            ctx['notes']['warnings'].append('GWDRDT: %s is %s from the tables but %s from the template' % (
                named['name'], got, expect))
    return {'access': access, 'coins': coins}


def multiplied_tables(shared_raw, mult):
    """Copies of the rare, gem and mega-rare tables with every quantity multiplied."""
    out = {}
    for key in ('rare_drop_table', 'gem_drop_table', 'mega_rare_drop_table'):
        entries, nested = shared_raw[key]
        out['%s_x%d' % (key, mult)] = ([Entry(e.name, e.lo * mult, e.hi * mult, e.noted, e.prob) for e in entries],
                                       [('%s_x%d' % (t, mult), w) for t, w in nested])
    return out


def infobox_monsters(text):
    text = strip_comments(text)
    out = []
    for m in re.finditer(r'\{\{\s*Infobox Monster', text, flags=re.I):
        for _, inner in find_templates(text[m.start():]):
            out.append(split_params(inner)[2])
            break
    return out


def infobox_number(ib, key, version):
    v = ib.get('%s%d' % (key, version), '') if version is not None else ''
    v = v.strip() or ib.get(key, '')
    m = re.match(r'\s*([\d,]+)', v)
    if not m:
        raise SystemExit('no %s in infobox %s (version %s)' % (key, ib.get('name'), version))
    return int(m.group(1).replace(',', ''))


def boss_stats(cfg, pages):
    """Hitpoints (summed over every NPC that must be killed) and the highest combat level in the fight."""
    st = cfg.get('stats', {})
    ver = st.get('version')
    hp_parts = st.get('hp') or [(cfg['page'], 0, ver, 1)]
    combat_parts = st.get('combat') or [(cfg['page'], 0, ver)]
    hp = sum(infobox_number(infobox_monsters(pages[p][2])[i], 'hitpoints', v) * n for p, i, v, n in hp_parts)
    combat = max(infobox_number(infobox_monsters(pages[p][2])[i], 'combat', v) for p, i, v in combat_parts)
    desc = ' + '.join('%s%s %d' % ('%d x ' % n if n > 1 else '', (lambda ib: ib.get('name') or ib.get('name1') or p)(
        infobox_monsters(pages[p][2])[i]), infobox_number(infobox_monsters(pages[p][2])[i], 'hitpoints', v))
        for p, i, v, n in hp_parts)
    if 'hpOverride' in st:
        hp_value, reason = st['hpOverride']
        desc = '%s (infobox says %d)' % (reason, hp)
        hp = hp_value
    return hp, combat, desc


def fit_int_fraction(frac, label, notes):
    if frac.numerator <= INT_MAX and frac.denominator <= INT_MAX:
        return frac
    approx = frac.limit_denominator(INT_MAX)
    notes['warnings'].append('%s: exact chance %.6g needs a denominator over 2^31; stored as %d/%d '
                             '(relative error %.1g)' % (label, frac, approx.numerator, approx.denominator,
                                                        abs(float((approx - frac) / frac))))
    return approx


# --------------------------------------------------------------------------------------------
# Building the reward-chest, wave and reward-pool bosses (CHEST_BOSSES)
# --------------------------------------------------------------------------------------------

def page_text(ctx, page):
    return re.sub(r'</?onlyinclude>', '', strip_comments(ctx['pages'][page][2]))


def run_checks(cfg, ctx):
    for page, regex in cfg.get('checks', []):
        if not re.search(regex, page_text(ctx, page)):
            raise SystemExit('%s: the Wiki no longer says /%s/ on %s' % (cfg['id'], regex, page))


def chest_rows(cfg, ctx, text, section=None):
    """Every drop row in text, as Entries with .section, .raw, .notes, .rolls and .alt. A rarity the Wiki gives
    no number for ("Varies") has prob None."""
    notes, bid = ctx['notes'], cfg['id']
    out = []
    for sub, tname, pos, named in templates_by_subsection(text):
        if tname not in ROW_TEMPLATES:
            continue
        if tname == 'DropsLineClue':
            named = dict(named, name=CLUE_NAMES[named['type'].strip().lower()], quantity='1')
        name = nget(named, 'name').strip()
        if not name or name == 'Nothing':
            continue
        raw = strip_comments(nget(named, 'rarity')).strip()
        reason = excluded_row(cfg, name, raw)
        if reason:
            notes['excluded'].append((bid, name, raw, reason))
            continue
        try:
            prob = eval_rarity(raw, {})
        except (ValueError, KeyError):
            prob = None
        lo, hi, noted = parse_quantity_v2(nget(named, 'quantity', '1'), False)
        if name in CHEST_NAME_ALIASES:
            notes['warnings'].append('%s: Wiki row %s is stored as %s' % (bid, name, CHEST_NAME_ALIASES[name]))
            name = CHEST_NAME_ALIASES[name]
        e = row_entry(name, lo, hi, noted, prob, section or sub, raw, nget(named, 'raritynotes'))
        e.rolls = int(nget(named, 'rolls') or 0)
        e.alt = strip_comments(nget(named, 'altrarity')).strip()
        out.append(e)
    return out


def chest_role(cfg, e):
    return cfg.get('rowRoles', {}).get(e.name) or section_role_v2(cfg, e.section)


def rows_boss(cfg, ctx, entries):
    """Sort rows into always / main / tertiary / shared tables (build_part_tables), then apply the setup."""
    notes, bid = ctx['notes'], cfg['id']
    pairs, rolls_seen = [], set()
    for e in entries:
        role = chest_role(cfg, e)
        if role == 'special':
            continue
        if role == 'skip':
            notes['skipped'].append((bid, e.name, e.raw, 'section "%s" is not part of the standard run' % e.section))
            continue
        if e.prob is None:
            raise SystemExit('%s: %s has no numeric rarity (%r)' % (bid, e.name, e.raw))
        if e.name in cfg.get('quantities', {}):
            lo, hi = cfg['quantities'][e.name]
            notes['warnings'].append('%s: %s quantity %s-%s on the Wiki; this setup gives %s-%s' % (
                bid, e.name, e.lo, e.hi, lo, hi))
            e.lo, e.hi = lo, hi
        if role in ('main', 'perRollPreroll') and e.rolls:
            rolls_seen.add(e.rolls)
        pairs.append((role, e))
    for role, want in cfg.get('expectTotal', {}).items():
        got = sum((e.prob for r, e in pairs if r == role), Fraction(0))
        if got != want:
            raise SystemExit('%s: %s rows add up to %s, not the Wiki\'s %s' % (bid, role, got, want))
    if 'rolls' in cfg:
        if rolls_seen and rolls_seen != {cfg['rolls']}:
            notes['warnings'].append('%s: the Wiki lists the rows at %s rolls (another setup); this setup rolls %d' % (
                bid, '/'.join(str(r) for r in sorted(rolls_seen)), cfg['rolls']))
        rolls_seen = {cfg['rolls']}
    b = build_part_tables(cfg, ctx, pairs, [], rolls_seen)
    main_total = sum((e.prob for e in b['main']), Fraction(0)) + sum((w for _, w in b['tables']), Fraction(0))
    if b['main'] and main_total < 1:
        notes['warnings'].append('%s: main rows add up to %s; the other %s is nothing' % (bid, main_total, 1 - main_total))
    for i, e in enumerate(b['tertiary']):
        if not isinstance(e, TableRoll) and e.name in cfg.get('perRollTertiary', set()):
            p = 1 - (1 - e.prob) ** b['rolls']
            b['tertiary'][i] = with_prob(e, p)
            notes['warnings'].append('%s: %s is %s per roll, at most once per run: %s over %d rolls (1/%.2f)' % (
                bid, e.name, e.prob, p, b['rolls'], 1 / p))
    for item, page, regex in cfg.get('always', []):
        m = re.search(regex, page_text(ctx, page))
        if not m:
            raise SystemExit('%s: cannot find the %s reward on %s' % (bid, item, page))
        qty = int(m.group(1).replace(',', '')) if m.groups() else 1
        b['always'].append(row_entry(item, qty, qty, False, Fraction(1)))
        notes['warnings'].append('%s: %s x%d is a reward the Wiki gives in prose (%s); listed as always' % (
            bid, item, qty, page))
    return b


def build_chest_rows(cfg, ctx):
    text = section_text(page_text(ctx, cfg['page']), 2, cfg.get('dropsHeading', 'Drops'))
    return rows_boss(cfg, ctx, chest_rows(cfg, ctx, text))


def wikitable_rows(text):
    """Rows of a {| ... |} table as lists of cell texts (header rows included, marked by a leading '!')."""
    rows = []
    for chunk in re.split(r'\n\|-[^\n]*', text):
        cells = []
        for line in chunk.split('\n'):
            line = line.strip()
            if line.startswith(('{|', '|}', '|+')) or not line:
                continue
            if line[0] in '|!':
                cells.extend(c.strip() for c in re.split(r'\|\||!!', line[1:]))
            elif cells:
                cells[-1] += '\n' + line
        if cells:
            rows.append(cells)
    return rows


def first_fraction(cell):
    m = re.match(r'\s*(\d+)/([\d,]+)', cell)
    return Fraction(int(m.group(1)), int(m.group(2).replace(',', ''))) if m else None


def build_doom(cfg, ctx):
    """A delve claimed after level cfg['delve']: one regular roll per level, the level-dependent unique, Dom and clue
    chances combined over the levels, and the guaranteed demon tears added up."""
    notes, bid, delve = ctx['notes'], cfg['id'], cfg['delve']
    full = page_text(ctx, cfg['page'])
    entries = chest_rows(cfg, ctx, section_text(full, 2, 'Drops'))
    special = {e.name: e for e in entries if chest_role(cfg, e) == 'special'}
    b = rows_boss(dict(cfg, rolls=delve), ctx, entries)

    # The "Unique rates" table: per-level rate of each unique.
    m = re.search(r'<span id="Unique rates"></span>\s*\{\|(.*?)\n\|\}', full, flags=re.S)
    rows = wikitable_rows(m.group(1))
    header = [c for c in rows[0]]
    if header[:3] != ['Delve level', 'Overall chance', 'Cumulative chance']:
        raise SystemExit('%s: unexpected unique-rate table header %s' % (bid, header))
    items = header[3:]
    per_level = {}
    for cells in rows[1:]:
        level = int(cells[0])
        rates = [None if 'NA' in c else first_fraction(c) for c in cells[3:]]
        per_level[level] = dict(zip(items, rates))
        overall = first_fraction(cells[1])
        listed = sum((r for it, r in zip(items, rates) if r and it != 'Dom'), Fraction(0))
        if listed != overall:
            raise SystemExit('%s: level %d uniques add up to %s, not %s' % (bid, level, listed, overall))
    for item in items:
        rows_for = [e for n, e in special.items() if n.lower().startswith(item.lower())]
        if len(rows_for) != 1:
            raise SystemExit('%s: unique-rate column %s matches %d rows' % (bid, item, len(rows_for)))
        e = rows_for[0]
        rates = [per_level[lv][item] for lv in sorted(per_level) if lv <= delve and per_level[lv][item]]
        if rates[0] != e.prob or per_level[max(per_level)][item] != (first_fraction(e.alt) or e.prob):
            raise SystemExit('%s: %s row %s/%s does not match the unique-rate table' % (bid, e.name, e.raw, e.alt))
        miss = Fraction(1)
        for r in rates:
            miss *= 1 - r
        b['tertiary'].append(with_prob(e, 1 - miss))
        notes['warnings'].append('%s: %s over delve levels 1-%d (%s): 1/%.1f per run' % (
            bid, e.name, delve, ', '.join(str(r) for r in rates), 1 / float(1 - miss)))

    clue = special['Clue scroll (elite)']
    m = re.search(r'(\d+/\d+) at delve levels 1 and 2, (\d+/\d+) afterwards', clue.notes)
    early, late = parse_fraction(m.group(1)), parse_fraction(m.group(2))
    if early != clue.prob or late != first_fraction(clue.alt):
        raise SystemExit('%s: elite clue note does not match its rarities' % bid)
    p = 1 - (1 - early) ** min(2, delve) * (1 - late) ** max(0, delve - 2)
    b['tertiary'].append(with_prob(clue, p))
    notes['warnings'].append('%s: elite clue %s on levels 1-2 and %s after, over %d levels: 1/%.2f per run' % (
        bid, early, late, delve, 1 / float(p)))

    # Guaranteed demon tears, from the quantity multiplier table.
    m = re.search(r'<span id="Quantity multiplier"></span>.*?\{\|(.*?)\n\|\}', full, flags=re.S)
    tears, cumulative, mults = 0, None, []
    for cells in wikitable_rows(m.group(1)):
        if re.fullmatch(r'\d+', cells[0]) and int(cells[0]) <= delve:
            mults.append(Fraction(cells[1]))
            tears += int(cells[2])
            cumulative = int(cells[3])
    if tears != cumulative:
        raise SystemExit('%s: guaranteed demon tears add up to %d, the table says %s' % (bid, tears, cumulative))
    tear = special['Demon tear']
    b['always'].append(row_entry(tear.name, tears, tears, False, Fraction(1)))
    notes['warnings'].append('%s: guaranteed demon tears over levels 1-%d: %d (the Wiki row shows level 3\'s %d)' % (
        bid, delve, tears, tear.lo))
    notes['warnings'].append('%s: regular rows keep the Wiki\'s level-3 quantities; the level multipliers %s average '
                             '%.3f over levels 1-%d' % (bid, ', '.join(str(x) for x in mults),
                                                       float(sum(mults) / len(mults)), delve))
    return b


def wintertodt_interpolate(level, low, high):
    """Module:Wintertodt supply crate, p.interpolate: the chance out of 256 of one sub-table row."""
    return min(max(low + (high - low) * (level - 1) // 98 + 1, 0), 256)


def round_table(probs, label, notes, bits=30):
    """Exact chances whose denominators don't fit an int, rounded to a common outOf of 2^bits that keeps the total.
    Largest remainders get the leftover units."""
    out_of = 2 ** bits
    scaled = [p * out_of for p in probs]
    weights = [int(s) for s in scaled]
    left = int(sum(probs) * out_of) - sum(weights)
    for i in sorted(range(len(probs)), key=lambda i: scaled[i] - weights[i], reverse=True)[:left]:
        weights[i] += 1
    rounded = [Fraction(w, out_of) for w in weights]
    err = max((abs(float((r - p) / p)) for r, p in zip(rounded, probs) if p), default=0)
    if any(r != p for r, p in zip(rounded, probs)):
        notes['warnings'].append('%s: exact chances need denominators up to 2^%d; stored out of 2^%d (largest '
                                 'relative error %.1g)' % (label, max(p.denominator for p in probs).bit_length() - 1,
                                                          bits, err))
    return rounded


def build_wintertodt(cfg, ctx):
    """One game at cfg['points'] points: the reward cart's rolls. Each roll goes down the unique chain (phoenix,
    dragon axe, tome, gloves, torch, pyromancer piece, burnt page), else the standard table, whose material sub-tables
    depend on the player's levels (Module:Wintertodt supply crate)."""
    notes, bid, points, level = ctx['notes'], cfg['id'], cfg['points'], cfg['skillLevel']
    text = page_text(ctx, cfg['page'])
    mech = section_text(text, 2, 'Drop mechanic')
    m = re.search(r'\*\s*%s points = (\d+) rolls \+ (\d+)%% chance of an extra roll' % format(points, ','), mech)
    if not m:
        raise SystemExit('%s: the reward cart page no longer gives the rolls for %d points' % (bid, points))
    base, extra = int(m.group(1)), Fraction(int(m.group(2)), 100)
    mod = ctx['pages']['Module:Wintertodt supply crate'][2]
    if 'local rolls = 1 + points / 500' not in mod or base + extra != 1 + Fraction(points, 500):
        raise SystemExit('%s: rolls for %d points no longer match the module' % (bid, points))

    rewards = section_text(text, 2, cfg['dropsHeading'])
    entries = chest_rows(cfg, ctx, rewards)
    uniques = [e for e in entries if e.section == 'Unique']

    # The unique chain, in the order of the "Reward rolls" table.
    table = re.search(r'===Reward rolls===.*?\{\|(.*?)\n\|\}', mech, flags=re.S).group(1)
    chain, used = [], set()
    reach = Fraction(1)
    for cells in wikitable_rows(table)[1:]:
        step = re.search(r'\[\[([^\]|]+)', cells[1]).group(1)
        rate, shown = first_fraction(cells[2]), cells[3]
        group = [e for e in uniques if e.name.lower() == step.lower() or e.name.lower().startswith(step.lower() + ' (')]
        if not group:
            group = [e for e in uniques if e.name.split()[0].lower() == step.split()[0].lower()]
        if not group or any(e.prob != rate for e in group) or used & {id(e) for e in group}:
            raise SystemExit('%s: reward roll %s does not match the unique rows' % (bid, step))
        used.update(id(e) for e in group)
        check_listed(bid, row_entry(step, 1, 1, False, rate, raw=shown), reach * rate, notes)
        for e in group:
            chain.append(with_prob(e, reach * rate / len(group)))
        if len(group) > 1:
            notes['warnings'].append('%s: %s is one %s roll for the piece the player has fewest of; each of %s is '
                                     'listed at a %d-way share' % (bid, step, rate, ', '.join(e.name for e in group),
                                                                   len(group)))
        reach *= 1 - rate
    if len(used) != len(uniques):
        raise SystemExit('%s: unique rows outside the reward-roll chain' % bid)
    tertiary = []
    for e in chain:
        p = 1 - (1 - e.prob) ** base * (1 - e.prob * extra)
        tertiary.append(with_prob(e, p))
    notes['warnings'].append('%s: %d points give %d rolls and a %s chance of another; each unique is listed at its '
                             'chance per game. The standard table is reached with chance %.4f per roll but is rolled '
                             'every roll, so standard drops are overstated by %.1f%%' % (
                                 bid, points, base, extra, reach, 100 * float(1 - reach)))

    # The standard table: the six material sub-tables and the "Other" rows.
    skills = {}
    for name, body in re.findall(r'items\.(\w+) = \{(.*?)\n\}', mod, flags=re.S):
        rows = re.findall(r'\{name="([^"]+)", minQty=(\d+), maxQty=(\d+), lowRoll=(-?\d+), highRoll=(-?\d+)\}', body)
        if rows:
            skills[name] = [(n, int(a), int(b_), int(lo), int(hi)) for n, a, b_, lo, hi in rows]
    for part in ('math.floor(lowRoll + (highRoll - lowRoll) * (skillLevel - 1) / 98)',
                 'math.min(math.max(unclamped+1, 0), 256)',
                 'local subchance = p.interpolate(skillLevel, item.lowRoll, item.highRoll) / 256',
                 'remainingProbability = remainingProbability - chancePerRoll'):
        if part not in mod:
            raise SystemExit('%s: Module:Wintertodt supply crate no longer has %s' % (bid, part))
    main, tables = [], []
    for sub in [t for _, lvl, t in headings(rewards) if lvl == 3 and t not in ('Unique', 'Other')]:
        stext = section_text(rewards, 3, sub)
        m = re.search(r"There is a (\d+/\d+) chance to drop on the \w+ subtable\. The exact drop rates on this "
                      r"subtable are based on the player's \[\[(\w+)\]\] level", stext)
        access, skill = parse_fraction(m.group(1)), m.group(2).lower()
        page_rows = {e.name: e for e in entries if e.section == sub}
        mod_rows = skills[skill]
        if set(page_rows) != {r[0] for r in mod_rows}:
            raise SystemExit('%s: %s rows differ from the module\'s %s rows' % (bid, sub, skill))
        rem, exact = Fraction(1), []
        for name, lo, hi, low, high in mod_rows:
            c = rem * Fraction(wintertodt_interpolate(level, low, high), 256)
            rem -= c
            exact.append((name, c))
            e = page_rows[name]
            if (e.lo, e.hi) != (lo, hi):
                notes['warnings'].append('%s: %s is %d-%d on the reward cart page but %d-%d in the module; the page\'s '
                                         'is used' % (bid, name, e.lo, e.hi, lo, hi))
        if rem != 0:
            raise SystemExit('%s: %s sub-table leaves %s' % (bid, sub, rem))
        zero = [n for n, c in exact if c == 0]
        exact = [(n, c) for n, c in exact if c]
        rounded = round_table([c for _, c in exact], '%s %s at level %d' % (bid, sub, level), notes)
        key = 'wintertodt_%s' % sub.lower()
        register_shared(ctx, key, [with_prob(page_rows[n], r) for (n, _), r in zip(exact, rounded)])
        tables.append((key, access))
        notes['warnings'].append('%s: %s (%s per roll) is shared table %s at %s %d%s' % (
            bid, sub, access, key, skill, level, '; never at this level: ' + ', '.join(zero) if zero else ''))
    for e in entries:
        if e.section == 'Special seeds':
            notes['excluded'].append((bid, e.name, e.raw, 'only in place of a 4th pair of warm gloves or bruma torch'))
        elif e.section == 'Other':
            main.append(e)
    total = sum((e.prob for e in main), Fraction(0)) + sum((w for _, w in tables), Fraction(0))
    if total != 1:
        raise SystemExit('%s: the standard table adds up to %s' % (bid, total))
    register_shared(ctx, 'wintertodt_reward_cart', main, tables)
    tertiary.append(TableRoll('wintertodt_reward_cart', extra))
    notes['warnings'].append('%s: the chance of a %d%s roll is a %s tertiary roll on wintertodt_reward_cart (the same '
                             'standard table)' % (bid, base + 1, 'rd' if base == 2 else 'th', extra))
    return {'always': [], 'main': main, 'tables': tables, 'tertiary': tertiary, 'rolls': base}


def tempoross_templates(cfg):
    """The reward pool's unique, fish (for the setup's Fishing band) and other rows, which it transcludes."""
    return {'Unique': 'Template:Reward pool/Rewards unique',
            'Fish': 'Template:Reward pool/Rewards levels %s' % cfg['fishBand'][len('Levels '):],
            'Other': 'Template:Reward pool/Rewards other'}


def build_tempoross(cfg, ctx):
    """One game with cfg['permits'] reward permits: one reward-pool roll per permit, at Fishing cfg['fishBand']."""
    notes, bid = ctx['notes'], cfg['id']
    pool = page_text(ctx, 'Reward pool')
    parts = {}
    for section, tmpl in tempoross_templates(cfg).items():
        if '{{%s}}' % tmpl[len('Template:'):] not in pool:
            raise SystemExit('%s: the reward pool no longer uses %s' % (bid, tmpl))
        rows = chest_rows(cfg, ctx, page_text(ctx, tmpl), section)
        keep = []
        for e in rows:
            if 'in place of duplicate' in e.notes:
                notes['excluded'].append((bid, '%s x%d' % (e.name, e.lo), e.raw,
                                          'only in place of a fish barrel or tackle box the player already owns'))
            else:
                keep.append(e)
        parts[section] = keep
    m = re.search(r"There is about a (\d+/\d+) chance to roll the fish sub-table", pool)
    fish_listed = sum((e.prob for e in parts['Fish']), Fraction(0))
    if parse_fraction(m.group(1)) != fish_listed:
        raise SystemExit('%s: fish rows add up to %s, not the Wiki\'s %s' % (bid, fish_listed, m.group(1)))
    rest = sum((e.prob for s in ('Unique', 'Other') for e in parts[s]), Fraction(0))
    slot = 1 - rest
    notes['warnings'].append('%s: the Wiki\'s rows add up to %s; the fish sub-table, "about %s" on the Wiki, is given '
                             'the remaining %s (each fish row times %s)' % (bid, rest + fish_listed, m.group(1), slot,
                                                                          slot / fish_listed))
    main = parts['Unique'] + [with_prob(e, e.prob * slot / fish_listed) for e in parts['Fish']] + parts['Other']
    perm = re.search(r'\|\s*([\d,]+)\s*\|\|\s*%d\s*\n' % cfg['permits'], page_text(ctx, 'Tempoross'))
    notes['warnings'].append('%s: %d permits (guaranteed from %s points) are %d reward-pool rolls' % (
        bid, cfg['permits'], perm.group(1), cfg['permits']))
    return {'always': [], 'main': main, 'tables': [], 'tertiary': [], 'rolls': cfg['permits']}


def link_name(cell):
    m = re.search(r'\[\[(?!File:)([^\]|#]+)(?:#[^\]|]*)?(?:\|([^\]]+))?\]\]', cell)
    return (m.group(1).strip(), (m.group(2) or m.group(1)).strip()) if m else (None, None)


def table_hitpoints(text):
    """{page name: hitpoints} from a minigame's monster table (the first number cell after the name cell)."""
    hp, extra = {}, {}
    for cells in wikitable_rows(text):
        for i, c in enumerate(cells):
            page, shown = link_name(c)
            if page:
                break
        else:
            continue
        nums = [x for x in cells[i + 1:] if re.fullmatch(r'\d+', x)]
        if nums:
            hp[page] = int(nums[0])
            hp.setdefault(shown, int(nums[0]))
            extra[page] = ' '.join(cells)
    return hp, extra


def infobox_hp(pages, page, version=None):
    return infobox_number(infobox_monsters(pages[page][2])[0], 'hitpoints', version)


def fight_cave_hp(pages):
    """Waves 1-63 of Template:TzHaar Fight Cave Waves. Each level-45 Tz-Kek splits into two level-22 ones when killed.
    The Yt-HurKot healers on wave 63 don't have to be killed."""
    cave = strip_comments(pages['TzHaar Fight Cave'][2])
    hp = {}
    for line in section_text(cave, 2, 'Monsters').split('\n'):
        cells = [c.strip() for c in line.lstrip('|').split('||')]
        if len(cells) > 4 and re.fullmatch(r'\d+', cells[0]):
            hp[(link_name(cells[2])[0], int(cells[0]))] = int(cells[3])
    split = re.search(r'\|\|\s*(\d+) appear after a 45 is killed', cave)
    tmpl = strip_comments(pages['Template:TzHaar Fight Cave Waves'][2])
    if "They don't have to be killed to complete the Fight Cave" not in tmpl:
        raise SystemExit('fight caves: the healer note has changed')
    chunks = re.split(r'\|\s*style="font-size:18px;text-align:center"\s*\|\s*(\d+)\s*\n', tmpl)
    total, waves = 0, 0
    for wave, body in zip(chunks[1::2], chunks[2::2]):
        waves += 1
        for m in re.finditer(r'\{\{TzHaar Fight Cave Waves/div\|([^|}]+)\|[^|}]*\|[^|}]*\|(\d+)\|', body):
            name, level = m.group(1).strip(), int(m.group(2))
            if name == 'Yt-HurKot':
                continue
            total += hp[(name, level)]
            if name == 'Tz-Kek' and level == 45:
                total += int(split.group(1)) * hp[('Tz-Kek', 22)]
    jad = infobox_hp(pages, 'TzTok-Jad')
    combat = infobox_number(infobox_monsters(pages['TzTok-Jad'][2])[0], 'combat', None)
    if waves != 63 or hp[('TzTok-Jad', combat)] != jad:
        raise SystemExit('fight caves: read %d waves; Jad %s' % (waves, hp.get(('TzTok-Jad', combat))))
    return total, combat, ('waves 1-62 plus Jad (%d) from Template:TzHaar Fight Cave Waves, each level-45 Tz-Kek with '
                           'the two level-22 ones it splits into; Jad\'s healers not counted' % jad)


def inferno_hp(pages):
    """Waves 1-68 of the Inferno's wave breakdown (each Jal-Ak with the three it splits into), plus TzKal-Zuk. Zuk's
    own minions die with it and aren't counted."""
    inf = strip_comments(pages['Inferno'][2])
    hp, rows = table_hitpoints(section_text(inf, 2, 'Monsters'))
    waves_text = section_text(inf, 2, 'Wave breakdown')
    key = dict(re.findall(r'^\*([A-Za-z]+):\s*\[\[File:[^\]]*\]\]\s*\[\[([^\]|]+)\]\]', waves_text, flags=re.M))
    splits = re.findall(r'\[\[(Jal-AkRek-\w+)\]\]', rows['Jal-Ak'])
    blob = hp['Jal-Ak'] + sum(infobox_hp(pages, p) for p in splits)
    total, seen = 0, []
    for wave, body in re.findall(r"^\*'''Wave (\d+):'''\s*(.*)$", waves_text, flags=re.M):
        seen.append(int(wave))
        for count, short in re.findall(r'(\d+)x\s*([A-Za-z]+)', body.replace("'''", '')):
            page = key[short]
            total += int(count) * (blob if page == 'Jal-Ak' else hp[page])
    if seen != list(range(1, 69)) or len(splits) != 3:
        raise SystemExit('inferno: read waves %s, splits %s' % (seen, splits))
    if 'Any remaining minions when TzKal-Zuk is killed will automatically die with it' not in pages['TzKal-Zuk'][2]:
        raise SystemExit('inferno: the Zuk minion note has changed')
    zuk = infobox_hp(pages, 'TzKal-Zuk')
    combat = infobox_number(infobox_monsters(pages['TzKal-Zuk'][2])[0], 'combat', None)
    return total + zuk, combat, ('waves 1-68 (%d, each Jal-Ak %d with its three splits) plus TzKal-Zuk %d; Zuk\'s '
                                 'minions not counted' % (total, blob, zuk))


def colosseum_hp(pages):
    """Waves 1-11 of Fortis Colosseum/Strategies' wave breakdown, reinforcements included, plus Sol Heredit."""
    fc = strip_comments(pages['Fortis Colosseum'][2])
    hp, _ = table_hitpoints(section_text(fc, 3, 'Enemies'))
    hp = {k.lower(): v for k, v in hp.items()}
    warband = sum(v for k, v in hp.items() if k.startswith('fremennik warband '))
    strat = strip_comments(pages['Fortis Colosseum/Strategies'][2])
    text = section_text(strat, 2, 'Wave breakdown')
    if 'If the wave is not cleared within 40 seconds, the reinforcement NPCs will spawn' not in text:
        raise SystemExit('colosseum: the reinforcement note has changed')
    total, waves, base = 0, [], 0
    for line in text.split('\n'):
        m = re.match(r'^\*Wave (\d+):\s*(.*)$', line) or re.match(r'^\*\*Reinforcements?:\s*(.*)$', line)
        if not m:
            continue
        if line.startswith('*Wave'):
            waves.append(int(m.group(1)))
            if int(m.group(1)) == 12:
                continue
        for part in m.groups()[-1].split(','):
            mm = re.match(r'\s*(?:(\d+)x\s*)?(.+?)\s*$', part)
            name = link_name(mm.group(2))[0] or mm.group(2)
            value = warband if name.lower() == 'fremennik warband' else hp[name.lower()]
            total += int(mm.group(1) or 1) * value
            if line.startswith('*Wave'):
                base += int(mm.group(1) or 1) * value
    if waves != list(range(1, 13)):
        raise SystemExit('colosseum: read waves %s' % waves)
    sol = infobox_hp(pages, 'Sol Heredit')
    combat = infobox_number(infobox_monsters(pages['Sol Heredit'][2])[0], 'combat', None)
    return total + sol, combat, ('waves 1-11 with reinforcements (%d; %d without) plus Sol Heredit %d' % (
        total, base, sol))


def chest_stats(cfg, pages):
    st = cfg['stats']
    if isinstance(st, dict):
        return boss_stats(cfg, pages)
    if st[0] == 'waves':
        return {'fightCaves': fight_cave_hp, 'inferno': inferno_hp, 'colosseum': colosseum_hp}[st[1]](pages)
    _, page, regex, what = st
    m = re.search(regex, strip_comments(pages[page][2]))
    if not m:
        raise SystemExit('%s: cannot find its %s on %s' % (cfg['id'], what, page))
    return sum(int(g.replace(',', '')) for g in m.groups()), 0, '%s (%s)' % (what, page)


def build_chest_boss(cfg, ctx):
    run_checks(cfg, ctx)
    builder = {'rows': build_chest_rows, 'doom': build_doom, 'wintertodt': build_wintertodt,
               'tempoross': build_tempoross}[cfg['kind']]
    return builder(cfg, ctx)


def collection_log_tabs_chest(log_page, data_json, tabs):
    """As collection_log_tabs, but also matches a log-page name that extends the data.json name (the log page says
    "Eye of Ayak (uncharged)", data.json "Eye of Ayak"). Returns the log page's names, and the data.json names that
    differ as aliases."""
    items = json.loads(data_json)
    text = strip_comments(log_page)
    out, aliases = {}, []
    for tab in tabs:
        sec = section_text(text, 3, tab)
        names = [split_params(inner)[1][0] for _, inner in find_templates(sec) if inner.startswith('plink|')]
        tab_items = [it for it in items if any(t.lower() == tab.lower() for t in it['tabs'])]
        ids = []
        for nm in names:
            match = [it for it in tab_items if it['name'].lower() == nm.lower()] or \
                    [it for it in tab_items if it['name'].lower().startswith(nm.lower())] or \
                    [it for it in tab_items if nm.lower().startswith(it['name'].lower() + ' (')]
            if len(match) != 1:
                raise SystemExit('log item %r (%s) matched %d data.json rows' % (nm, tab, len(match)))
            ids.append((match[0]['id'], nm))
            if match[0]['name'] != nm:
                aliases.append((match[0]['id'], match[0]['name']))
        if len(ids) != len(tab_items):
            raise SystemExit('%s: log page lists %d items, data.json has %d' % (tab, len(ids), len(tab_items)))
        out[tab] = ids
    return out, aliases


# --------------------------------------------------------------------------------------------
# Building the raids (RAID_BOSSES)
# --------------------------------------------------------------------------------------------

def wiki_int(s):
    return int(s.replace(',', ''))


def page_match(ctx, page, regex, what):
    m = re.search(regex, page_text(ctx, page))
    if not m:
        raise SystemExit('cannot find %s on %s (/%s/)' % (what, page, regex))
    return m


def source_has(ctx, page, parts):
    """Stop if a module or template source no longer has these pieces (whitespace ignored)."""
    src = re.sub(r'\s+', ' ', ctx['pages'][page][2])
    for part in parts:
        if re.sub(r'\s+', ' ', part) not in src:
            raise SystemExit('%s no longer has %r' % (page, part))
    return src


def raid_rows(cfg, ctx):
    """The chest page's rows by role, with the unique and main totals and the main rows' rolls checked against the
    setup. The unique rows become the pre-roll's shared table."""
    notes, bid = ctx['notes'], cfg['id']
    text = section_text(page_text(ctx, cfg['page']), 2, cfg['dropsHeading'])
    out = {'unique': [], 'main': [], 'tertiary': []}
    for e in chest_rows(cfg, ctx, text):
        role = cfg['sections'].get(e.section)
        if role is None:
            raise SystemExit('%s: %s is in section "%s", which has no role' % (bid, e.name, e.section))
        if role == 'skip':
            notes['skipped'].append((bid, e.name, e.raw, 'section "%s" is not part of the standard run' % e.section))
            continue
        out.setdefault(role, []).append(e)
    for role, key in (('unique', 'uniqueTotal'), ('main', 'mainTotal')):
        got = sum((e.prob for e in out[role]), Fraction(0))
        if got != cfg[key]:
            raise SystemExit('%s: %s rows add up to %s, not %s' % (bid, role, got, cfg[key]))
    for e in out['main']:
        if e.rolls != cfg['rolls']:
            raise SystemExit('%s: %s is listed at %d rolls, not %d' % (bid, e.name, e.rolls, cfg['rolls']))
    register_shared(ctx, cfg['uniqueTable'], out['unique'])
    notes['warnings'].append('%s: the %d unique rows (adding up to %s) are shared table %s; the %d common rows '
                             '(adding up to %s) are main, rolled %d times' % (
                                 bid, len(out['unique']), cfg['uniqueTotal'], cfg['uniqueTable'], len(out['main']),
                                 cfg['mainTotal'], cfg['rolls']))
    return out


def raid_tertiary(cfg, ctx, rows, rates):
    """The tertiary rows at their chance per run: rates is row name -> (chance, how it was worked out)."""
    names = sorted(e.name for e in rows['tertiary'])
    if names != sorted(rates):
        raise SystemExit('%s: tertiary rows are %s, expected %s' % (cfg['id'], names, sorted(rates)))
    out = []
    for e in rows['tertiary']:
        p, how = rates[e.name]
        out.append(with_prob(e, p))
        ctx['notes']['warnings'].append('%s: %s is %s: %s (1/%.1f) per run' % (cfg['id'], e.name, how, p, 1 / p))
    return out


def raid_result(cfg, ctx, main, tertiary, p):
    ctx['notes']['warnings'].append('%s: pre-roll %s on %s: %s (1/%.2f) per run; if it hits, no common rolls' % (
        cfg['id'], cfg['uniqueTable'], cfg['name'], p, 1 / p))
    return {'always': [], 'main': main, 'tables': [], 'tertiary': tertiary, 'rolls': cfg['rolls'],
            'preRoll': (cfg['uniqueTable'], p)}


def build_cox(cfg, ctx):
    """A solo normal-mode raid at cfg['points'] points. The unique pre-roll is points / 867,600 (the ancient chest's 1%
    per 8,676 points); otherwise two common rolls, each row's quantity floor(points / divisor) from the Wiki's loot
    calculator. The elite clue is only rolled without a unique and Olmlet only with one; Tekton drops the onyx."""
    notes, bid, points = ctx['notes'], cfg['id'], cfg['points']
    rows = raid_rows(cfg, ctx)
    per_pct = wiki_int(page_match(ctx, cfg['page'], r'For every ([\d,]+) total points obtained, a 1% chance to obtain '
                                                    r'a unique loot is given', 'the unique rate').group(1))
    cap = page_match(ctx, cfg['page'], r'This chance is capped at ([\d.]+)% \(([\d,]+) points\)', 'the unique cap')
    if points >= wiki_int(cap.group(2)):
        raise SystemExit('%s: %d points reach the unique cap' % (bid, points))
    p = Fraction(points, per_pct * 100)
    notes['warnings'].append('%s: unique chance %d / (%d x 100) = %s, below the %s%% cap at %s points' % (
        bid, points, per_pct, p, cap.group(1), cap.group(2)))

    source_has(ctx, 'Module:Chambers of Xeric calculator', [
        'local quantity = math.floor(args.personalPoints / trash.divisor)',
        'if trash.name == "Torn prayer scroll" or trash.name == "Dark relic" then quantity = 1'])
    mod = ctx['pages']['Module:Chambers of Xeric calculator'][2]
    divisors = {n: int(d) for n, d in re.findall(r'\{name = "([^"]+)", divisor = (\d+), weight = \d+\}', mod)}
    common_cap = wiki_int(page_match(ctx, 'Chambers of Xeric', r'Common loot awarded at the end of the raid scales up '
                                                               r'to ([\d,]+) points', 'the common-loot cap').group(1))
    main, shown, off = [], [], []
    for e in rows['main']:
        if e.name not in divisors:
            raise SystemExit('%s: %s has no divisor in the loot calculator' % (bid, e.name))
        if e.name in ('Torn prayer scroll', 'Dark relic'):
            qty = 1
            if (e.lo, e.hi) != (1, 1):
                raise SystemExit('%s: %s is %d-%d on the chest page' % (bid, e.name, e.lo, e.hi))
        else:
            qty = points // divisors[e.name]
            top = common_cap // divisors[e.name]
            if e.lo != 1 or abs(e.hi - top) > 1:
                raise SystemExit('%s: %s is %d-%d on the chest page, not 1-floor(%d / %d)' % (
                    bid, e.name, e.lo, e.hi, common_cap, divisors[e.name]))
            if e.hi != top:
                off.append('%s %d (floor(%d / %d) = %d)' % (e.name, e.hi, common_cap, divisors[e.name], top))
            shown.append('%s %d (/%d)' % (e.name, qty, divisors[e.name]))
        main.append(row_entry(e.name, qty, qty, e.noted, e.prob, e.section, e.raw, e.notes))
    seeds = sorted(n for n in divisors if n not in {e.name for e in rows['main']})
    notes['warnings'].append('%s: common quantities at %d points, floor(points / divisor) from Module:Chambers of '
                             'Xeric calculator: %s' % (bid, points, ', '.join(shown)))
    notes['warnings'].append('%s: each row\'s listed maximum is floor(%d / divisor), except %s' % (
        bid, common_cap, '; '.join(off) or 'none'))
    herbs = [e.name for e in rows['main'] if e.section == 'Herbs']
    if not any('33% of these herbs will drop as seeds instead' in e.notes for e in rows['main']):
        raise SystemExit('%s: the herb seed note has changed' % bid)
    notes['warnings'].append('%s: not modelled: the two common rolls can\'t give the same row; %d herb rows drop a '
                             'third as seeds (the calculator\'s %s)' % (bid, len(herbs), ', '.join(seeds)))

    t = {e.name: e for e in rows['tertiary']}
    clue, olmlet = t['Clue scroll (elite)'], t['Olmlet']
    if 'only rolled when the player does not get a broadcasted unique reward' not in clue.notes:
        raise SystemExit('%s: the elite clue note has changed' % bid)
    m = re.search(r'Olmlet is only rolled when the player gets a broadcasted unique reward; with ([\d,]+) points per '
                  r'raid, the Olmlet rate is ~1/([\d,]+)', olmlet.notes)
    if not m:
        raise SystemExit('%s: the Olmlet note has changed' % bid)
    at = Fraction(wiki_int(m.group(1)), per_pct * 100) * olmlet.prob
    shown_rate = wiki_int(m.group(2))
    if abs(1 / float(at) - shown_rate) > 0.01 * shown_rate:
        raise SystemExit('%s: the Olmlet note says ~1/%d at %s points; the rule gives 1/%.1f' % (
            bid, shown_rate, m.group(1), 1 / at))
    notes['warnings'].append('%s: the Olmlet note says ~1/%s at %s points; points / %d / 53 gives 1/%.1f there' % (
        bid, m.group(2), m.group(1), per_pct * 100, 1 / at))
    tertiary = raid_tertiary(cfg, ctx, rows, {
        'Clue scroll (elite)': ((1 - p) * clue.prob, '%s, rolled only without a unique' % clue.raw),
        'Olmlet': (p * olmlet.prob, '%s, rolled only with a unique' % olmlet.raw)})
    onyx = [e for e in chest_rows(cfg, ctx, section_text(page_text(ctx, 'Tekton'), 2, 'Drops')) if e.name == 'Onyx']
    if len(onyx) != 1 or '1/450 being the rate in solo normal mode raids' not in onyx[0].notes:
        raise SystemExit('%s: Tekton\'s onyx row has changed' % bid)
    tertiary.append(with_prob(onyx[0], onyx[0].prob))
    notes['warnings'].append('%s: Tekton\'s onyx (%s in a solo normal raid) is a tertiary, as if every raid had '
                             'Tekton\'s room (the money-making guide counts it at 1/450 per raid)' % (bid, onyx[0].raw))
    return raid_result(cfg, ctx, main, tertiary, p)


def build_tob(cfg, ctx):
    """A deathless normal-mode raid in a team of cfg['teamSize'] with equal contribution: the team's 1/9.1 unique, split
    evenly, otherwise three common rolls; the normal-mode elite clue and Lil' Zik."""
    notes, bid = ctx['notes'], cfg['id']
    rows = raid_rows(cfg, ctx)
    m = page_match(ctx, cfg['page'], r'Without any deaths, there is a 1/([\d.]+) \(~\d+%\) chance of a unique reward '
                                     r'across the team', 'the unique rate')
    team = 1 / Fraction(m.group(1))
    p = team / cfg['teamSize']
    notes['warnings'].append('%s: unique chance 1/%s for the team, an equal 1/%d share each: %s' % (
        bid, m.group(1), cfg['teamSize'], p))
    t = {e.name: e for e in rows['tertiary']}
    clue, zik = t['Clue scroll (elite)'], t["Lil' Zik"]
    m = re.search(r'The elite clue scroll rarity for Entry, Normal, and Hard Mode is ([\d.]+/\d+), ([\d.]+/\d+), and '
                  r'([\d.]+/\d+), respectively', clue.notes)
    if not m or eval_arith(m.group(1)) != clue.prob:
        raise SystemExit('%s: the elite clue note has changed' % bid)
    m2 = re.search(r"The base rate for Lil' Zik is (1/\d+) in Normal Mode", zik.notes)
    if not m2 or parse_fraction(m2.group(1)) != zik.prob:
        raise SystemExit("%s: the Lil' Zik note has changed" % bid)
    tertiary = raid_tertiary(cfg, ctx, rows, {
        'Clue scroll (elite)': (eval_arith(m.group(2)), 'the normal-mode rate in its note (the row shows Entry Mode\'s '
                                                        '%s)' % clue.raw),
        "Lil' Zik": (zik.prob, 'the normal-mode base rate (full performance)')})
    return raid_result(cfg, ctx, rows['main'], tertiary, p)


def build_toa(cfg, ctx):
    """An Expert solo raid at the money-making guide's raid level, at the reward points its unique rate implies: the
    unique pre-roll, otherwise three common rolls with quantities from the divisor rule; the thread, jewels, elite clue
    and pet. Challenge rewards need a higher raid level."""
    notes, bid, team = ctx['notes'], cfg['id'], cfg['teamSize']
    rows = raid_rows(cfg, ctx)
    guide = 'Money making guide/Tombs of Amascut (Expert)'
    rl = int(page_match(ctx, guide, r'Completing Expert solo \[\[Tombs of Amascut\]\] \(lvl (\d+)\)', 'the raid level')
             .group(1))
    one_in = Fraction(page_match(ctx, guide, r'the rate of receiving a unique drop per raid is roughly '
                                             r'1/(\d+(?:\.\d+)?)', 'the unique rate').group(1))
    reg = page_match(ctx, guide, r'#vardefine:regchance\|([\d.]+)\}\}', 'regchance').group(1)
    if abs(float(1 - 1 / one_in) - float(reg)) > 1e-5:
        raise SystemExit('%s: regchance %s is not 1 - 1/%s' % (bid, reg, one_in))
    chest = page_text(ctx, cfg['page'])
    m = re.search(r"to receive a unique item for every <math>([\d,]+) - (\d+) \\times RL</math> total reward points",
                  chest)
    if not m or r'\text{RaidLevel} && \text{RaidLevel} \leq 310' not in chest or rl > 310:
        raise SystemExit('%s: the unique rule has changed' % bid)
    per_pct = wiki_int(m.group(1)) - int(m.group(2)) * rl
    points = per_pct * 100 / one_in
    if points.denominator != 1:
        raise SystemExit('%s: 1/%s does not give whole points' % (bid, one_in))
    points = int(points)
    p = Fraction(points, per_pct * 100)
    cap = int(page_match(ctx, cfg['page'], r'with a maximum rate of (\d+)% to see a unique', 'the unique cap').group(1))
    if p >= Fraction(cap, 100):
        raise SystemExit('%s: the unique chance reaches the cap' % bid)
    notes['warnings'].append('%s: the guide\'s 1/%s (regchance %s) at raid level %d is %d reward points, after the '
                             'starting 5,000 are subtracted: %d / (100 x (%s - %s x %d)) = %s' % (
                                 bid, float(one_in), reg, rl, points, points, m.group(1), m.group(2), rl, p))

    mod = source_has(ctx, 'Module:Tombs of Amascut loot', [
        'if raid_level <= 300 then return {weightsum = 240, weights = PURPLES}',
        'local quantity = math.floor(points / team_size / v.divisor)', 'quantity = math.floor(quantity * scale)',
        'quantity = math.max(quantity, 1)', 'local percent = math.floor((raid_level - 300) / 5) + 15'])
    if rl > 300:
        raise SystemExit('%s: unique weights above raid level 300 are not read' % bid)
    purples = {n: (int(w), int(lv)) for n, w, lv in re.findall(r'\{item = "([^"]+)", weight = (\d+), level = (\d+)\}',
                                                                  mod)}
    for e in rows['unique']:
        w, lv = purples[e.name]
        if Fraction(w, 240) != e.prob or lv > rl:
            raise SystemExit('%s: %s is %s on the chest page but %d/240 from level %d in the module' % (
                bid, e.name, e.raw, w, lv))
    divisors = {n: int(d) for n, d in re.findall(r'\{item = "([^"]+)", divisor = (\d+)\}', mod)}
    table = section_text(section_text(chest, 2, 'Loot mechanics'), 3, 'Common rewards')
    listed = {n.strip(): wiki_int(d) for n, d in re.findall(r'\{\{plinkt\|([^}|]+)(?:\|[^}]*)?\}\} \|\| ([\d,]+)\s*$',
                                                             table, flags=re.M)}
    if listed != {n: d for n, d in divisors.items() if n != 'Cache of runes'}:
        raise SystemExit('%s: the chest page\'s divisors differ from the module\'s' % bid)
    if rl < 300:
        raise SystemExit('%s: only the raid level 300+ quantity rule is read' % bid)
    percent = (rl - 300) // 5 + 15
    scale = 1 + Fraction(percent, 100)
    main, shown, float_diff = [], [], []
    for e in rows['main']:
        d = divisors[e.name]
        qty = max(int(Fraction(points // team // d) * scale), 1)
        as_float = max(int((points / team // d) * (1 + percent / 100)), 1)
        if as_float != qty:
            float_diff.append('%s %d (the calculator\'s floating point gives %d)' % (e.name, qty, as_float))
        shown.append('%s %d' % (e.name, qty))
        main.append(row_entry(e.name, qty, qty, e.noted, e.prob, e.section, e.raw, e.notes))
    notes['warnings'].append('%s: common quantities max(floor(floor(%d / divisor) x %s), 1) (raid level %d): %s' % (
        bid, points, scale, rl, ', '.join(shown)))
    if float_diff:
        notes['warnings'].append('%s: exact arithmetic used where Module:Tombs of Amascut loot\'s 1.15 is inexact: %s'
                                 % (bid, '; '.join(float_diff)))

    t = {e.name: e for e in rows['tertiary']}
    m = re.search(r'netting a 1/100 drop rate for every ([\d,]+) rewards points awarded, up to a maximum of (\d+)/100',
                  t['Clue scroll (elite)'].notes)
    if not m:
        raise SystemExit('%s: the elite clue note has changed' % bid)
    clue = min(Fraction(points, wiki_int(m.group(1)) * 100), Fraction(int(m.group(2)), 100))
    m2 = re.search(r"to receive the pet for every <math>([\d,]+) - (\d+) \\times RL</math> total reward points", chest)
    if not m2 or r'\text{RaidLevel} & \text{RaidLevel} \leq 400' not in chest or rl > 400:
        raise SystemExit('%s: the pet rule has changed' % bid)
    pet_pct = wiki_int(m2.group(1)) - int(m2.group(2)) * rl
    rates = {'Clue scroll (elite)': (clue, '1%% per %s points, %d points' % (m.group(1), points)),
             "Tumeken's guardian": (Fraction(points, pet_pct * 100), '1%% per (%s - %s x %d) points, %d points' % (
                 m2.group(1), m2.group(2), rl, points)),
             'Thread of Elidinis': (t['Thread of Elidinis'].prob, 'the base rate (its bad-luck mitigation, and 1/50 '
                                                                  'after the first, not modelled)')}
    jewels = [e for e in rows['tertiary'] if 'name="jewel"' in e.notes]
    if len(jewels) != 4 or any(e.prob != Fraction(1, 50) for e in jewels):
        raise SystemExit('%s: the four jewel rows have changed' % bid)
    for e in jewels:
        rates[e.name] = (e.prob, 'the base rate of one jewel (its bad-luck mitigation and duplicate protection not '
                                 'modelled)')
    tertiary = raid_tertiary(cfg, ctx, rows, rates)

    # Challenge rewards: the chest page's table gives the raid level each needs.
    levels = {n.strip(): int(lv) for n, lv in re.findall(r'^\| \{\{plinkt\|([^}|]+)\}\} \|\| (\d+)\+ \|\|', chest,
                                                          flags=re.M)}
    not_in_log = {}
    for e in rows.get('challenge', []):
        if levels.get(e.name, 0) <= rl:
            raise SystemExit('%s: challenge reward %s has no raid level above %d' % (bid, e.name, rl))
        reason = 'a challenge reward that needs raid level %d+' % levels[e.name]
        notes['excluded'].append((bid, e.name, e.raw, reason))
        not_in_log[e.name] = reason
    b = raid_result(cfg, ctx, main, tertiary, p)
    b['notInLog'] = not_in_log
    return b


def build_tfa(cfg, ctx):
    """A deathless trio with equal contribution: the team's 1/14 x 1.04 unique, split evenly, otherwise three standard
    rolls, each with a 1/51 chance of five times the quantity; the tertiaries on every run."""
    notes, bid, team = ctx['notes'], cfg['id'], cfg['teamSize']
    rows = raid_rows(cfg, ctx)
    one_in = int(page_match(ctx, cfg['page'], r'there is a 1/(\d+) chance of successfully rolling for a unique drop',
                            'the unique rate').group(1))
    step = int(page_match(ctx, cfg['page'], r'In trio and larger sizes, each additional player will increase the drop '
                                            r'rate of a unique by a multiplicative (\d+)%', 'the team bonus').group(1))
    team_p = Fraction(1, one_in) * (1 + Fraction(step * max(0, team - 2), 100))
    p = team_p / team
    notes['warnings'].append('%s: unique chance 1/%d x %s for a team of %d, an equal 1/%d share each: %s' % (
        bid, one_in, 1 + Fraction(step * max(0, team - 2), 100), team, team, p))
    m = page_match(ctx, cfg['page'], r"Each roll has a 1/(\d+) chance to be multiplied (\w+) times'''; the quantities "
                                     r"listed below are the original values", 'the quantity multiplier')
    n, mult = int(m.group(1)), {'five': 5}[m.group(2)]
    main = []
    for e in rows['main']:
        main.append(with_prob(e, e.prob * (n - 1) / n))
        main.append(row_entry(e.name, e.lo * mult, e.hi * mult, e.noted, e.prob / n, e.section, e.raw, e.notes))
    notes['warnings'].append('%s: each of the %d standard rows is a row at %d/%d of its rate and a x%d row at 1/%d' % (
        bid, len(rows['main']), n - 1, n, mult, n))
    rates = {}
    for e in rows['tertiary']:
        how = 'as listed, rolled on every run (the Wiki lists the tertiaries under standard loot)'
        if 'name=dupe' in e.notes:
            how += '; it stops once owned'
        if 'name=fragDupe' in e.notes:
            how += '; duplicate avoidance not modelled'
        rates[e.name] = (e.prob, how)
    tertiary = raid_tertiary(cfg, ctx, rows, rates)
    return raid_result(cfg, ctx, main, tertiary, p)


def build_raid_boss(cfg, ctx):
    run_checks(cfg, ctx)
    return {'cox': build_cox, 'tob': build_tob, 'toa': build_toa, 'tfa': build_tfa}[cfg['kind']](cfg, ctx)


def cox_hp(pages):
    """The Great Olm at solo scale: both claws in each of its standard phases, then its head. The combat rooms before
    it are random and aren't counted."""
    strat = strip_comments(pages['Chambers of Xeric/Strategies'][2])
    m = re.search(r'In a team size of 1-7, Olm has four phases in total, (\w+) standard phases and a head phase', strat)
    if not m or 'A raid layout can have 3 to 5 of these bosses' not in strat:
        raise SystemExit('chambers of xeric: the Olm phase or room note has changed')
    phases = {'three': 3}[m.group(1)]
    head, left, right = infobox_monsters(pages['Great Olm'][2])[:3]
    if (left.get('name'), right.get('name')) != ('Great Olm (Left claw)', 'Great Olm (Right claw)'):
        raise SystemExit('chambers of xeric: the Great Olm infoboxes have moved')
    h, l_, r_ = (infobox_number(ib, 'hitpoints', None) for ib in (head, left, right))
    total = phases * (l_ + r_) + h
    return total, infobox_number(head, 'combat', None), (
        'the Great Olm: %d standard phases of left claw %d + right claw %d, then the head %d; the 3 to 5 random combat '
        'rooms not counted' % (phases, l_, r_, h))


def tob_hp(pages):
    """The six bosses' normal-mode hitpoints, scaled to a trio ("75% of their original hitpoints")."""
    tob = strip_comments(pages['Theatre of Blood'][2])
    m = re.search(r'With a team of three or fewer players the bosses will have (\d+)% of their original hitpoints', tob)
    if not m:
        raise SystemExit('theatre of blood: the hitpoint scaling note has changed')
    scale = Fraction(int(m.group(1)), 100)
    listed = {name: int(lv) for name, lv in re.findall(r'^#\[\[([^\]]+)\]\] \(level-(\d+)', tob, flags=re.M)}
    total, parts, combat = Fraction(0), [], 0
    for page, idx, versions in TOB_BOSSES:
        ib = infobox_monsters(pages[page][2])[idx]
        if infobox_number(ib, 'combat', versions[0]) != listed[page]:
            raise SystemExit('theatre of blood: %s infobox %d is not the normal-mode one' % (page, idx))
        hps = [infobox_number(ib, 'hitpoints', v) for v in versions]
        total += sum(hps) * scale
        parts.append('%s %s' % (page, '+'.join(str(x) for x in hps)))
        combat = infobox_number(ib, 'combat', versions[-1])
    if total.denominator != 1:
        raise SystemExit('theatre of blood: trio hitpoints are not whole')
    return int(total), combat, 'normal mode at %s of the five-player hitpoints: %s' % (scale, ', '.join(parts))


def toa_hp(cfg, pages):
    """Raid level 300 solo: each path boss and Warden phase from its infobox, times 1 + raid level / 250, and the path
    bosses times their expected path-level multiplier under Walk the Path, in the Wiki calculator's kill order."""
    rl = 300
    strat = strip_comments(pages['Tombs of Amascut/Strategies'][2])
    toa = strip_comments(pages['Tombs of Amascut'][2])
    mod = pages['Module:Tombs of Amascut loot'][2]
    m = re.search(r'CalculatedStat = \(BaseStat\)\(1 \+ RaidLevel/(\d+)\)', strat)
    m2 = re.search(r'The first path level will modify most enemy hitpoints and damage by \+(\d+)%, with every '
                   r'additional level modifying these stats by \+(\d+)%', toa)
    wtp = ('Completing the first path randomly adds levels to two other paths; the second adds a level to one of the '
           'other two paths, and then the third path adds another level to the final path')
    order = re.search(r'for WTP we assume the order is ([\w-]+), ([\w-]+), ([\w-]+), ([\w-]+)', mod)
    core = re.search(r'Damage dealt to the core deals (\w+) times the damage towards the Warden', strat)
    # Heals and regeneration aren't counted; stop if what they are changes.
    for page, text in (('Kephri', "Once Kephri's shield is depleted, she will summon her children"),
                       ('Tombs of Amascut/Strategies', 'Scarab swarms]] recover Kephri\'s shields'),
                       ('Tombs of Amascut/Strategies', 'Upon depleting all of Akkha\'s health, he will restore 20% of '
                                                       'his health and enter a final stand'),
                       ('Tombs of Amascut/Strategies', 'obtaining a one-time heal of 20.0% of its maximum health')):
        if text not in strip_comments(pages[page][2]):
            raise SystemExit('tombs of amascut: %s no longer says %r' % (page, text))
    guide = strip_comments(pages['Money making guide/Tombs of Amascut (Expert)'][2])
    if not (m and m2 and order and core) or wtp not in toa:
        raise SystemExit('tombs of amascut: a hitpoint scaling note has changed')
    # The guide's invocations: Walk the Path, and no path-level invocation.
    if '|Walk the Path\n' not in guide or any(x in guide for x in ('Pathseeker', 'Pathfinder', 'Pathmaster')):
        raise SystemExit('tombs of amascut: the guide\'s path invocations have changed')
    rl_mult = 1 + Fraction(rl, int(m.group(1)))
    first, more = int(m2.group(1)), int(m2.group(2))

    def path_mult(level):
        return Fraction(1) if level == 0 else 1 + Fraction(first + more * (level - 1), 100)

    # Walk the Path: after the 1st path, two of the other three level up; after the 2nd, one of the last two; after
    # the 3rd, the last one. Each choice is random, so the six outcomes are equally likely.
    outcomes = []
    for pick1 in ((1, 2), (1, 3), (2, 3)):
        for pick2 in (2, 3):
            outcomes.append([0, int(1 in pick1), int(2 in pick1) + int(pick2 == 2),
                             int(3 in pick1) + int(pick2 == 3) + 1])
    expected = [sum(path_mult(o[i]) for o in outcomes) / len(outcomes) for i in range(4)]
    levels = [Fraction(sum(o[i] for o in outcomes), len(outcomes)) for i in range(4)]

    def ib(page, i=0):
        return infobox_monsters(pages[page][2])[i]

    kephri = infobox_monsters(pages['Kephri'][2])
    if [k.get('version') for k in kephri[:2]] != ['Aggressive', 'Shielded']:
        raise SystemExit('tombs of amascut: the Kephri infoboxes have moved')
    base = {'Ba-Ba': infobox_number(ib('Ba-Ba'), 'hitpoints', None),
            'Kephri': infobox_number(kephri[1], 'hitpoints', None) + infobox_number(kephri[0], 'hitpoints', None),
            'Zebak': infobox_number(ib('Zebak'), 'hitpoints', None),
            'Akkha': infobox_number(ib('Akkha'), 'hitpoints', None)}
    total, parts = Fraction(0), []
    for i, boss in enumerate(order.groups()):
        total += base[boss] * rl_mult * expected[i]
        parts.append('%s %d x %s x %.4f (path level %s)' % (boss, base[boss], rl_mult, expected[i], levels[i]))
    times = {'five': 5}[core.group(1)]
    wardens = []
    for page in ("Tumeken's Warden", "Elidinis' Warden"):
        p2, p3 = infobox_monsters(pages[page][2])[:2]
        wardens.append((infobox_number(p2, 'hitpoints', 2), infobox_number(p3, 'hitpoints', None),
                        infobox_number(p3, 'combat', None)))
    if wardens[0] != wardens[1]:
        raise SystemExit('tombs of amascut: the two Wardens differ')
    core_hp, p3_hp, combat = wardens[0]
    obelisk = infobox_number(ib('Obelisk (Tombs of Amascut)'), 'hitpoints', None)
    total += (obelisk + Fraction(core_hp, times) + p3_hp) * rl_mult
    parts.append('obelisk %d, the P2 Warden\'s %d through its core at x%d (%s), the P3 Warden %d, each x %s' % (
        obelisk, core_hp, times, Fraction(core_hp, times), p3_hp, rl_mult))
    return round(total), combat, 'raid level %d solo, %.1f before rounding: %s' % (rl, total, '; '.join(parts))


def team_table(text, team):
    """{column: value} for one team size of a boss page's ==Hitpoints== table (the first number in each cell)."""
    rows = wikitable_rows(section_text(text, 2, 'Hitpoints'))
    header = rows[0]
    for cells in rows[1:]:
        if cells[0] == str(team):
            return {h: wiki_int(re.match(r'\s*([\d,]+)', c).group(1)) for h, c in zip(header[1:], cells[1:])
                    if re.match(r'\s*[\d,]+', c)}
    raise SystemExit('no team size %d row' % team)


def tfa_hp(cfg, pages):
    """A trio's five bosses from each boss page's team-size table; at each fork the boss fought most in the raid's
    first week."""
    team = cfg['teamSize']
    tfa = strip_comments(pages['The Fractured Archive'][2])
    if "All bosses' hitpoints scale to the amount of players present in the raid" not in tfa:
        raise SystemExit('fractured archive: the hitpoint scaling note has changed')
    # The Archive Guardian's flying pool is its shield (counted once); Ocellus's phase 2 goes into his remainder.
    strat = strip_comments(pages['The Fractured Archive/Strategies'][2])
    for text in ('Once the shield health is depleted, it will crash into the ground',
                 'only half of all damage dealt will transfer to the boss\'s phase 3 health bar'):
        if text not in strat:
            raise SystemExit('fractured archive: the Strategies page no longer says %r' % text)
    deaths = dict(re.findall(r"^\*\*\*([^:\n]+): ([\d,]+|N/A)\s*$", tfa, flags=re.M))
    counts = [wiki_int(v) for v in deaths.values() if v != 'N/A']
    for other, chosen in TFA_FORKS:
        a, b = deaths[other], deaths[chosen]
        ok = wiki_int(b) > wiki_int(a) if b != 'N/A' else wiki_int(a) == min(counts)
        if not ok:
            raise SystemExit('fractured archive: %s (%s) is not the more fought of %s (%s)' % (chosen, b, other, a))
    total, parts = 0, []
    for page, cols in TFA_RUN:
        row = team_table(strip_comments(pages[page][2]), team)
        vals = [row[c] for c in cols]
        total += sum(vals)
        parts.append('%s %s' % (page, ' + '.join('%s %d' % (c.split(' (')[0], v) for c, v in zip(cols, vals))))
    combat = infobox_number(infobox_monsters(pages['Ocellus'][2])[0], 'combat', 3)
    return total, combat, 'team size %d: %s' % (team, ', '.join(parts))


def raid_stats(cfg, pages):
    if cfg['stats'] == 'cox':
        return cox_hp(pages)
    if cfg['stats'] == 'tob':
        return tob_hp(pages)
    return {'toa': toa_hp, 'tfa': tfa_hp}[cfg['stats']](cfg, pages)


# --------------------------------------------------------------------------------------------
# Collection log
# --------------------------------------------------------------------------------------------

def collection_logs(log_page, data_json):
    items = json.loads(data_json)
    out = {}
    text = strip_comments(log_page)
    for tab, boss_id in LOG_SECTIONS.items():
        sec = section_text(text, 3, tab)
        names = [split_params(inner)[1][0] for _, inner in find_templates(sec) if inner.startswith('plink|')]
        tab_items = [it for it in items if any(t.lower() == tab.lower() for t in it['tabs'])]
        ids = []
        for nm in names:
            match = [it for it in tab_items if it['name'].lower() == nm.lower()] or \
                    [it for it in tab_items if it['name'].lower().startswith(nm.lower())]
            if len(match) != 1:
                raise SystemExit('log item %r (%s) matched %d data.json rows' % (nm, tab, len(match)))
            ids.append((match[0]['id'], match[0]['name']))
        if len(ids) != len(tab_items):
            raise SystemExit('%s: log page lists %d items, data.json has %d' % (tab, len(ids), len(tab_items)))
        out[boss_id] = ids
    return out


def collection_log_tabs(log_page, data_json, tabs):
    """{tab: [(item id, name)]} in log order, matched the same way as collection_logs."""
    items = json.loads(data_json)
    text = strip_comments(log_page)
    out = {}
    for tab in tabs:
        sec = section_text(text, 3, tab)
        names = [split_params(inner)[1][0] for _, inner in find_templates(sec) if inner.startswith('plink|')]
        tab_items = [it for it in items if any(t.lower() == tab.lower() for t in it['tabs'])]
        ids = []
        for nm in names:
            match = [it for it in tab_items if it['name'].lower() == nm.lower()] or \
                    [it for it in tab_items if it['name'].lower().startswith(nm.lower())]
            if len(match) != 1:
                raise SystemExit('log item %r (%s) matched %d data.json rows' % (nm, tab, len(match)))
            ids.append((match[0]['id'], match[0]['name']))
        if len(ids) != len(tab_items):
            raise SystemExit('%s: log page lists %d items, data.json has %d' % (tab, len(ids), len(tab_items)))
        out[tab] = ids
    return out


def reachable_ids(boss, shared):
    """Item IDs a boss entry in drops.json can give: always, main, table slots, tertiary rolls and the shared
    tables they lead to."""
    out = set()

    def walk(drops, depth):
        for d in drops:
            if 'table' in d:
                if depth < 3 and d['table'] in shared:
                    walk(shared[d['table']]['entries'], depth + 1)
            else:
                out.add(d['itemId'])

    for key in ('always', 'main', 'tables', 'tertiary'):
        walk(boss[key], 0)
    if 'preRoll' in boss:
        walk([boss['preRoll']], 0)
    return out


# --------------------------------------------------------------------------------------------
# Item IDs
# --------------------------------------------------------------------------------------------

def resolve_item_ids(wiki, names, log_ids):
    resolved = dict(ITEM_ID_OVERRIDES)
    for ids in log_ids.values():
        for item_id, name in ids:
            resolved.setdefault(name, item_id)
    todo = sorted(n for n in names if n not in resolved)
    rows = []
    for field in ('item_name', 'page_name'):
        pending = [n for n in todo if n not in {r['_key'] for r in rows}]
        for start in range(0, len(pending), 40):
            batch = pending[start:start + 40]
            cond = ','.join('{%s,%s}' % (lua_str(field), lua_str(n)) for n in batch)
            q = ("bucket('infobox_item').select('page_name','item_name','item_id','default_version')"
                 ".where(bucket.Or(%s)).limit(500).run()" % cond)
            for r in wiki.bucket('infobox_%s_%d_%s' % (field, start, batch[0][:30]), q):
                r['_key'] = r[field]
                rows.append(r)
    multi = []
    for n in todo:
        cands = [r for r in rows if r.get('page_name') == n or r.get('item_name') == n]
        # Prefer the item's own page (other pages can share the item name, e.g. minigame copies).
        exact = [r for r in cands if r.get('page_name') == n and r.get('item_name') == n] or \
                [r for r in cands if r.get('page_name') == n] or cands
        default = [r for r in exact if r.get('default_version') is True] or exact
        ids = []
        for r in default:
            for v in r.get('item_id') or []:
                if str(v).isdigit():
                    ids.append(int(v))
        if not ids:
            raise SystemExit('no item ID for %r' % n)
        resolved[n] = min(ids)
        if len(set(ids)) > 1:
            multi.append((n, sorted(set(ids)), min(ids)))
    return resolved, multi


def load_javap(path):
    by_id = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            m = re.search(r'public static final int (\w+) = (-?\d+);', line)
            if m:
                by_id.setdefault(int(m.group(2)), []).append(m.group(1))
    return by_id


def name_tokens(s):
    s = s.lower().replace("'", '')
    return {t for t in re.split(r'[^a-z0-9]+', s) if len(t) > 2}


# --------------------------------------------------------------------------------------------
# JSON output
# --------------------------------------------------------------------------------------------

def item_json(e, ids, weight=None, frac=None):
    d = {'itemId': ids[e.name], 'name': e.name, 'min': e.lo, 'max': e.hi}
    if e.noted:
        d['noted'] = True
    if weight is not None:
        d['weight'] = weight
    if frac is not None:
        d['numerator'] = frac.numerator
        d['denominator'] = frac.denominator
    return d


def weighted_json(entries, nested, ids, label=''):
    probs = [e.prob for e in entries] + [w for _, w in nested]
    out_of = lcm(*[p.denominator for p in probs])
    if out_of > INT_MAX:
        raise SystemExit('%s: outOf %d does not fit in a Java int (denominators %s)' % (label, out_of, sorted({p.denominator for p in probs})))
    rows = [item_json(e, ids, weight=int(e.prob * out_of)) for e in entries]
    rows += [{'table': t, 'weight': int(w * out_of)} for t, w in nested]
    total = sum(probs)
    if total > 1:
        raise SystemExit('%s: weights add up to more than 1: %s' % (label, total))
    return out_of, rows


def check_json(path):
    """Read drops.json back and print table totals, log reachability and expected kills per log item."""
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    shared = data['sharedTables']
    ok = True

    def table_prob(name, item_id, depth=0):
        t = shared[name]
        p = Fraction(0)
        for e in t['entries']:
            if 'table' in e:
                p += Fraction(e['weight'], t['outOf']) * table_prob(e['table'], item_id, depth + 1)
            elif e['itemId'] == item_id:
                p += Fraction(e['weight'], t['outOf'])
        return p

    print('\nshared tables:')
    for name, t in shared.items():
        total = sum(e['weight'] for e in t['entries'])
        flag = 'ok' if total <= t['outOf'] else 'OVER'
        ok &= total <= t['outOf']
        print('  %-32s %10d / %-10d %s' % (name, total, t['outOf'], flag))
        for e in t['entries']:
            if 'table' in e and e['table'] not in shared:
                print('    missing nested table', e['table'])
                ok = False

    def table_names(name, item_id, depth=0):
        out = set()
        for e in shared[name]['entries']:
            if 'table' in e:
                if depth < 3:
                    out |= table_names(e['table'], item_id, depth + 1)
            elif e['itemId'] == item_id:
                out.add(e['name'])
        return out

    for b in data['bosses']:
        main_total = sum(e['weight'] for e in b['main'])
        table_total = sum(t['weight'] for t in b['tables'])
        over = main_total + table_total > b['mainOutOf']
        ok &= not over
        print('\n%s (rev %s, %s hp, combat %s): rolls %d, main %d + tables %d = %d / %d %s' % (
            b['id'], b['revision'], b.get('hitpoints'), b.get('combatLevel'), b['rolls'], main_total, table_total,
            main_total + table_total, b['mainOutOf'], 'OVER' if over else 'ok'))
        for t in b['tables'] + [e for e in b['tertiary'] if 'table' in e]:
            if t['table'] not in shared:
                print('  missing table', t['table'])
                ok = False
        for e in b['tertiary']:
            if not 0 < e['numerator'] <= e['denominator']:
                print('  bad tertiary chance', e)
                ok = False
        pre = b.get('preRoll')
        if pre:
            # Rolled first: a hit rolls the table once and no main rolls are made.
            print('  pre-roll %d/%d (1 in %.2f) on %s' % (pre['numerator'], pre['denominator'],
                                                          pre['denominator'] / pre['numerator'], pre['table']))
            if pre['table'] not in shared or not 0 < pre['numerator'] <= pre['denominator'] or \
                    max(pre['numerator'], pre['denominator']) > INT_MAX:
                print('  bad pre-roll', pre)
                ok = False
        print('  log items: %d' % len(b['log']))
        for item_id in b['log']:
            q = sum((Fraction(e['weight'], b['mainOutOf']) for e in b['main'] if e['itemId'] == item_id), Fraction(0))
            q += sum((Fraction(t['weight'], b['mainOutOf']) * table_prob(t['table'], item_id) for t in b['tables']),
                     Fraction(0))
            miss = (1 - q) ** b['rolls']
            names = {e['name'] for e in b['always'] + b['main'] + b['tertiary'] if e.get('itemId') == item_id}
            for t in b['tables']:
                names |= table_names(t['table'], item_id)
            if pre:
                # (1 - p) x (1 - (1 - q)^rolls) from the main rolls, p x weight / outOf from the pre-roll's table.
                pp = Fraction(pre['numerator'], pre['denominator'])
                miss = 1 - (pp * table_prob(pre['table'], item_id) + (1 - pp) * (1 - miss))
                names |= table_names(pre['table'], item_id)
            for e in b['tertiary']:
                p = Fraction(e['numerator'], e['denominator'])
                if 'table' in e:
                    miss *= 1 - p * table_prob(e['table'], item_id)
                    names |= table_names(e['table'], item_id)
                elif e['itemId'] == item_id:
                    miss *= 1 - p
            if any(e['itemId'] == item_id for e in b['always']):
                miss = Fraction(0)
            if miss == 1:
                print('    %-6d %-30s NOT REACHABLE through drops' % (item_id, ''))
                ok = False
            else:
                print('    %-6d %-30s 1 in %.1f kills' % (item_id, ', '.join(sorted(names)), 1 / float(1 - miss)))
    return ok


def crosscheck_bucket(wiki, notes, boss_id, page, drops, quantity, templates=('DropsLine',)):
    """Check every directly listed {{DropsLine}} row (or the given row templates) against the Wiki's Bucket
    dropsline rows for its page."""
    rows = wiki.bucket('dropsline_' + page,
                       "bucket('dropsline').select('page_name_sub','item_name','drop_json')"
                       ".where('page_name',%s).limit(2000).run()" % lua_str(page))
    seen = set()
    for r in rows:
        j = json.loads(r['drop_json'])
        seen.add((j.get('Dropped item'), j.get('Rarity'), str(j.get('Quantity Low')), str(j.get('Quantity High'))))
    def same_rarity(a, b):
        try:
            return eval_arith(a) == eval_arith(b)
        except (ValueError, ZeroDivisionError):
            return a.replace(',', '') == b.replace(',', '')

    checked = 0
    for sub, tname, pos, named in templates_by_subsection(drops):
        name = (nget(named, 'bucketname') or nget(named, 'name')).strip()
        raw = strip_comments(nget(named, 'rarity')).strip()
        if tname not in templates or name in ('', 'Nothing') or '{{' in raw or not raw or \
                nget(named, 'bucket').lower() == 'no':
            continue
        try:
            lo, hi = quantity(nget(named, 'quantity', '1'))
        except ValueError:
            notes['warnings'].append('%s: %s quantity %r not checked against Bucket' % (
                boss_id, name, nget(named, 'quantity')))
            continue
        key = (name, raw, str(lo), str(hi))
        checked += 1
        if not any(k[0] == key[0] and k[2] == key[2] and k[3] == key[3] and same_rarity(k[1] or '', key[1])
                   for k in seen):
            notes['warnings'].append('%s: %s %s x%s-%s not found in Bucket dropsline' % ((boss_id,) + key))
    return checked


def bucket_quantity(q, pairs):
    """Quantity bounds as Bucket stores them. A DT2 "normal, perfect" pair is stored as low and high."""
    if pairs:
        s = re.sub(r'<ref[^>]*/>|<ref.*?</ref>', '', strip_comments(q), flags=re.S).replace('(noted)', '').strip()
        parts = re.split(r'\s*[,;]\s*', s)
        if len(parts) == 2:
            return parse_quantity(parts[0])[0], parse_quantity(parts[1])[1]
    lo, hi, _ = parse_quantity_v2(q, False)
    return lo, hi


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--cache', required=True, help='download cache folder (keep it outside the repo)')
    ap.add_argument('--out', default=os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                                                  'com', 'idlebosses', 'drops.json'))
    ap.add_argument('--itemid-javap', help='javap -constants output of net.runelite.api.gameval.ItemID')
    ap.add_argument('--offline', action='store_true', help='use only the cache')
    ap.add_argument('--date', default=datetime.date.today().isoformat(), help='fetch date for the source line')
    args = ap.parse_args()

    wiki = Wiki(args.cache, args.offline)
    # The first nine bosses' pages keep their original batches (and cache keys); the new pages follow.
    titles = [b['page'] for b in BOSSES] + EXTRA_PAGES
    pages = wiki.pages(titles)
    pages.update(wiki.pages(NEW_BOSS_PAGES))
    pages.update(wiki.pages(NEW_EXTRA_PAGES))
    pages.update(wiki.pages(CHEST_PAGES))
    pages.update(wiki.pages(RAID_PAGES))
    all_titles = titles + NEW_BOSS_PAGES + NEW_EXTRA_PAGES + CHEST_PAGES + RAID_PAGES
    notes = {'excluded': [], 'warnings': [], 'subtables': [], 'skipped': [], 'logs': []}

    subtables = {
        'HerbDropLines': parse_weighted_template(pages['Template:HerbDropLines'][2], 'herbbase'),
        'UncommonSeedDropLines': parse_weighted_template(pages['Template:UncommonSeedDropLines'][2], 'csdtbase'),
        'RareSeedDropLines': parse_weighted_template(pages['Template:RareSeedDropLines'][2], 'rsdtbase'),
        'TreeHerbSeedDropLines': parse_weighted_template(pages['Template:TreeHerbSeedDropLines'][2], 'thsdtbase'),
        'TalismanDropLines': parse_weighted_template(pages['Template:TalismanDropLines'][2], 'talbase'),
    }
    for k, (out_of, rows) in subtables.items():
        total = sum(w for _, w, _ in rows)
        if total != out_of:
            notes['warnings'].append('%s: weights add up to %d of %d (remainder is nothing)' % (k, total, out_of))
    gsdt_groups = parse_general_seed_module(pages['Module:GeneralSeedDropLines'][2])
    for threshold, denom, rows in gsdt_groups:
        if sum(w for _, _, w in rows) != denom:
            raise SystemExit('general seed group %d does not add up' % threshold)

    shared_raw = build_shared_tables(pages['Rare drop table'][2], pages['Gem drop table'][2], notes)
    extra_shared = {}
    built = {}
    for cfg in BOSSES:
        built[cfg['id']] = build_boss(cfg, pages[cfg['page']][2], subtables, gsdt_groups, notes, extra_shared)
        if cfg['id'] in CHEST_ROLLS:
            name, prob = CHEST_ROLLS[cfg['id']]
            built[cfg['id']]['tertiary'].append(Entry(name, 1, 1, False, prob))

    # The new bosses, and the shared tables only they use.
    ctx = {'pages': pages, 'notes': notes, 'subtables': subtables, 'shared_v2': {}, 'shared_sig': {},
           'multiplied': set()}
    ctx['gwd'] = build_gwd_tables(ctx)
    built_new = {cfg['id']: build_boss_v2(cfg, ctx) for cfg in NEW_BOSSES}
    for mult in sorted(ctx['multiplied']):
        for key, (entries, nested) in multiplied_tables(shared_raw, mult).items():
            register_shared(ctx, key, entries, nested)
    stats = {cfg['id']: boss_stats(cfg, pages) for cfg in BOSSES + NEW_BOSSES}

    log_ids = collection_logs(pages['Collection log'][2], pages['Module:Collection log/data.json'][2])
    new_tabs = []
    for cfg in NEW_BOSSES:
        tab = cfg.get('logTab', cfg['name'])
        if tab not in new_tabs:
            new_tabs.append(tab)
    tab_ids = collection_log_tabs(pages['Collection log'][2], pages['Module:Collection log/data.json'][2], new_tabs)

    # Cross-check every directly listed row against the wiki's Bucket dropsline rows for that page.
    for cfg in BOSSES:
        rows = wiki.bucket('dropsline_' + cfg['page'],
                           "bucket('dropsline').select('page_name_sub','item_name','drop_json')"
                           ".where('page_name',%s).limit(2000).run()" % lua_str(cfg['page']))
        seen = set()
        for r in rows:
            j = json.loads(r['drop_json'])
            seen.add((j.get('Dropped item'), j.get('Rarity'), str(j.get('Quantity Low')), str(j.get('Quantity High'))))
        drops = section_text(strip_comments(pages[cfg['page']][2]), 2, cfg['dropsHeading'])
        for sub, tname, pos, named in templates_by_subsection(drops):
            if tname != 'DropsLine' or named.get('name', '').strip() in ('Nothing',) or '{{' in named.get('rarity', ''):
                continue
            lo, hi, _ = parse_quantity(strip_comments(named.get('quantity', '1')))
            key = (named['name'].strip(), strip_comments(named['rarity']).strip(), str(lo), str(hi))
            if not any(k[0] == key[0] and k[2] == key[2] and k[3] == key[3] and
                       k[1].replace(',', '') == key[1].replace(',', '') for k in seen):
                notes['warnings'].append('%s: %s %s x%s-%s not found in Bucket dropsline' % ((cfg['id'],) + key))
    bucket_checked = 0
    for cfg in NEW_BOSSES:
        for page, heading, _ in cfg.get('merge') or [(cfg['page'], cfg.get('dropsHeading', 'Drops'), 1)]:
            drops = section_text(re.sub(r'</?onlyinclude>', '', strip_comments(pages[page][2])), 2, heading)
            bucket_checked += crosscheck_bucket(wiki, notes, cfg['id'], page, drops,
                                                lambda q, c=cfg: bucket_quantity(q, c.get('quantityPairs')))

    # Item IDs. The first nine bosses resolve exactly as before; names only the new bosses use come second,
    # with the new collection log tabs' IDs taking precedence over the infobox.
    names = set()
    for b in built.values():
        for e in b['always'] + b['main'] + b['tertiary']:
            names.add(e.name)
    for entries, _ in shared_raw.values():
        names.update(e.name for e in entries)
    for entries in extra_shared.values():
        names.update(e.name for e in entries)
    ids, multi = resolve_item_ids(wiki, names, log_ids)
    new_names = set()
    for b in built_new.values():
        for e in b['always'] + b['main'] + b['tertiary']:
            if not isinstance(e, TableRoll):
                new_names.add(e.name)
    for entries, _ in ctx['shared_v2'].values():
        new_names.update(e.name for e in entries)
    ids_new, multi_new = resolve_item_ids(wiki, {n for n in new_names if n not in ids}, tab_ids)
    conflicts = [(n, ids[n], ids_new[n]) for n in sorted(set(ids) & set(ids_new)) if ids[n] != ids_new[n]]
    for n, old_id, new_id in conflicts:
        notes['warnings'].append('item %s is %d for the first nine bosses but %d in a new log tab' % (n, old_id, new_id))
    all_ids = dict(ids_new)
    all_ids.update(ids)

    out = {'source': 'OSRS Wiki (CC BY-NC-SA 3.0), fetched %s' % args.date, 'sharedTables': {}, 'bosses': []}
    for key in ('rare_drop_table', 'gem_drop_table', 'mega_rare_drop_table'):
        entries, nested = shared_raw[key]
        out_of, rows = weighted_json(entries, nested, ids, key)
        out['sharedTables'][key] = {'outOf': out_of, 'entries': rows}
    for key, entries in sorted(extra_shared.items()):
        out_of, rows = weighted_json(entries, [], ids, key)
        out['sharedTables'][key] = {'outOf': out_of, 'entries': rows}
    for key, (entries, nested) in ctx['shared_v2'].items():
        out_of, rows = weighted_json(entries, nested, all_ids, key)
        out['sharedTables'][key] = {'outOf': out_of, 'entries': rows}

    for cfg in BOSSES:
        b = built[cfg['id']]
        main_out_of, main_rows = weighted_json(b['main'], b['tables'], ids, cfg['id'])
        out['bosses'].append({
            'id': cfg['id'],
            'name': cfg['name'],
            'wikiPage': cfg['page'],
            'revision': pages[cfg['page']][0],
            'hitpoints': stats[cfg['id']][0],
            'combatLevel': stats[cfg['id']][1],
            'rolls': b['rolls'],
            'always': [item_json(e, ids) for e in b['always']],
            'main': [r for r in main_rows if 'table' not in r],
            'mainOutOf': main_out_of,
            'tables': [r for r in main_rows if 'table' in r],
            'tertiary': [item_json(e, ids, frac=e.prob) for e in b['tertiary']],
            'log': [i for i, _ in log_ids[cfg['id']]],
        })

    tab_bosses = {}
    for cfg in NEW_BOSSES:
        tab_bosses.setdefault(cfg.get('logTab', cfg['name']), []).append(cfg['id'])
    covered = {tab: set() for tab in tab_bosses}
    for cfg in NEW_BOSSES:
        b = built_new[cfg['id']]
        main_out_of, main_rows = weighted_json(b['main'], b['tables'], all_ids, cfg['id'])
        tertiary = []
        for e in b['tertiary']:
            frac = fit_int_fraction(e.prob, '%s %s' % (cfg['id'], e.name or e.table), notes)
            if isinstance(e, TableRoll):
                tertiary.append({'table': e.table, 'numerator': frac.numerator, 'denominator': frac.denominator})
            else:
                tertiary.append(item_json(e, all_ids, frac=frac))
        entry = {
            'id': cfg['id'],
            'name': cfg['name'],
            'wikiPage': cfg['page'],
            'revision': pages[cfg['page']][0],
            'hitpoints': stats[cfg['id']][0],
            'combatLevel': stats[cfg['id']][1],
            'rolls': b['rolls'],
            'always': [item_json(e, all_ids) for e in b['always']],
            'main': [r for r in main_rows if 'table' not in r],
            'mainOutOf': main_out_of,
            'tables': [r for r in main_rows if 'table' in r],
            'tertiary': tertiary,
            'log': [],
        }
        tab = cfg.get('logTab', cfg['name'])
        reach = reachable_ids(entry, out['sharedTables'])
        entry['log'] = [i for i, _ in tab_ids[tab] if i in reach]
        covered[tab].update(entry['log'])
        missing = [(i, n) for i, n in tab_ids[tab] if i not in reach]
        if missing and len(tab_bosses[tab]) == 1:
            raise SystemExit('%s: log items never drop: %s' % (cfg['id'], missing))
        if missing:
            notes['logs'].append('%s: from the "%s" tab, leaves out %s' % (
                cfg['id'], tab, ', '.join(n for _, n in missing)))
        out['bosses'].append(entry)
    for tab, got in covered.items():
        lost = [n for i, n in tab_ids[tab] if i not in got]
        if lost:
            raise SystemExit('"%s" tab items no boss here can drop: %s' % (tab, lost))

    # The reward-chest, wave and reward-pool bosses, built last with a registry of their own, so everything above
    # (including its item lookups) is exactly as before.
    cctx = {'pages': pages, 'notes': notes, 'subtables': subtables, 'shared_v2': {}, 'shared_sig': {},
            'multiplied': set(), 'gwd': ctx['gwd']}
    built_chest = {cfg['id']: build_chest_boss(cfg, cctx) for cfg in CHEST_BOSSES}
    for cfg in CHEST_BOSSES:
        stats[cfg['id']] = chest_stats(cfg, pages)
    chest_tabs = []
    for cfg in CHEST_BOSSES:
        if cfg['logTab'] not in chest_tabs:
            chest_tabs.append(cfg['logTab'])
    chest_tab_ids, chest_aliases = collection_log_tabs_chest(pages['Collection log'][2],
                                                             pages['Module:Collection log/data.json'][2], chest_tabs)
    chest_checked, checked_parts = 0, set()
    for cfg in CHEST_BOSSES:
        if cfg['kind'] == 'tempoross':
            parts = [('Reward pool', page_text(cctx, t)) for t in tempoross_templates(cfg).values()]
        else:
            parts = [(cfg['page'], section_text(page_text(cctx, cfg['page']), 2, cfg.get('dropsHeading', 'Drops')))]
        for page, text in parts:
            if (page, text) not in checked_parts:
                checked_parts.add((page, text))
                chest_checked += crosscheck_bucket(wiki, notes, cfg['id'], page, text,
                                                   lambda q: bucket_quantity(q, False),
                                                   templates=('DropsLine', 'DropsLineReward'))
    chest_names = set()
    for b in built_chest.values():
        for e in b['always'] + b['main'] + b['tertiary']:
            if not isinstance(e, TableRoll):
                chest_names.add(e.name)
    for entries, _ in cctx['shared_v2'].values():
        chest_names.update(e.name for e in entries)
    seed = dict(chest_tab_ids, _aliases=chest_aliases)
    ids_chest, multi_chest = resolve_item_ids(wiki, {n for n in chest_names if n not in all_ids}, seed)
    for n in sorted(set(all_ids) & set(ids_chest)):
        if all_ids[n] != ids_chest[n]:
            notes['warnings'].append('item %s is %d for the bosses above but %d in a chest boss log tab' % (
                n, all_ids[n], ids_chest[n]))
    chest_ids = dict(ids_chest)
    chest_ids.update(all_ids)

    for key, (entries, nested) in cctx['shared_v2'].items():
        if key in out['sharedTables']:
            raise SystemExit('chest shared table %s is already defined' % key)
        out_of, rows = weighted_json(entries, nested, chest_ids, key)
        out['sharedTables'][key] = {'outOf': out_of, 'entries': rows}
    chest_tab_bosses = {}
    for cfg in CHEST_BOSSES:
        chest_tab_bosses.setdefault(cfg['logTab'], []).append(cfg)
    chest_covered = {tab: set() for tab in chest_tab_bosses}
    for cfg in CHEST_BOSSES:
        b = built_chest[cfg['id']]
        main_out_of, main_rows = weighted_json(b['main'], b['tables'], chest_ids, cfg['id'])
        tertiary = []
        for e in b['tertiary']:
            frac = fit_int_fraction(e.prob, '%s %s' % (cfg['id'], e.name or e.table), notes)
            if isinstance(e, TableRoll):
                tertiary.append({'table': e.table, 'numerator': frac.numerator, 'denominator': frac.denominator})
            else:
                tertiary.append(item_json(e, chest_ids, frac=frac))
        entry = {
            'id': cfg['id'],
            'name': cfg['name'],
            'wikiPage': cfg['page'],
            'revision': pages[cfg['page']][0],
            'hitpoints': stats[cfg['id']][0],
            'combatLevel': stats[cfg['id']][1],
            'rolls': b['rolls'],
            'always': [item_json(e, chest_ids) for e in b['always']],
            'main': [r for r in main_rows if 'table' not in r],
            'mainOutOf': main_out_of,
            'tables': [r for r in main_rows if 'table' in r],
            'tertiary': tertiary,
            'log': [],
        }
        tab, skip = cfg['logTab'], cfg.get('notInLog', {})
        reach = reachable_ids(entry, out['sharedTables'])
        tab_names = {n for _, n in chest_tab_ids[tab]}
        for n in skip:
            if n not in tab_names or chest_ids.get(n) in reach:
                raise SystemExit('%s: notInLog item %s is not an unreachable item of the "%s" tab' % (cfg['id'], n, tab))
            notes['logs'].append('%s: leaves out %s from the "%s" tab: %s' % (cfg['id'], n, tab, skip[n]))
        entry['log'] = [i for i, n in chest_tab_ids[tab] if i in reach]
        chest_covered[tab].update(entry['log'])
        missing = [n for i, n in chest_tab_ids[tab] if i not in reach and n not in skip]
        if missing and len(chest_tab_bosses[tab]) == 1:
            raise SystemExit('%s: log items never drop: %s' % (cfg['id'], missing))
        if missing:
            notes['logs'].append('%s: from the "%s" tab, leaves out %s' % (cfg['id'], tab, ', '.join(missing)))
        out['bosses'].append(entry)
    for tab, got in chest_covered.items():
        skipped = {n for c in chest_tab_bosses[tab] for n in c.get('notInLog', {})}
        lost = [n for i, n in chest_tab_ids[tab] if i not in got and n not in skipped]
        if lost:
            raise SystemExit('"%s" tab items no boss here can drop: %s' % (tab, lost))

    # The four raids, built last with a registry and item lookup of their own, so everything above (including its
    # item lookups) is exactly as before.
    rctx = {'pages': pages, 'notes': notes, 'subtables': subtables, 'shared_v2': {}, 'shared_sig': {},
            'multiplied': set(), 'gwd': ctx['gwd']}
    built_raid = {cfg['id']: build_raid_boss(cfg, rctx) for cfg in RAID_BOSSES}
    for cfg in RAID_BOSSES:
        stats[cfg['id']] = raid_stats(cfg, pages)
    raid_tab_ids, raid_aliases = collection_log_tabs_chest(pages['Collection log'][2],
                                                           pages['Module:Collection log/data.json'][2],
                                                           [cfg['logTab'] for cfg in RAID_BOSSES])
    raid_checked = 0
    for cfg in RAID_BOSSES:
        raid_checked += crosscheck_bucket(wiki, notes, cfg['id'], cfg['page'],
                                          section_text(page_text(rctx, cfg['page']), 2, cfg['dropsHeading']),
                                          lambda q: bucket_quantity(q, False),
                                          templates=('DropsLine', 'DropsLineReward'))
    raid_checked += crosscheck_bucket(wiki, notes, 'chambers_of_xeric', 'Tekton',
                                      section_text(page_text(rctx, 'Tekton'), 2, 'Drops'),
                                      lambda q: bucket_quantity(q, False))
    raid_names = set()
    for b in built_raid.values():
        for e in b['always'] + b['main'] + b['tertiary']:
            if not isinstance(e, TableRoll):
                raid_names.add(e.name)
    for entries, _ in rctx['shared_v2'].values():
        raid_names.update(e.name for e in entries)
    ids_raid, multi_raid = resolve_item_ids(wiki, {n for n in raid_names if n not in chest_ids},
                                            dict(raid_tab_ids, _aliases=raid_aliases))
    for n in sorted(set(chest_ids) & set(ids_raid)):
        if chest_ids[n] != ids_raid[n]:
            notes['warnings'].append('item %s is %d for the bosses above but %d in a raid log tab' % (
                n, chest_ids[n], ids_raid[n]))
    raid_ids = dict(ids_raid)
    raid_ids.update(chest_ids)

    for key, (entries, nested) in rctx['shared_v2'].items():
        if key in out['sharedTables']:
            raise SystemExit('raid shared table %s is already defined' % key)
        out_of, rows = weighted_json(entries, nested, raid_ids, key)
        out['sharedTables'][key] = {'outOf': out_of, 'entries': rows}
    for cfg in RAID_BOSSES:
        b = built_raid[cfg['id']]
        main_out_of, main_rows = weighted_json(b['main'], b['tables'], raid_ids, cfg['id'])
        table, pre = b['preRoll']
        pre = fit_int_fraction(pre, '%s pre-roll' % cfg['id'], notes)
        tertiary = []
        for e in b['tertiary']:
            frac = fit_int_fraction(e.prob, '%s %s' % (cfg['id'], e.name or e.table), notes)
            if isinstance(e, TableRoll):
                tertiary.append({'table': e.table, 'numerator': frac.numerator, 'denominator': frac.denominator})
            else:
                tertiary.append(item_json(e, raid_ids, frac=frac))
        entry = {
            'id': cfg['id'],
            'name': cfg['name'],
            'wikiPage': cfg['page'],
            'revision': pages[cfg['page']][0],
            'hitpoints': stats[cfg['id']][0],
            'combatLevel': stats[cfg['id']][1],
            'rolls': b['rolls'],
            'preRoll': {'table': table, 'numerator': pre.numerator, 'denominator': pre.denominator},
            'always': [item_json(e, raid_ids) for e in b['always']],
            'main': [r for r in main_rows if 'table' not in r],
            'mainOutOf': main_out_of,
            'tables': [r for r in main_rows if 'table' in r],
            'tertiary': tertiary,
            'log': [],
        }
        tab, skip = cfg['logTab'], dict(cfg.get('notInLog', {}), **b.get('notInLog', {}))
        reach = reachable_ids(entry, out['sharedTables'])
        tab_names = {n for _, n in raid_tab_ids[tab]}
        for n in skip:
            if n not in tab_names or raid_ids.get(n) in reach:
                raise SystemExit('%s: notInLog item %s is not an unreachable item of the "%s" tab' % (
                    cfg['id'], n, tab))
            notes['logs'].append('%s: leaves out %s from the "%s" tab: %s' % (cfg['id'], n, tab, skip[n]))
        entry['log'] = [i for i, n in raid_tab_ids[tab] if i in reach]
        missing = [n for i, n in raid_tab_ids[tab] if i not in reach and n not in skip]
        if missing:
            raise SystemExit('%s: log items never drop: %s' % (cfg['id'], missing))
        out['bosses'].append(entry)

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, 'w', encoding='utf-8') as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write('\n')

    # ---------------------------------------------------------------- report
    print('wrote', os.path.abspath(args.out))
    print('\nrevisions:')
    for t in all_titles:
        print('  %-44s rev %-10s %s' % (t, pages[t][0], pages[t][1]))
    print('\nhitpoints and combat level:')
    for cfg in BOSSES + NEW_BOSSES + CHEST_BOSSES + RAID_BOSSES:
        hp, combat, desc = stats[cfg['id']]
        print('  %-26s %5d hp  combat %4d  (%s)' % (cfg['id'], hp, combat, desc))
    print('\nexcluded drops:')
    for x in notes['excluded']:
        print('  %s: %s (%s) - %s' % x)
    print('\nrows not used:')
    for x in notes['skipped']:
        print('  %s: %s (%s) - %s' % x)
    print('\nnotes:')
    for w in notes['warnings']:
        print('  ' + w)
    print('\nshared collection log tabs:')
    for w in notes['logs']:
        print('  ' + w)
    print('\nsub-tables folded/linked:')
    for s in notes['subtables']:
        print('  %s: %s access %s arg %s outOf %s' % s)
    print('\nnew-boss rows checked against Bucket dropsline: %d' % bucket_checked)
    print('chest-boss rows checked against Bucket dropsline: %d' % chest_checked)
    print('raid rows checked against Bucket dropsline: %d' % raid_checked)
    print('\nnames with several item IDs (lowest used unless it is a log item):')
    for n, all_list, chosen in multi + multi_new + multi_chest + multi_raid:
        print('  %s: %s -> %d' % (n, all_list, chosen))
    if args.itemid_javap:
        by_id = load_javap(args.itemid_javap)
        print('\nitem ID check against RuneLite gameval ItemID:')
        bad = False
        everything = names | new_names | {nm for v in log_ids.values() for _, nm in v} | \
            {nm for v in tab_ids.values() for _, nm in v} | chest_names | \
            {nm for v in chest_tab_ids.values() for _, nm in v} | raid_names | \
            {nm for v in raid_tab_ids.values() for _, nm in v}
        for n in sorted(everything):
            i = raid_ids[n]
            consts = by_id.get(i)
            if not consts:
                print('  MISSING  %-32s %d' % (n, i))
                bad = True
                continue
            overlap = any(t in c.lower().replace('_', '') for c in consts for t in name_tokens(n))
            print('  %-8s %-32s %-6d %s' % ('ok' if overlap else 'CHECK', n, i, ','.join(consts)))
        if bad:
            raise SystemExit('some item IDs are not in RuneLite ItemID')
    if not check_json(args.out):
        raise SystemExit('drops.json failed its checks')


if __name__ == '__main__':
    main()
