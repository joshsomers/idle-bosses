# Drop table sources

The drop data in `src/main/resources/com/idlebosses/drops.json` comes from the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki), licensed under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).

The first nine bosses' pages were fetched on **2026-11-03**. The 44 bosses added afterwards, and
the pages only they use, were fetched on **2026-11-04**, as were the 13 reward-chest, wave and
reward-pool bosses added after them and the four raids added last. Everything was fetched by
`tools/fetch_drops.py`.

## Regenerating

```sh
javap -constants -cp <runelite-api.jar> net.runelite.api.gameval.ItemID > /tmp/scratch/itemid.txt
python3 -I tools/fetch_drops.py --cache /tmp/scratch/wiki-cache \
    --out src/main/resources/com/idlebosses/drops.json \
    --itemid-javap /tmp/scratch/itemid.txt --date "2026-11-03 to 2026-11-04"
```

Keep the cache folder outside the repo. `--offline` rebuilds from the cache. The script checks
every item ID against RuneLite's `ItemID`. It then reads the JSON back and prints, for each table,
its total weight against its `outOf`, plus the expected kills for each collection log item.

The first nine bosses' pages and item lookups are fetched in the same batches as before, so a
rebuild leaves their entries as they were. The added bosses' pages are separate batches. The 13
chest bosses are built after the other 53, with shared tables and item lookups of their own, so a
rebuild leaves the 53 entries and their 33 shared tables byte-for-byte as they were. The four raids
are built last in the same way, with their own page batches (`RAID_PAGES`), shared tables and item
lookups, so the 66 entries before them and their 51 shared tables come out byte-for-byte as before.
A page batch's cache key is its first 150 characters of titles, so a page added to `RAID_PAGES`
has to go where it changes them (otherwise the old batch is read back from the cache without it).

## Boss pages

| Boss | Wiki page | Revision | Fetched |
|---|---|---|---|
| Obor | [Obor](https://oldschool.runescape.wiki/w/Obor) | [15383989](https://oldschool.runescape.wiki/?oldid=15383989) | 2026-11-03 |
| Bryophyta | [Bryophyta](https://oldschool.runescape.wiki/w/Bryophyta) | [15392719](https://oldschool.runescape.wiki/?oldid=15392719) | 2026-11-03 |
| Scurrius | [Scurrius](https://oldschool.runescape.wiki/w/Scurrius) | [15383993](https://oldschool.runescape.wiki/?oldid=15383993) | 2026-11-03 |
| Giant Mole | [Giant Mole](https://oldschool.runescape.wiki/w/Giant_Mole) | [15361720](https://oldschool.runescape.wiki/?oldid=15361720) | 2026-11-03 |
| Sarachnis | [Sarachnis](https://oldschool.runescape.wiki/w/Sarachnis) | [15383980](https://oldschool.runescape.wiki/?oldid=15383980) | 2026-11-03 |
| King Black Dragon | [King Black Dragon](https://oldschool.runescape.wiki/w/King_Black_Dragon) | [15385987](https://oldschool.runescape.wiki/?oldid=15385987) | 2026-11-03 |
| Kraken | [Kraken](https://oldschool.runescape.wiki/w/Kraken) | [15383981](https://oldschool.runescape.wiki/?oldid=15383981) | 2026-11-03 |
| Zulrah | [Zulrah](https://oldschool.runescape.wiki/w/Zulrah) | [15385964](https://oldschool.runescape.wiki/?oldid=15385964) | 2026-11-03 |
| Vorkath | [Vorkath](https://oldschool.runescape.wiki/w/Vorkath) | [15392908](https://oldschool.runescape.wiki/?oldid=15392908) | 2026-11-03 |
| Abyssal Sire | [Abyssal Sire](https://oldschool.runescape.wiki/w/Abyssal_Sire) | [15392068](https://oldschool.runescape.wiki/?oldid=15392068) | 2026-11-04 |
| Alchemical Hydra | [Alchemical Hydra](https://oldschool.runescape.wiki/w/Alchemical_Hydra) | [15375803](https://oldschool.runescape.wiki/?oldid=15375803) | 2026-11-04 |
| Amoxliatl | [Amoxliatl](https://oldschool.runescape.wiki/w/Amoxliatl) | [15383998](https://oldschool.runescape.wiki/?oldid=15383998) | 2026-11-04 |
| Araxxor | [Araxxor](https://oldschool.runescape.wiki/w/Araxxor) | [15384709](https://oldschool.runescape.wiki/?oldid=15384709) | 2026-11-04 |
| Brutus | [Brutus](https://oldschool.runescape.wiki/w/Brutus) | [15386320](https://oldschool.runescape.wiki/?oldid=15386320) | 2026-11-04 |
| Callisto | [Callisto](https://oldschool.runescape.wiki/w/Callisto) | [15360463](https://oldschool.runescape.wiki/?oldid=15360463) | 2026-11-04 |
| Artio | [Artio](https://oldschool.runescape.wiki/w/Artio) | [15358758](https://oldschool.runescape.wiki/?oldid=15358758) | 2026-11-04 |
| Cerberus | [Cerberus](https://oldschool.runescape.wiki/w/Cerberus) | [15363026](https://oldschool.runescape.wiki/?oldid=15363026) | 2026-11-04 |
| Chaos Elemental | [Chaos Elemental](https://oldschool.runescape.wiki/w/Chaos_Elemental) | [15346650](https://oldschool.runescape.wiki/?oldid=15346650) | 2026-11-04 |
| Chaos Fanatic | [Chaos Fanatic](https://oldschool.runescape.wiki/w/Chaos_Fanatic) | [15346616](https://oldschool.runescape.wiki/?oldid=15346616) | 2026-11-04 |
| Commander Zilyana | [Commander Zilyana](https://oldschool.runescape.wiki/w/Commander_Zilyana) | [15389109](https://oldschool.runescape.wiki/?oldid=15389109) | 2026-11-04 |
| Corporeal Beast | [Corporeal Beast](https://oldschool.runescape.wiki/w/Corporeal_Beast) | [15364759](https://oldschool.runescape.wiki/?oldid=15364759) | 2026-11-04 |
| Crazy archaeologist | [Crazy archaeologist](https://oldschool.runescape.wiki/w/Crazy_archaeologist) | [15374897](https://oldschool.runescape.wiki/?oldid=15374897) | 2026-11-04 |
| Dagannoth Rex | [Dagannoth Rex](https://oldschool.runescape.wiki/w/Dagannoth_Rex) | [15341726](https://oldschool.runescape.wiki/?oldid=15341726) | 2026-11-04 |
| Dagannoth Prime | [Dagannoth Prime](https://oldschool.runescape.wiki/w/Dagannoth_Prime) | [15360344](https://oldschool.runescape.wiki/?oldid=15360344) | 2026-11-04 |
| Dagannoth Supreme | [Dagannoth Supreme](https://oldschool.runescape.wiki/w/Dagannoth_Supreme) | [15357392](https://oldschool.runescape.wiki/?oldid=15357392) | 2026-11-04 |
| Deranged archaeologist | [Deranged archaeologist](https://oldschool.runescape.wiki/w/Deranged_archaeologist) | [15392075](https://oldschool.runescape.wiki/?oldid=15392075) | 2026-11-04 |
| Duke Sucellus | [Duke Sucellus](https://oldschool.runescape.wiki/w/Duke_Sucellus) | [15385988](https://oldschool.runescape.wiki/?oldid=15385988) | 2026-11-04 |
| General Graardor | [General Graardor](https://oldschool.runescape.wiki/w/General_Graardor) | [15337045](https://oldschool.runescape.wiki/?oldid=15337045) | 2026-11-04 |
| Grotesque Guardians | [Grotesque Guardians](https://oldschool.runescape.wiki/w/Grotesque_Guardians) | [15387555](https://oldschool.runescape.wiki/?oldid=15387555) | 2026-11-04 |
| Hespori | [Hespori](https://oldschool.runescape.wiki/w/Hespori) | [15387550](https://oldschool.runescape.wiki/?oldid=15387550) | 2026-11-04 |
| The Hueycoatl | [The Hueycoatl](https://oldschool.runescape.wiki/w/The_Hueycoatl) | [15383996](https://oldschool.runescape.wiki/?oldid=15383996) | 2026-11-04 |
| Kalphite Queen | [Kalphite Queen](https://oldschool.runescape.wiki/w/Kalphite_Queen) | [15387547](https://oldschool.runescape.wiki/?oldid=15387547) | 2026-11-04 |
| Kree'arra | [Kree'arra](https://oldschool.runescape.wiki/w/Kree%27arra) | [15337073](https://oldschool.runescape.wiki/?oldid=15337073) | 2026-11-04 |
| K'ril Tsutsaroth | [K'ril Tsutsaroth](https://oldschool.runescape.wiki/w/K%27ril_Tsutsaroth) | [15357597](https://oldschool.runescape.wiki/?oldid=15357597) | 2026-11-04 |
| The Leviathan | [The Leviathan](https://oldschool.runescape.wiki/w/The_Leviathan) | [15385989](https://oldschool.runescape.wiki/?oldid=15385989) | 2026-11-04 |
| The Mad Angel | [Mad Angel](https://oldschool.runescape.wiki/w/Mad_Angel) | [15366654](https://oldschool.runescape.wiki/?oldid=15366654) | 2026-11-04 |
| Maggot King | [Maggot King](https://oldschool.runescape.wiki/w/Maggot_King) | [15383453](https://oldschool.runescape.wiki/?oldid=15383453) | 2026-11-04 |
| Nex | [Nex](https://oldschool.runescape.wiki/w/Nex) | [15392072](https://oldschool.runescape.wiki/?oldid=15392072) | 2026-11-04 |
| The Nightmare | [The Nightmare](https://oldschool.runescape.wiki/w/The_Nightmare) | [15360502](https://oldschool.runescape.wiki/?oldid=15360502) | 2026-11-04 |
| Phosani's Nightmare | [Phosani's Nightmare](https://oldschool.runescape.wiki/w/Phosani%27s_Nightmare) | [15387587](https://oldschool.runescape.wiki/?oldid=15387587) | 2026-11-04 |
| Phantom Muspah | [Phantom Muspah](https://oldschool.runescape.wiki/w/Phantom_Muspah) | [15392069](https://oldschool.runescape.wiki/?oldid=15392069) | 2026-11-04 |
| Royal Titans | [Royal Titans](https://oldschool.runescape.wiki/w/Royal_Titans) | [15384036](https://oldschool.runescape.wiki/?oldid=15384036) | 2026-11-04 |
| Scorpia | [Scorpia](https://oldschool.runescape.wiki/w/Scorpia) | [15362166](https://oldschool.runescape.wiki/?oldid=15362166) | 2026-11-04 |
| Shellbane Gryphon | [Shellbane gryphon](https://oldschool.runescape.wiki/w/Shellbane_gryphon) | [15387213](https://oldschool.runescape.wiki/?oldid=15387213) | 2026-11-04 |
| Skotizo | [Skotizo](https://oldschool.runescape.wiki/w/Skotizo) | [15384031](https://oldschool.runescape.wiki/?oldid=15384031) | 2026-11-04 |
| Thermonuclear smoke devil | [Thermonuclear smoke devil](https://oldschool.runescape.wiki/w/Thermonuclear_smoke_devil) | [15369525](https://oldschool.runescape.wiki/?oldid=15369525) | 2026-11-04 |
| Vardorvis | [Vardorvis](https://oldschool.runescape.wiki/w/Vardorvis) | [15385991](https://oldschool.runescape.wiki/?oldid=15385991) | 2026-11-04 |
| Venenatis | [Venenatis](https://oldschool.runescape.wiki/w/Venenatis) | [15361982](https://oldschool.runescape.wiki/?oldid=15361982) | 2026-11-04 |
| Spindel | [Spindel](https://oldschool.runescape.wiki/w/Spindel) | [15358779](https://oldschool.runescape.wiki/?oldid=15358779) | 2026-11-04 |
| Vet'ion | [Vet'ion](https://oldschool.runescape.wiki/w/Vet%27ion) | [15360817](https://oldschool.runescape.wiki/?oldid=15360817) | 2026-11-04 |
| Calvar'ion | [Calvar'ion](https://oldschool.runescape.wiki/w/Calvar%27ion) | [15375801](https://oldschool.runescape.wiki/?oldid=15375801) | 2026-11-04 |
| The Whisperer | [The Whisperer](https://oldschool.runescape.wiki/w/The_Whisperer) | [15387570](https://oldschool.runescape.wiki/?oldid=15387570) | 2026-11-04 |
| Yama | [Yama](https://oldschool.runescape.wiki/w/Yama) | [15387136](https://oldschool.runescape.wiki/?oldid=15387136) | 2026-11-04 |
| Blue Moon, Eclipse Moon, Blood Moon | [Lunar Chest](https://oldschool.runescape.wiki/w/Lunar_Chest) | [15346700](https://oldschool.runescape.wiki/?oldid=15346700) | 2026-11-04 |
| Barrows | [Chest (Barrows)](https://oldschool.runescape.wiki/w/Chest_(Barrows)) | [15390031](https://oldschool.runescape.wiki/?oldid=15390031) | 2026-11-04 |
| The Gauntlet, The Corrupted Gauntlet | [Reward Chest (The Gauntlet)](https://oldschool.runescape.wiki/w/Reward_Chest_(The_Gauntlet)) | [15372255](https://oldschool.runescape.wiki/?oldid=15372255) | 2026-11-04 |
| TzTok-Jad | [TzTok-Jad](https://oldschool.runescape.wiki/w/TzTok-Jad) | [15389114](https://oldschool.runescape.wiki/?oldid=15389114) | 2026-11-04 |
| TzKal-Zuk | [TzKal-Zuk](https://oldschool.runescape.wiki/w/TzKal-Zuk) | [15390730](https://oldschool.runescape.wiki/?oldid=15390730) | 2026-11-04 |
| Sol Heredit | [Rewards Chest (Fortis Colosseum)](https://oldschool.runescape.wiki/w/Rewards_Chest_(Fortis_Colosseum)) | [15337621](https://oldschool.runescape.wiki/?oldid=15337621) | 2026-11-04 |
| Doom of Mokhaiotl | [Doom of Mokhaiotl](https://oldschool.runescape.wiki/w/Doom_of_Mokhaiotl) | [15387556](https://oldschool.runescape.wiki/?oldid=15387556) | 2026-11-04 |
| Wintertodt | [Reward Cart](https://oldschool.runescape.wiki/w/Reward_Cart) | [15388221](https://oldschool.runescape.wiki/?oldid=15388221) | 2026-11-04 |
| Tempoross | [Reward pool](https://oldschool.runescape.wiki/w/Reward_pool) | [15208420](https://oldschool.runescape.wiki/?oldid=15208420) | 2026-11-04 |
| Zalcano | [Zalcano](https://oldschool.runescape.wiki/w/Zalcano) | [15358247](https://oldschool.runescape.wiki/?oldid=15358247) | 2026-11-04 |
| Chambers of Xeric | [Ancient chest](https://oldschool.runescape.wiki/w/Ancient_chest) | [15387491](https://oldschool.runescape.wiki/?oldid=15387491) | 2026-11-04 |
| Theatre of Blood | [Monumental chest](https://oldschool.runescape.wiki/w/Monumental_chest) | [15354643](https://oldschool.runescape.wiki/?oldid=15354643) | 2026-11-04 |
| Tombs of Amascut | [Chest (Tombs of Amascut)](https://oldschool.runescape.wiki/w/Chest_(Tombs_of_Amascut)) | [15385393](https://oldschool.runescape.wiki/?oldid=15385393) | 2026-11-04 |
| The Fractured Archive | [Chest (The Fractured Archive)](https://oldschool.runescape.wiki/w/Chest_(The_Fractured_Archive)) | [15392798](https://oldschool.runescape.wiki/?oldid=15392798) | 2026-11-04 |

The user-facing names are kept where the page title differs: "The Mad Angel" is the page
`Mad Angel`, and "Shellbane Gryphon" is `Shellbane gryphon` (both are redirects on the Wiki).
The Royal Titans' drops are on the two titans' own pages (below). For the 13 chest bosses and
the four raids the page is where the run's loot is listed, and `wikiPage` in `drops.json` names
it.

The Fractured Archive was released on 20 October 2026 and is still being balanced. Its chest
page is used at revision 15392798 (3 November 2026). The raid page says the team-size bonus to
the unique chance "is something they'll be looking at in next week's balancing changes
(11/09/26)", so that raid should be rebuilt after the next balancing update.

## Supporting pages

| Used for | Page | Revision |
|---|---|---|
| `rare_drop_table` | [Rare drop table](https://oldschool.runescape.wiki/w/Rare_drop_table) | 15339986 |
| `gem_drop_table`, `mega_rare_drop_table` | [Gem drop table](https://oldschool.runescape.wiki/w/Gem_drop_table) | 15167578 |
| Collection log order | [Collection log](https://oldschool.runescape.wiki/w/Collection_log) | 15391852 |
| Collection log item IDs | [Module:Collection log/data.json](https://oldschool.runescape.wiki/w/Module:Collection_log/data.json) | 15377615 |
| Herb drop table weights | [Template:HerbDropLines](https://oldschool.runescape.wiki/w/Template:HerbDropLines) | 15325751 |
| Uncommon seed table weights | [Template:UncommonSeedDropLines](https://oldschool.runescape.wiki/w/Template:UncommonSeedDropLines) | 15338446 |
| Rare seed table weights | [Template:RareSeedDropLines](https://oldschool.runescape.wiki/w/Template:RareSeedDropLines) | 15325837 |
| Tree-herb seed table weights | [Template:TreeHerbSeedDropLines](https://oldschool.runescape.wiki/w/Template:TreeHerbSeedDropLines) | 15325890 |
| General seed table weights | [Module:GeneralSeedDropLines](https://oldschool.runescape.wiki/w/Module:GeneralSeedDropLines) | 14832486 |
| Notes only (log items not in drops) | [Chest (Obor's lair)](https://oldschool.runescape.wiki/w/Chest_(Obor%27s_lair)) | 15339759 |
| Notes only (log items not in drops) | [Chest (Bryophyta's lair)](https://oldschool.runescape.wiki/w/Chest_(Bryophyta%27s_lair)) | 15391888 |
| `gwd_rare_drop_table`, `gwd_gem_drop_table`, `gwd_mega_rare_drop_table` | [God Wars Dungeon-variant rare drop table](https://oldschool.runescape.wiki/w/God_Wars_Dungeon-variant_rare_drop_table) | 15392103 |
| GWD table chances, and the flattened rates they are checked against | [Template:GWDRDT](https://oldschool.runescape.wiki/w/Template:GWDRDT) | 15392194 |
| The formula behind those flattened rates | [Template:CalculateRDTNaked](https://oldschool.runescape.wiki/w/Template:CalculateRDTNaked) | 14009946 |
| Talisman table weights (Abyssal Sire, Dagannoth Kings) | [Template:TalismanDropLines](https://oldschool.runescape.wiki/w/Template:TalismanDropLines) | 14831807 |
| Fossil rows (Deranged archaeologist) | [Template:FossilDropLines](https://oldschool.runescape.wiki/w/Template:FossilDropLines) | 15300668 |
| `corporeal_beast_sigil_table` | [Template:Uniques/Corporeal Beast](https://oldschool.runescape.wiki/w/Template:Uniques/Corporeal_Beast) | 14141207 |
| Notes only (the Wilderness Slayer table, left out) | [Template:WildernessSlayerDropTable](https://oldschool.runescape.wiki/w/Template:WildernessSlayerDropTable) | 14994091 |
| Royal Titans loot | [Branda the Fire Queen](https://oldschool.runescape.wiki/w/Branda_the_Fire_Queen) | 15390244 |
| Royal Titans loot | [Eldric the Ice King](https://oldschool.runescape.wiki/w/Eldric_the_Ice_King) | 15390296 |
| `font_of_consumption` | [Unsired](https://oldschool.runescape.wiki/w/Unsired) (Rewards) | 15371302 |
| `dossier` | [Dossier](https://oldschool.runescape.wiki/w/Dossier) | 15314766 |
| `forgotten_lockbox` | [Forgotten lockbox](https://oldschool.runescape.wiki/w/Forgotten_lockbox) | 15371386 |
| Notes only | [The Font of Consumption](https://oldschool.runescape.wiki/w/The_Font_of_Consumption) | 14412664 |
| Notes only (where the rite comes from) | [Rite of vile transference](https://oldschool.runescape.wiki/w/Rite_of_vile_transference) | 15387104 |
| Hitpoints | [Dusk](https://oldschool.runescape.wiki/w/Dusk) 15352439, [Dawn](https://oldschool.runescape.wiki/w/Dawn) 15303778 | |
| Hitpoints | [Skeleton Hellhound (Vet'ion)](https://oldschool.runescape.wiki/w/Skeleton_Hellhound_(Vet%27ion)) 15358786, [Greater Skeleton Hellhound (Vet'ion)](https://oldschool.runescape.wiki/w/Greater_Skeleton_Hellhound_(Vet%27ion)) 15358787 | |
| Hitpoints | [Skeleton Hellhound (Calvar'ion)](https://oldschool.runescape.wiki/w/Skeleton_Hellhound_(Calvar%27ion)) 15358761, [Greater Skeleton Hellhound (Calvar'ion)](https://oldschool.runescape.wiki/w/Greater_Skeleton_Hellhound_(Calvar%27ion)) 15370225 | |
| Hitpoints | [Fumus](https://oldschool.runescape.wiki/w/Fumus) 15365819, [Umbra](https://oldschool.runescape.wiki/w/Umbra) 15365825, [Cruor](https://oldschool.runescape.wiki/w/Cruor) 15365818, [Glacies](https://oldschool.runescape.wiki/w/Glacies) 15365820 | |
| Hitpoints | [Judge of Yama](https://oldschool.runescape.wiki/w/Judge_of_Yama) 15215874 | |
| Hitpoints (the Moons) | [Blue Moon](https://oldschool.runescape.wiki/w/Blue_Moon) 15392867, [Eclipse Moon](https://oldschool.runescape.wiki/w/Eclipse_Moon) 15383986, [Blood Moon](https://oldschool.runescape.wiki/w/Blood_Moon) 15383459 | |
| Hitpoints (Barrows) | [Ahrim the Blighted](https://oldschool.runescape.wiki/w/Ahrim_the_Blighted) 15199256, [Dharok the Wretched](https://oldschool.runescape.wiki/w/Dharok_the_Wretched) 15316733, [Guthan the Infested](https://oldschool.runescape.wiki/w/Guthan_the_Infested) 15316734, [Karil the Tainted](https://oldschool.runescape.wiki/w/Karil_the_Tainted) 15317918, [Torag the Corrupted](https://oldschool.runescape.wiki/w/Torag_the_Corrupted) 15316735, [Verac the Defiled](https://oldschool.runescape.wiki/w/Verac_the_Defiled) 15345458 | |
| Hitpoints (the Gauntlets) | [Crystalline Hunllef](https://oldschool.runescape.wiki/w/Crystalline_Hunllef) 15373456, [Corrupted Hunllef](https://oldschool.runescape.wiki/w/Corrupted_Hunllef) 15373433 | |
| Fight Caves monster hitpoints | [TzHaar Fight Cave](https://oldschool.runescape.wiki/w/TzHaar_Fight_Cave) | 15387577 |
| Fight Caves waves | [Template:TzHaar Fight Cave Waves](https://oldschool.runescape.wiki/w/Template:TzHaar_Fight_Cave_Waves) | 15140194 |
| Inferno waves, monster hitpoints, infernal cape and Tokkul | [Inferno](https://oldschool.runescape.wiki/w/Inferno) | 15385996 |
| Hitpoints (what each Jal-Ak splits into) | [Jal-AkRek-Ket](https://oldschool.runescape.wiki/w/Jal-AkRek-Ket) 15316948, [Jal-AkRek-Mej](https://oldschool.runescape.wiki/w/Jal-AkRek-Mej) 15316949, [Jal-AkRek-Xil](https://oldschool.runescape.wiki/w/Jal-AkRek-Xil) 15316950 | |
| Colosseum enemy hitpoints | [Fortis Colosseum](https://oldschool.runescape.wiki/w/Fortis_Colosseum) | 15390022 |
| Colosseum waves and reinforcements | [Fortis Colosseum/Strategies](https://oldschool.runescape.wiki/w/Fortis_Colosseum/Strategies) | 15387384 |
| Hitpoints (Sol Heredit) | [Sol Heredit](https://oldschool.runescape.wiki/w/Sol_Heredit) | 15379686 |
| `wintertodt_logs`, `_gems`, `_ores`, `_herbs`, `_seeds`, `_fish` (the level formula and row order) | [Module:Wintertodt supply crate](https://oldschool.runescape.wiki/w/Module:Wintertodt_supply_crate) | 15312585 |
| Wintertodt's energy as hitpoints | [Wintertodt/Strategies](https://oldschool.runescape.wiki/w/Wintertodt/Strategies) | 15382910 |
| Tempoross's reward-pool rows | [Template:Reward pool/Rewards unique](https://oldschool.runescape.wiki/w/Template:Reward_pool/Rewards_unique) 15310837, [Template:Reward pool/Rewards levels 50–75](https://oldschool.runescape.wiki/w/Template:Reward_pool/Rewards_levels_50–75) 15141564, [Template:Reward pool/Rewards other](https://oldschool.runescape.wiki/w/Template:Reward_pool/Rewards_other) 15173597 | |
| Tempoross permits per points, energy and essence | [Tempoross](https://oldschool.runescape.wiki/w/Tempoross) | 15390338 |
| Tempoross's standard points | [Tempoross/Strategies](https://oldschool.runescape.wiki/w/Tempoross/Strategies) | 15384412 |
| Notes only (why the spirit angler outfit isn't in Tempoross's log) | [Spirit angler's outfit](https://oldschool.runescape.wiki/w/Spirit_angler%27s_outfit) | 15384195 |
| Notes only (the Colosseum's token, left out) | [Token (Varlamore)](https://oldschool.runescape.wiki/w/Token_(Varlamore)) | 15291238 |
| CoX standard run (33,000 points), and Tekton's onyx counted per raid | [Money making guide/Chambers of Xeric (Normal Mode)](https://oldschool.runescape.wiki/w/Money_making_guide/Chambers_of_Xeric_(Normal_Mode)) | 15374781 |
| CoX common quantities (each row's divisor) | [Module:Chambers of Xeric calculator](https://oldschool.runescape.wiki/w/Module:Chambers_of_Xeric_calculator) | 15293646 |
| Notes only (the ancient chest's calculator, which runs that module) | [Calculator:Chambers of Xeric loot](https://oldschool.runescape.wiki/w/Calculator:Chambers_of_Xeric_loot) 15293832, [Calculator:Chambers of Xeric/Template](https://oldschool.runescape.wiki/w/Calculator:Chambers_of_Xeric/Template) 11733738 | |
| CoX common-loot cap (131,071 points), Xeric's capes | [Chambers of Xeric](https://oldschool.runescape.wiki/w/Chambers_of_Xeric) | 15385998 |
| Olm's phases, the combat rooms per raid, Tekton's onyx | [Chambers of Xeric/Strategies](https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies) | 15392111 |
| Hitpoints (the Great Olm) | [Great Olm](https://oldschool.runescape.wiki/w/Great_Olm) | 15372769 |
| CoX onyx (1/450) | [Tekton](https://oldschool.runescape.wiki/w/Tekton) | 15387642 |
| ToB standard run (trio, 0 deaths) | [Money making guide/Theatre of Blood](https://oldschool.runescape.wiki/w/Money_making_guide/Theatre_of_Blood) | 15307185 |
| ToB hitpoint scaling, the bosses' levels, Sinhaza shrouds | [Theatre of Blood](https://oldschool.runescape.wiki/w/Theatre_of_Blood) | 15393349 |
| Hitpoints (ToB) | [The Maiden of Sugadinti](https://oldschool.runescape.wiki/w/The_Maiden_of_Sugadinti) 15383115, [Pestilent Bloat](https://oldschool.runescape.wiki/w/Pestilent_Bloat) 15317867, [Nylocas Vasilias](https://oldschool.runescape.wiki/w/Nylocas_Vasilias) 15366394, [Sotetseg](https://oldschool.runescape.wiki/w/Sotetseg) 15366015, [Xarpus](https://oldschool.runescape.wiki/w/Xarpus) 15315550, [Verzik Vitur](https://oldschool.runescape.wiki/w/Verzik_Vitur) 15391510 | |
| ToA standard run (Expert solo, raid level 300, unique ~1/22.5) | [Money making guide/Tombs of Amascut (Expert)](https://oldschool.runescape.wiki/w/Money_making_guide/Tombs_of_Amascut_(Expert)) | 15315543 |
| ToA unique weights, common divisors and quantity rule, the kill order for Walk the Path | [Module:Tombs of Amascut loot](https://oldschool.runescape.wiki/w/Module:Tombs_of_Amascut_loot) | 15313816 |
| Notes only (the chest page's calculator, which runs that module) | [Calculator:Tombs of Amascut loot](https://oldschool.runescape.wiki/w/Calculator:Tombs_of_Amascut_loot) | 15010378 |
| ToA path levels, Walk the Path, Icthlarin's shrouds | [Tombs of Amascut](https://oldschool.runescape.wiki/w/Tombs_of_Amascut) | 15391794 |
| ToA raid-level hitpoint formula, the Warden's core, the heals not counted | [Tombs of Amascut/Strategies](https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies) | 15392814 |
| Hitpoints (ToA) | [Ba-Ba](https://oldschool.runescape.wiki/w/Ba-Ba) 15200300, [Kephri](https://oldschool.runescape.wiki/w/Kephri) 15200302, [Zebak](https://oldschool.runescape.wiki/w/Zebak) 15351537, [Akkha](https://oldschool.runescape.wiki/w/Akkha) 15383708, [Obelisk (Tombs of Amascut)](https://oldschool.runescape.wiki/w/Obelisk_(Tombs_of_Amascut)) 15200324, [Tumeken's Warden](https://oldschool.runescape.wiki/w/Tumeken%27s_Warden) 15343238, [Elidinis' Warden](https://oldschool.runescape.wiki/w/Elidinis%27_Warden) 15272445 | |
| TFA standard run (deathless trio) | [Money making guide/The Fractured Archive (Trio)](https://oldschool.runescape.wiki/w/Money_making_guide/The_Fractured_Archive_(Trio)) | 15392633 |
| TFA hitpoint scaling, the first week's boss counts, Living records | [The Fractured Archive](https://oldschool.runescape.wiki/w/The_Fractured_Archive) | 15391893 |
| TFA phases (the Archive Guardian's shield, Ocellus's phase 2) | [The Fractured Archive/Strategies](https://oldschool.runescape.wiki/w/The_Fractured_Archive/Strategies) | 15393925 |
| Hitpoints (TFA) | [Archive Guardian](https://oldschool.runescape.wiki/w/Archive_Guardian) 15392745, [Bork](https://oldschool.runescape.wiki/w/Bork) 15387221, [Dreadnoth the Jellied](https://oldschool.runescape.wiki/w/Dreadnoth_the_Jellied) 15387217, [Xarth](https://oldschool.runescape.wiki/w/Xarth) 15392746, [Ocellus](https://oldschool.runescape.wiki/w/Ocellus) 15392748 | |

Two pages informed the standard setups but aren't fetched by the script:
[Money making guide/Killing the Doom of Mokhaiotl (Delve 1-8)](https://oldschool.runescape.wiki/w/Money_making_guide/Killing_the_Doom_of_Mokhaiotl_(Delve_1-8))
(15348782) and [Money making guide/Killing Zalcano](https://oldschool.runescape.wiki/w/Money_making_guide/Killing_Zalcano)
(15122641). For the raids, [Module:Theatre of Blood calculator](https://oldschool.runescape.wiki/w/Module:Theatre_of_Blood_calculator)
(15316895) was read and isn't fetched: it gives each common row from its listed minimum to 1.2
times that at full performance, which is the chest page's ranges, so those are used as listed.

Item IDs come from the Wiki's `infobox_item` bucket. Collection log items use the ID from
`Module:Collection log/data.json`. Clue scrolls have no single infobox ID, so they use the
generic per-tier IDs RuneLite uses (`CLUE_SCROLL_*` in the old `ItemID`).

## How the data was read

- **Wikitext, cross-checked with Bucket.** The table structure comes from each page's wikitext:
  which section a row is in (100%, pre-roll, main, tertiary), the sub-table templates, and the
  exact rarities as written. The Bucket `dropsline` rows can't show pre-rolls or sections, and they
  hold sub-table rows only at rounded rates (for example `1/77.1`). Every directly listed
  `{{DropsLine}}` row was checked against the Bucket rows for its page, and all of them matched.
- **Sub-tables are folded into `main`.** Herb, seed, shark, bolt-tip and flax/mutagen sub-tables
  are multiplied out into `main`, and `mainOutOf` is the lowest common denominator, so every weight
  is an exact integer. That is why some values are large: Bryophyta 2,146,304; Sarachnis 436,000;
  Vorkath 3,412,500; Zulrah 27,962,368. Every `outOf` fits in a Java `int`.
- **Obor's general seed table is the one exception.** Folding it in would need a `mainOutOf` of
  44,074,800,000, which doesn't fit in an `int`. It is instead the shared table
  `general_seed_drop_table_lvl106`, which Obor reaches at 18/135. The table depends on combat
  level (Obor is level 106), so the level is in its name. It is flattened and exact, with
  outOf 1,469,160,000.
- **`mega_rare_drop_table` is an extra shared table.** Both the rare drop table (15/128) and the
  gem drop table (1/128) link to the mega-rare table, so it is a third shared table that both
  reach through nested entries.
- **Rare and gem table access** comes from `{{RareDropTable|rdt|gdt}}` and `{{GemDropTable|gdt}}`.
  The rare drop table links to the gem drop table at 20/128.
- **Empty slots.** A "Nothing" slot is left out and becomes the unused remainder of `outOf`. This
  covers the gem table's 63/128 and the mega-rare table's 113/128.

### The 44 added bosses

They go through `build_boss_v2`. It reads the same way as above, with these additions.

- **Exact expressions.** Rarities the Wiki writes as `{{#expr:}}` (Hydra's uniques, Shellbane
  gryphon's seed and herb rows, the sub-table templates) are worked out exactly, without the
  Wiki's rounding. Yama's rows use `Rarity=` and `Quantity=`, so parameter names are read
  regardless of case.
- **Bucket.** All 1,617 directly listed rows on the new pages match their Bucket rows. Rows marked
  `bucket=No` are skipped. The DT2 bosses' "normal, perfect" quantities are compared as Bucket
  stores them, as low and high.
- **A separate roll that gives one item from a table** becomes a shared table plus one tertiary
  entry, `{"table": ..., "numerator", "denominator"}`, at the section's total chance. The tables'
  weights are the rows' rates divided by that total. The added shared tables are
  `callisto_unique_table`, `artio_unique_table`, `venenatis_unique_table`, `spindel_unique_table`,
  `vetion_unique_table` and `calvarion_unique_table` (the Wilderness unique tables);
  `wilderness_supply_roll` (one table shared by those six bosses); `chaos_elemental_minor_drops`;
  `kalphite_queen_consumables`; `hespori_anima_seeds`; `amoxliatl_frozen_tears`;
  `skotizo_ancient_shards`; `phantom_muspah_unique_table`; `hueycoatl_unique_table`;
  `nex_unique_table`; `corporeal_beast_sigil_table`; `nightmare_unique_table`,
  `nightmare_orb_table`, `phosanis_nightmare_unique_table` and `phosanis_nightmare_orb_table`. The
  guaranteed ones (minor drops, consumables, anima seeds, frozen tears, ancient shards, Muspah's
  uniques) are rolled at 1/1.
- **Items that open into a table.** The Unsired opens into `font_of_consumption`, and Yama's
  dossier and forgotten lockbox open into `dossier` and `forgotten_lockbox`. The item stays a
  normal drop. Using it is a second tertiary table roll at the same chance per kill. The two rolls
  are independent here: a kill can roll the Font without dropping an Unsired. Each item's chance
  per kill is still the Wiki's.
- **Wiki pre-rolls stay tertiary**, as for the first nine bosses. Only the Grotesque Guardians'
  unique rows are a per-roll pre-roll like Zulrah's (see below).
- **"Rolled for until the roll results in a success"** (the four DT2 bosses and Yama) is a chain of
  pre-rolls on the single main roll. Each step only happens when the earlier ones fail: its
  chance is the step's rate times the chance of reaching it. The standard table at the end is
  scaled by the chance of reaching it. Rates the Wiki shows rounded (1/25.8) are replaced by the
  exact chain value. The script checks each against the Wiki's rounding and prints any that
  differ.
- **Rows dropped together.** When the Wiki says items drop together (for example "Players will
  receive Mystic fire staff together with Mystic water staff"), the first row keeps the slot in
  `main`. Each item that comes with it is a tertiary roll with the same chance per kill
  (`1 - (1 - p)^rolls`). They are listed per boss below. Without this, a slot's items would be
  counted twice and the table would go over its `outOf`.
- **God Wars Dungeon tables.** The four generals' `{{GWDRDT}}` slots roll the GWD-variant tables:
  `gwd_rare_drop_table` at 8/127 and `gwd_gem_drop_table` at 2/127. Both link to
  `gwd_mega_rare_drop_table`, as the normal tables do. Every flattened rate in Template:GWDRDT
  (computed with Template:CalculateRDTNaked) matches these tables exactly.
- **Doubled quantities.** The Abyssal Sire's rare drop table slot "will always drop double the
  usual quantity". It rolls `rare_drop_table_x2`, which links to `gem_drop_table_x2` and
  `mega_rare_drop_table_x2`. These are the three shared tables with every quantity doubled.
- **Item IDs.** Names the first nine bosses use are looked up in the same Bucket batches as before.
  Names only the added bosses use are looked up afterwards, with the new log tabs' IDs taking
  precedence over the infobox. No name ended up with two different IDs.
- **Chances too fine for a Java `int`.** Eight tertiary chances need a denominator over 2^31: Hydra's
  six chained unique rates, and the two Grotesque Guardians potions that drop with the magic
  potion (once the pre-roll scaling is applied). Each is stored as the nearest fraction that
  fits. The relative error is at most 2e-14.

### The 13 reward-chest, wave and reward-pool bosses

Each "kill" is one completed run under the standard setup listed under Judgement calls below.
They go through `build_chest_boss`, which reads rows the same way as `build_boss_v2`, with these
additions.

- **Built last, kept apart.** They are built after the other 53, with their own registry of shared
  tables and a separate item lookup for names only they use. A rebuild was diffed against the
  `drops.json` from before they were added: the 53 entries and 33 shared tables are unchanged,
  and only the 13 entries and 18 shared tables below are new.
- **Reward rows.** Reward pages use `{{DropsLineReward}}`. All 441 directly listed rows on these
  pages match their Bucket rows. Tempoross's rows are on three templates that the reward pool
  transcludes; they were checked against the reward pool's Bucket rows.
- **The Wiki's statements are checked.** Each setup rests on a sentence such as "1 Moon gives 1
  roll" or "accurate for a Barrows run with all 6 brothers slain and with full potential (1012
  points)". The script stops if one of them changes (`checks` in `CHEST_BOSSES`).
- **Rewards given in prose.** The fire cape, infernal cape and both Tokkul amounts aren't drop rows.
  The script reads them from the sentences that state them and lists them as `always`.
- **One run, several rolls of a unique.** When a run rolls a unique several times at different
  rates (Doom's delve levels, Barrows' elite clue), its chance per run is `1 - Π(1 - p)`. Fifteen
  of these chances need a denominator over 2^31. Each is stored as the nearest fraction that fits,
  with a relative error of at most 1e-14.
- **Waves as shared tables.** Each Colosseum wave's chest table is one roll. Wave 1 (80 sunfire
  splinters) is `always`, waves 2-11 are the shared tables `fortis_colosseum_wave_2` to
  `fortis_colosseum_wave_11`, each rolled at 1/1, and wave 12 is `main`.
- **Wintertodt's material sub-tables.** The reward cart page gives their rows as "Varies". The
  Wiki's own calculator, Module:Wintertodt supply crate, has the rule: each row, in order, takes
  `floor(low + (high - low) × (level - 1) / 98) + 1` out of 256 of what the rows before it left.
  The six sub-tables are worked out exactly for the setup's levels and stored as
  `wintertodt_logs`, `wintertodt_gems`, `wintertodt_ores`, `wintertodt_herbs`, `wintertodt_seeds`
  and `wintertodt_fish`. The exact chances need denominators up to 2^107, so each sub-table is
  stored out of 2^30 with its total kept exact. The largest relative error is 6e-7.
- **Other new shared tables.** `wintertodt_reward_cart` is the cart's standard table again, for
  the third roll that 750 points gives half the time. `zalcano_tool_seed_table` is Zalcano's 1/200
  roll (crystal tool seed 39/40, uncut onyx 1/40).

### The four raids

Each "kill" is one completed raid under the standard setup listed under Judgement calls below.
They go through `build_raid_boss` (`build_cox`, `build_tob`, `build_toa`, `build_tfa`), which read
the chest page's rows the same way as the chest bosses, with these additions.

- **Built last, kept apart.** They are built after the other 66, with their own registry of shared
  tables, their own page batches and a separate item lookup for names only they use. A rebuild was
  diffed against the `drops.json` from before they were added: the 66 entries and 51 shared tables
  are unchanged, and only the 4 entries and 4 shared tables below are new.
- **A unique replaces the common loot.** In every raid, a player who gets a unique gets no common
  rolls ("If one of these items is received, no common rewards are given", the ancient chest). This
  is the new `preRoll` field: `{"table", "numerator", "denominator"}`, rolled before anything else.
  If it hits, the shared table is rolled once and `main` isn't rolled at all; if it misses, `main` is
  rolled `rolls` times. `always` and `tertiary` are given either way. Per run, a main-table item comes
  with chance `(1 - p) × (1 - (1 - q)^rolls)`, and a unique with `p × weight / outOf`. The script's
  check of `drops.json` prints each pre-roll and counts it in the expected kills.
- **The unique tables** are `chambers_of_xeric_unique_table` (out of 60),
  `theatre_of_blood_unique_table` (19), `tombs_of_amascut_unique_table` (24) and
  `fractured_archive_unique_table` (15). Each is the chest page's rows for the setup's mode, and
  the script checks they add up to the whole table.
- **Quantities that depend on points** come from the Wiki's own loot calculators, which the chest
  pages embed. The Chambers of Xeric calculator (Module:Chambers of Xeric calculator) gives each
  common row `floor(personal points / divisor)`, and 1 for the torn prayer scroll and dark relic.
  The Tombs of Amascut calculator (Module:Tombs of Amascut loot) gives
  `max(floor(floor(points / team size / divisor) × scale), 1)`, with `scale` 1.15 at raid level 300.
  The script reads the divisors and checks the formula's pieces. The chest page's own divisor table
  for the Tombs matches the module's.
- **Bucket.** All 214 directly listed rows on the four chest pages and Tekton's page match their
  Bucket rows.
- **The Wiki's statements are checked**, as for the chest bosses. Each setup rests on sentences such
  as "with the player earning 33,000 points in each", "in trios with 0 deaths", "Completing Expert
  solo Tombs of Amascut (lvl 300)" and "~1/13.46 (1/14 x 1.04)". The script stops if one changes
  (`checks` in `RAID_BOSSES`, and the patterns each builder reads).
- **Every chance fits an `int`.** No raid chance needs a denominator over 2^31.

## Hitpoints and combat level

Every boss entry, including the first nine, has `hitpoints` and `combatLevel` from the Wiki's
infoboxes. Versions are the standard ones: post-quest (Amoxliatl, Brutus, the Leviathan,
Vardorvis, the Whisperer, Shellbane gryphon, the Mad Angel, Vorkath), solo (Scurrius), the
non-awakened DT2 fight, and the Kraken itself rather than its whirlpool. When a fight needs more
than one NPC killed, `hitpoints` is the total, and `combatLevel` is the highest level in the fight.

| Boss | Hitpoints | How |
|---|---|---|
| Grotesque Guardians | 900 | Dusk 450 + Dawn 450. Combat 328 is Dusk's second form. |
| The Hueycoatl | 4,050 | 2,500 + five body segments of 250 + the tail's 300 |
| Kalphite Queen | 510 | Two forms of 255 |
| Nex | 5,400 | 3,400 + Fumus, Umbra, Cruor and Glacies at 500 each (each must be killed to move on) |
| The Nightmare | 7,800 | The Wiki's stated total for the original fight: three phases of 2,000 shield and 600 totem charge (the infobox says 2,400) |
| Phosani's Nightmare | 2,550 | The Wiki's stated total: 1,350 physical damage and 1,200 totem charge (the infobox says 3,200) |
| Royal Titans | 1,200 | Branda 600 + Eldric 600 |
| Vet'ion | 630 | Two forms of 255 + two skeleton hellhounds and two greater ones at 30 each |
| Calvar'ion | 420 | Two forms of 150 + four hellhounds at 30 |
| Yama | 3,300 | 2,500 + two Judges of Yama at 400 |

The Abyssal Sire (425), Zulrah (500) and the Phantom Muspah (850) have one health pool across
their phases. Hespori's flower buds die in one hit and aren't counted. God Wars bodyguards aren't
needed for the kill and aren't counted.

For the 13 chest bosses, `hitpoints` is everything one run must defeat, and `combatLevel` is the
final boss's level, or 0 for Wintertodt and Tempoross, which have none. The wave totals are summed
from the Wiki's wave lists by the script. The Fight Caves total was also checked by hand.

| Boss | Hitpoints | How |
|---|---|---|
| Blue Moon, Eclipse Moon, Blood Moon | 500 each | The Moon's infobox. Combat 329 |
| Barrows | 600 | Six brothers at 100. Combat 115 is the highest brother's (Dharok, Guthan, Torag, Verac). The crypt monsters killed for reward potential aren't counted |
| The Gauntlet | 600 | Crystalline Hunllef only. Combat 674 |
| The Corrupted Gauntlet | 1,000 | Corrupted Hunllef only. Combat 894 |
| TzTok-Jad | 11,770 | Waves 1-62 (11,520), each level-45 Tz-Kek counted with the two level-22 ones it splits into, plus Jad's 250. Jad's healers "don't have to be killed" and aren't counted |
| TzKal-Zuk | 23,510 | Waves 1-68 (22,310), each Jal-Ak counted as 40 plus its three 15-hitpoint splits, plus Zuk's 1,200. Zuk's own minions die with it and aren't counted |
| Sol Heredit | 12,363 | Waves 1-11 with their reinforcements (10,863; 8,363 without), plus Sol Heredit's 1,500 |
| Doom of Mokhaiotl | 4,850 | Delves 1-8: 525 + 550 + 575 + 600 + 625 + 650 + 650 + 675. Combat 908 is delve 8's |
| Wintertodt | 700 | Its energy, as hitpoints: Wintertodt/Strategies says to "imagine that the Wintertodt is a boss with 700 Hitpoints" |
| Tempoross | 350 | Its energy and essence: one player's share in a 40-player game, ~180 energy and ~170 essence (the Tempoross page, from Mod Squid) |
| Zalcano | 1,300 | Armour 300 + health 1,000 (the infobox's two versions). Combat 336 |

For the four raids, `hitpoints` is the total of the raid's bosses at the setup's team size and
level, from the Wiki's infoboxes and the raid's stated hitpoint scaling, with every phase counted.
Minions, trash and puzzle rooms aren't counted, as the Gauntlet's prep monsters aren't. Heals and
shield regeneration aren't counted either, because the infoboxes don't give them as pools.
`combatLevel` is the final boss's level in its last phase. The script works each total out from
the pages (`raid_stats`).

| Raid | Hitpoints | How |
|---|---|---|
| Chambers of Xeric | 4,400 | The Great Olm only: "In a team size of 1-7, Olm has four phases in total, three standard phases and a head phase" (Chambers of Xeric/Strategies). Both hands are disabled in each standard phase, and the head is only damaged for good in the head phase: 3 × (left claw 600 + right claw 600) + head 800. Combat 1043 (the head) |
| Theatre of Blood | 19,500 | The six bosses' normal-mode infobox hitpoints, "75% of their original hitpoints" with three or fewer players (the Theatre of Blood page): Maiden 3,500, Bloat 2,000, Nylocas Vasilias 2,500, Sotetseg 4,000, Xarpus 5,000, Verzik 2,000 + 3,500 + 3,500 (her three phases) = 26,000, × 0.75. Combat 1520 (Verzik's third phase) |
| Tombs of Amascut | 8,241 | Raid level 300 solo, below. Combat 544 (the Warden's third phase) |
| The Fractured Archive | 23,510 | The team-size-3 rows of each boss's hitpoints table: Archive Guardian 800 (flying) + 1,600 (grounded), Bork 2,180 + the Smorks 870, Dreadnoth the Jellied 3,050, Xarth 3,770, Ocellus 3,630 (phase 1) + 7,610 (the rest). Combat 1601 (Ocellus's third phase) |

**Chambers of Xeric.** The combat rooms before the Olm are random. Chambers of Xeric/Strategies
names the eight combat rooms and says "A raid layout can have 3 to 5 of these bosses", but not
how often each count or each room comes up, so no expected total can be worked out and only the
Olm is counted. The Olm's infobox gives 800 and 600 with no party size; the Wiki doesn't state the
Olm's hitpoint scaling anywhere, so these are taken as the solo values. That isn't confirmed.

**Theatre of Blood.** The script checks each chosen infobox is the normal-mode one by matching its
combat level to the Theatre of Blood page's boss list (Maiden 940, Bloat 870, Nylocas 800,
Sotetseg 995, Xarpus 960, Verzik 1040). Every trio value is whole (2,625, 1,500, 1,875, 3,000,
3,750, and Verzik 1,500 + 2,625 + 2,625). Verzik's one-time 30% recovery is Hard Mode only.

**Tombs of Amascut.** Each path boss and Warden phase, times `1 + raid level / 250` ("CalculatedStat
= (BaseStat)(1 + RaidLevel/250)", Tombs of Amascut/Strategies), so × 2.2 at raid level 300:

| Part | Base | Expected path level | Path multiplier | At raid level 300 |
|---|---|---|---|---|
| Ba-Ba | 380 | 0 | 1 | 836.0 |
| Kephri: shield 150 + health 80 | 230 | 2/3 | 1.0533 | 533.0 |
| Zebak | 580 | 7/6 | 1.0833 | 1,382.3 |
| Akkha | 400 | 13/6 | 1.1383 | 1,001.7 |
| Obelisk (Wardens' phase 1) | 260 | - | 1 | 572.0 |
| Warden phase 2: its 4,500 health through the core, which takes damage at × 5 | 900 | - | 1 | 1,980.0 |
| Warden phase 3 | 880 | - | 1 | 1,936.0 |
| Total | | | | 8,241.1, stored as 8,241 |

- **Path levels.** The guide's invocation list uses Walk the Path and no Pathseeker. Walk the Path
  gives four level-ups per raid, "random without bias": the first path completed levels up two of
  the other three, the second one of the last two, and the third the last one. The bosses are taken
  in the order Ba-Ba, Kephri, Zebak, Akkha, the order the Wiki's loot calculator assumes ("for WTP
  we assume the order is Ba-Ba, Kephri, Zebak, Akkha"). Over the six equally likely outcomes, their
  expected path levels are 0, 2/3, 7/6 and 13/6. Each path level multiplier is the page's: +8% at
  level 1 and +5% for each level after. Each boss gets its expected multiplier: 1, 79/75, 13/12 and
  683/600. The multiplier is applied on top of the raid-level one, as the Wiki's calculator does;
  the Tombs of Amascut page doesn't say whether the two add or multiply. The Wardens aren't on a
  path, so no path level applies to them.
- **Kephri** is her shield (the "Shielded" infobox, 150) once plus her own health ("Aggressive",
  80). The scarab swarms that recharge the shield vary with play and aren't counted.
- **Akkha's** 20% restore for his final stand and the **phase 3 Warden's** one-time 20% heal aren't
  counted, as no infobox gives them. Akkha's shadows are minions and aren't counted.
- **Warden phase 2** is the hardest to source. The infobox gives 140 ("Active") and 4,500
  ("Core-ejected"), and the Strategies page says the core "is essentially the Warden's health" and
  that "Damage dealt to the core deals five times the damage towards the Warden". So 4,500 / 5 =
  900 of damage to the core removes the Warden's health, and that is what is counted, scaled like
  the rest. The Wiki doesn't say whether the 4,500 scales with raid level (the infobox marks all its
  hitpoints as scaled), and the 140 "Active" bar (the damage that ejects the core, two or three
  times a phase) isn't counted. Both Wardens have the same numbers, so it doesn't matter which one
  powers up.

**The Fractured Archive.** "All bosses' hitpoints scale to the amount of players present in the
raid", and each boss page has a table by team size; the trio row is used. One run fights the
Archive Guardian, one of Ezekiel or Bork and the Smorks, one of Moia or Dreadnoth, one of the
Dagon'hai or Xarth, then Ocellus. At each fork the boss fought more often in the raid's first week
is taken (The Fractured Archive, Trivia): Bork and the Smorks (955,311 against Ezekiel's 198,191),
Dreadnoth (570,763 against Moia's 44,860) and Xarth. Xarth's count is "N/A", but the Dagon'hai's
12,621 is the lowest of every room. The raid page's boss order lists that room as "Bork and the
Smorks", and Bork's hitpoints table gives both, so both count.
The Archive Guardian's flying pool is its shield ("Once the shield health is depleted, it will
crash into the ground"); it comes back in later cycles but is counted once. Ocellus's table gives
phase 1 and "Remainder"; his phase 2 is fought through the Blade, and "only half of all damage
dealt will transfer to the boss's phase 3 health bar", so the remainder is what is counted and the
phase 2 infobox's 1,000 isn't added. Dreadnoth's jellies are minions.

## Judgement calls

### All bosses

- **Standard solo rates.** Obor and Bryophyta use the members' world tables. Scurrius uses the
  MVP/solo table, not the non-MVP group table. Vorkath uses the post-quest table.
- **Talisman.** The gem drop table's single talisman slot is a nature talisman. A chaos talisman
  only drops underground inside a set map region. Every page here that says which talisman says
  nature, except Kraken; see Kraken below.
- **Wiki "pre-rolls" are tertiary.** A pre-roll is a separate roll made before the main table, so
  it is listed under `tertiary` at its exact rate. In game, a pre-roll hit replaces that kill's main
  drop. Listing it as tertiary gives a main drop on those kills too, which overstates main-table
  drops by the pre-roll chance: 3/384 for Sarachnis and 1/1000 for King Black Dragon.
- **Long bone and curved bone** are independent tertiaries. The curved bone's 1/5012.5 is stored
  as 2/10025. In game a kill can't give both, but here it can, with a chance of about 1 in 2 million.
- **Assumed unlocks.** Legends' Quest is assumed done (needed for the mega-rare table through the
  gem table). Monkey Madness II is assumed done (KBD fire runes at 5/128, and dragon javelin tips).
  The giant champion scroll (Obor, Bryophyta, 1/5000) needs 32 quest points and a members' world;
  it is included.
- **Noted drops** use the unnoted item ID with `"noted": true`. Mole claws and mole skins are noted
  after the Hard Falador Diary, but they are stored unnoted.

### Per boss

- **Obor.** The listed members' main-table rows, including an explicit 1/135 "Nothing", add up to
  127/135. The other 8/135 isn't listed on the Wiki, so it is treated as nothing. The beginner clue
  rate (1/50) is marked approximate on the Wiki. The giant key (1/16) is a tertiary.
- **Bryophyta.** The uncommon seed table's rows add up to 1046/1048; the other 2/1048 is nothing.
  The mossy key uses the normal 1/16 rate, not the 1/8 on a moss giant slayer task.
- **Sarachnis.** The dragon med helm (2/384) and Sarachnis cudgel (1/384) are a wiki pre-roll, so
  they are tertiary. The Wiki shows each tattered page at 1/5, but its note says 1/5 is the chance
  of any page, and the player gets the page they have fewest of. So each page is 1/15 here. Its
  rare seed table gives three of each seed (nine snape grass seeds), as the Wiki states.
- **King Black Dragon.** The dragon pickaxe (1/1000) is a wiki pre-roll, so it is tertiary.
- **Kraken.** The Wiki lists the Kraken tentacle (1/400) in the main table's "Other" rows. The other
  rows already add up to exactly 128/128, so the tentacle is a separate tertiary roll here. The
  rusty sword's 1.75/128 and the trident's 1/512 give a `mainOutOf` of 512. The Wiki lists both
  talismans for Kraken's gem table; this uses the shared nature talisman. A chaos talisman would
  drop in the non-instanced cove.
- **Zulrah.** Mod Ash confirmed the unique table is 1/256 per roll, with two rolls per kill. It is
  read as a pre-roll on each main roll: a hit gives a unique instead of that roll's regular drop.
  So the four uniques are each 1/1024 per roll, and the 249-weight table, including the rare drop
  table slot, is scaled by 255/256. The Wiki doesn't say how the two interact, but its listed rows
  add up to exactly 249/249, so the uniques can't simply be added on top. Flax and the two mutagens
  use the exact rates in the Wiki's notes: 10/249 × 5244/5264, and 10/249 × 10/5264 each. That puts
  each mutagen at about 1 in 6,580 kills; the Wiki's rounded figure is ~1/6,554 because it leaves
  out the 255/256.
- **Vorkath.** The Wiki writes the snapdragon and torstol seed rates as a combined value: 1/150
  direct plus the tree-herb seed table. They are stored as separate main entries: the 1/150 direct
  roll and the table share. The 50th-kill guarantee of Vorkath's head isn't modelled; only the
  1/50 drop is.

### The 44 added bosses: all of them

- **Standard kill.** Solo, or MVP with full contribution, for group bosses (Nex, the Nightmare, the
  Hueycoatl, the Royal Titans, Yama, the Wilderness bosses). Post-quest and non-awakened versions.
  Members' worlds. Araxxor's corpse is harvested, not destroyed. The Maggot King is opened
  ("open-stomach"), not egg-taken. The Royal Titans use "Loot", not "Sacrifice" or "Take pages".
  Yama has no contract.
- **Base rates.** Rates that a Ring of wealth (i), contribution or the MVP bonus would change use
  the Wiki's base value. Rates that improve with kill count or level until the first drop use the
  base value too: the DT2 tablets and quartz, and Tangleroot at 1/5,375 (level 65 Farming; 1/4,525
  at 99).
- **Guarantees aren't modelled.** These are Phosani's slepey tablet on the 25th kill, Yama's rite of
  vile transference from the first dossier after 100 kills, and the tattered KQ head on the 256th
  kill (left out). Only the drop rolls are modelled.
- **"Always" rewards that need a fast kill.** Araxxor's coagulated venom (kill under 1:15) and the
  Phantom Muspah's charged ice (under 3:00) are listed as "Always" for players who don't already
  own one. Both are log items, so both are kept as always drops. That puts them in the log on the
  first kill.

### The 44 added bosses: per boss

- **Abyssal Sire.** The Unsired (1/100) is a Wiki pre-roll, so it's tertiary. A second 1/100
  tertiary rolls `font_of_consumption`, the Font's roll of 128 from the Unsired page: bludgeon
  pieces 62, dagger 26, jar of miasma 13, whip 12, head 10, orphan 5. The Wiki shows each bludgeon
  piece at 62/128, but that is the rate for any piece (no duplicates until a set is done). So each
  is 62/384. That gives the Wiki's own per-kill figures: orphan 1/2,560, whip 1/1,066.7, dagger
  1/492.3, each piece 1/619.4. The pet stays on the Font's table after it is owned; the game
  re-rolls without it, out of 123. The rare drop table slot (3/139) rolls `rare_drop_table_x2`.
  The Unsired pre-roll overstates main drops by 1/100.
- **Alchemical Hydra.** Two main rolls. The unique pre-roll on the first roll is tertiary. Its rates
  are the Wiki's exact chained ones: the thrownaxe, knife, claw, tail, leather and ring piece are
  rolled in turn, each only if the earlier ones failed. Hydra's eye, fang and heart each show the
  whole ring-piece rate (1/181.07). The game hands them out in order (eye, then fang, then heart),
  so each is listed at a third, 1/543.2. The RDT note says it "will give the player two rolls on
  the rare drop table". That is read as the 1/101 slot on each of the two main rolls. The seed
  table's 2-3 multiplier and its 30-45 watermelon override are kept. Items that drop together:
  mystic water staff (with the fire staff), rune platelegs or plateskirt (with the platebody),
  mystic robe bottom (light) (with the top), 2 super restore(3) (with the ranging potion).
- **Amoxliatl.** Two main rolls, plus a guaranteed roll on `amoxliatl_frozen_tears` (2-4 at 6/10,
  5-10 at 3/10, 10-20 at 1/10). The Pendant of Ates (inert) is kept at its listed 1/25.
- **Araxxor.** Every main rate is marked approximate on the Wiki (drop-log estimates out of 115).
  The ~1/8 supply drop can't share the main table: with the listed rows it would go past 115/115.
  It gives a venom sac or super combat(1), a prayer potion(4), and 2-3 sharks or wild pies. Its
  five rows are separate tertiary rolls at their listed rates. The noxious pommel, point and
  blade share 1/200, so each is 1/600. The unique table (1/150) is a slot in the main table.
- **Brutus.** The three coin rows marked free-to-play are left out; on members' worlds those slots
  give seeds and noted steaks. The members' rows add up to exactly 81/81. The pre-roll (mooleta,
  milk bucket, slippers: 10/150) is tertiary, so main drops are overstated by 1/15.
- **Callisto, Venenatis, Vet'ion.** The unique table is a separate roll: "at most one item from this
  table is dropped per kill", and the main table is a full 126/126. It's a shared table reached
  at its total rate, 19921/1128960 (~1/56.67). The secondary supply roll (six items at 1/18 each)
  is `wilderness_supply_roll` at 1/3. A solo player gets big bones only. Vet'ion's skeleton
  champion scroll (1/5,000) is kept, like the giant champion scroll.
- **Artio, Spindel, Calvar'ion.** As above, with unique tables at ~1/103.12 and the supply roll at
  1/3 (Spindel: 5/9). Calvar'ion's skeleton champion scroll is kept.
- **Cerberus.** The uniques are a 1/130 slot inside the main table, each of the four at 1/520.
- **Chaos Elemental.** Every major drop comes with a minor drop, so `chaos_elemental_minor_drops`
  (one of five) is rolled every kill. The dragon pickaxe is a pre-roll (tertiary).
- **Chaos Fanatic, Crazy archaeologist, Scorpia.** The odium and malediction shards are pre-rolls
  (tertiary, 1/256 each). The Fanatic's zamorak monk bottom and uncut sapphire drop with the top
  and the uncut emerald. The Crazy archaeologist's uncut sapphire drops with the emerald. The
  Crazy archaeologist's listed main rows add up to 127/128; the last 1/128 is treated as nothing.
  The Wiki lists the fedora (1/128) as a separate tertiary. Scorpia's offspring uses the Wiki's
  1/2,015.75 (1/128 rarer than 1/2,000 because of the shard pre-roll).
- **Commander Zilyana, General Graardor, Kree'arra, K'ril Tsutsaroth.** The general's kill only; bodyguard
  drops aren't included. The Wiki's big coin row, "from all loot tables, including GDT and RDT",
  also counts the coins the GWD rare and gem tables give: 3.69966/127. Those coins now come from
  `gwd_rare_drop_table`, so that much comes off the row. What's left is Zilyana 27.5/127
  (from 31.2), Graardor 28/127 (from 31.7), Kree'arra 40/127 (from 43.7) and K'ril 33/127 (from
  36.7). Each main table then adds up to exactly 127/127. Graardor's and Kree'arra's separate
  1.25/127 coin rows (from the hilt and shard tables) are kept as listed. Items that drop
  together: Zilyana's super restore(4) and magic potion(3), Kree'arra's super defence(3), K'ril's
  super strength(3) and zamorak brew(3).
- **Corporeal Beast.** The sigil table is a separate roll (1/585, then 1/7 elysian, 3/7 spectral,
  3/7 arcane), since the main table already adds up to 512/512. It is
  `corporeal_beast_sigil_table`. In game the gem table slot (12/512) rolls the gem drop table ten
  times. A table slot here rolls a table once, so gem drops are undercounted. No log item comes
  from it.
- **Dagannoth Rex, Prime, Supreme.** Their talisman tables use Template:TalismanDropLines. The
  pages say nature talisman, even though Waterbirth is underground.
- **Deranged archaeologist.** Template:FossilDropLines (fossil=12, numulite=28) gives tertiary
  rows: 5-32 numulites at 108/175, and small, medium, large and rare fossils at 12/350, 12/700,
  12/875 and 12/3,500.
- **Duke Sucellus, The Leviathan, Vardorvis, The Whisperer.** The Wiki's roll order is kept: unique
  table, awakener's orb, tablet, quartz, supply drop, then the standard table. The unique rows
  are already per kill. What's left of the unique step is the vestige counter's gold rings,
  which the Wiki doesn't list, so it is nothing: Duke 1/360, Leviathan 1/384, Vardorvis 1/544,
  Whisperer 1/256. The orb, tablet and supply chances are the exact chain values. All round to the
  Wiki's figures except the Leviathan's tablet, which comes out at 1/25.75 against the Wiki's
  1/25.8. **The quartz differs.** The Wiki's quartz rate leaves out the tablet step (after the
  first tablet it is no longer rolled). The tablet is a log item, so it stays in the chain here.
  That makes the quartz 1/215.2 (the Wiki says 1/206.6) for Duke, 1/214.6 (1/206.0) for the
  Leviathan, 1/212.5 (1/204.0) for Vardorvis and 1/218.1 (1/209.3) for the Whisperer. The supply
  drop's three items drop together: the first takes the slot, and the other two are separate
  rolls. Every quantity is the normal kill's, the first of the Wiki's "normal, perfect" pair. The
  standard-table rows add up to 78/100 on all four pages; the other 22/100 is nothing (as for
  Obor). The rows are scaled by the chance of reaching the standard table: Duke 0.740,
  Leviathan 0.742, Vardorvis 0.749, Whisperer 0.730. Clue scrolls are 1/160 per tier.
- **Grotesque Guardians.** Two main rolls. The unique rows add up to 31/3000 per roll. The other rows
  already add up to exactly 142/142, so, as for Zulrah, the uniques are a pre-roll on each roll,
  and the rest of the table is scaled by 2969/3000. The ranging and super combat potions drop
  with the magic potion.
- **Hespori.** One guaranteed roll on `hespori_anima_seeds` (1/3 each), plus the seed table. The
  bottomless compost bucket (1/35) is a pre-roll (tertiary).
- **The Hueycoatl.** The unique roll (1/18) comes "in addition to the main loot table", so it is
  `hueycoatl_unique_table`. Big bones go to the MVP.
- **Kalphite Queen.** Each kill gives one regular drop and one consumable, so
  `kalphite_queen_consumables` (1/9 each) is rolled every kill. The page lists both talismans; the
  shared nature talisman is used, as for Kraken.
- **The Mad Angel.** The pre-roll (teleports 1/25, hallowfell 1/127, jar 1/1,000) is tertiary. The
  supply batch gives a shark or yellowfin roll in `main`; the prayer potion(2) and super combat
  potion(1) that come with it are separate rolls.
- **Maggot King.** The pre-roll (fang 1/340, kisten 1/520) is tertiary. The dull ancient medal drops
  with the stymphike tartare.
- **Nex.** The unique table is 1/43 per kill (`nex_unique_table`). The MVP's 10% unique boost isn't
  added. Twenty of the common rows only say "Common" or "Uncommon" on the Wiki, so they are left
  out. Only the numeric rows are in `main`: blood essence 1/82, rune shortsword 1/209, and nihil
  shards at 5/82 and 8/209. The rest of the main roll is nothing, so a kill gives much less
  common loot than in game.
- **The Nightmare, Phosani's Nightmare.** Each kill rolls two unique tables, and both can hit: the
  Nightmare's at 31/2625 and 1/320, Phosani's at 1/140 and 3/1600. The rates are for one player
  (Little Nightmare 1/800 in the Nightmare). A solo player in the Nightmare is both MVP and
  eligible for uniques, so gets big bones and bones. Phosani's main rows add up to 101/100: mithril
  ore is 5/100 there but 4/100 in the Nightmare. Each Phosani's row is divided by 1.01.
- **Phantom Muspah.** The unique table is rolled every kill (`phantom_muspah_unique_table`: three
  sizes of ancient essence, frozen cache, ancient icon, venator shard). In game a venator shard
  means no other loot; that isn't modelled. The supply table (2/9 per kill, three items,
  replacing one main roll) is six tertiary rows at their listed 1/9. Main drops are overstated by
  2/9 of a roll.
- **Royal Titans.** The player loots one of the two titans. Each kill counts as half Branda, half
  Eldric: their main rows are merged at half weight, and their pre-rolls at half rate (each crown
  and prayer scroll 1/150; the giantsoul amulet 1/16 from either). The pre-roll replaces both
  standard rolls in game ("if no unique item is received, then two standard drops are rolled
  instead"). It is tertiary here, so main drops are overstated by about 9% (1/75 + 1/75 + 1/16). The prayer scrolls
  stop dropping once read; that isn't modelled. The amulet is stored as the uncharged one,
  30637, the log item.
- **Shellbane Gryphon.** Two main rolls. The pre-roll (Belle's folly 1/75) is rolled once.
- **Skotizo.** The ancient shards (1-5) are a guaranteed roll on `skotizo_ancient_shards`. The dark
  totem base, middle and top share 3/128 (the piece you have fewest of), so each is 1/128. The
  dark totem is 1/128. The gemstone sub-table is folded into `main` (uncut onyx 1/1,000). The
  guaranteed hard clue is kept.
- **Thermonuclear smoke devil.** The pre-roll (occult necklace, smoke battlestaff, dragon
  chainbody) is tertiary.
- **Yama.** The roll order is unique table 1/120 (horn 2/5, armour 3/5 split over three pieces),
  dossier 1/12, lockbox 1/30, 12 oathplate shards 1/15, then the standard table. The Wiki shows
  the standard rows as w/95.11, which is a 78-weight table times the chance of reaching it
  (78 / 0.8201 = 95.11). They are stored exactly as w/78 × 265727/324000. The supply drop gives a
  pizza or pie in `main`; the two potion pairs that come with it are separate rolls. The dossier
  and lockbox open into their tables, so the rite comes out at the Wiki's 1/181.5 and each
  oathplate piece at 1/574.1.

### The 13 chest bosses: standard setups

One "kill" is one run like this:

| Boss | One run |
|---|---|
| Blue Moon, Eclipse Moon, Blood Moon | That Moon defeated on its own, then the Lunar Chest opened for one Moon |
| Barrows | All six brothers killed, and the chest opened at full reward potential (1,012) |
| The Gauntlet | Crystalline Hunllef killed, and the reward chest looted |
| The Corrupted Gauntlet | Corrupted Hunllef killed, and the reward chest looted |
| TzTok-Jad | The Fight Caves completed off-task, keeping the fire cape |
| TzKal-Zuk | The Inferno completed off-task, keeping the infernal cape |
| Sol Heredit | All 12 waves cleared and the rewards claimed after wave 12, keeping the quiver |
| Doom of Mokhaiotl | Delves 1-8 completed and the loot claimed after delve 8 |
| Wintertodt | One solo game at 750 points, with the cart searched at level 70 in its six skills |
| Tempoross | One mass-world cooking game at 4,800 points (5 permits), searched at Fishing 70 |
| Zalcano | A solo kill: the player is the MVP with the full 1,000 tertiary points |

No diaries or combat achievements are assumed (the Elite Karamja Diary doubles Tokkul, the hard
Morytania Diary adds 50% to Barrows runes, and the elite combat achievements raise Barrows' clue
rate). These choices go beyond the setups first suggested:

- **Doom of Mokhaiotl at delve 8.** The Wiki's money-making guide is "Delve 1-8", and its unique-rate
  table's cumulative column runs to delve 8. Delve 8 is also the last fixed level: 9 and deeper are
  "deep delves" with repeating rates.
- **Wintertodt and Tempoross at level 70.** Their materials and fish depend on skill levels that
  the Wiki leaves to the player. Level 70 (Tempoross's 50-75 band) is a mid-game choice. It changes
  no log item's rate.
- **Tempoross at 5 permits.** The Wiki gives no single standard. Tempoross/Strategies says the usual
  mass-world cooking method "consistently yields 4,000–5,000 points per round". 4,800 points is the
  threshold for 5 guaranteed permits.
- **Zalcano solo.** The money-making guide assumes 15 equal players. The Wiki has no formula for a
  player's share of the tool-seed roll or for the shard rate between 1/750 and 1/1,500, so the full
  contribution (the MVP figures) is used. It is the only contribution at which all the Wiki's
  numbers apply.

### The 13 chest bosses: per boss

- **Blue, Eclipse and Blood Moon.** One Moon gives a 1/56 roll at its own set (1/224 per piece).
  If it misses, the standard table is rolled once (out of 30); a unique replaces the standard
  roll. That is a pre-roll on the one main roll, as for Zulrah: each piece is 3/672, and the
  standard rows are scaled by 55/56. The Wiki lists the standard rows at 6 rolls, which is for
  all three Moons. Duplicate protection isn't modelled, so each piece stays at 1/224. Atlatl darts
  come from the standard table, so they are in all three Moons' `log`s (1/6.1 per chest).
- **Barrows.** Seven rolls. Each roll has a 1/102 chance (`1/(450 - 58 × 6)`) of a Barrows item,
  shared evenly by the 24 pieces (1/2,448 each). Otherwise it rolls the reward-potential table out
  of 1,012, so those rows are scaled by 101/102. The coin row is 379/1,012, though the mechanics
  table says 380/1,012. The drop row is used, and the 1/1,012 left over is nothing. The elite clue
  is 1/200 per roll, but at most one per chest (Mod Ash), so it is `1 - (199/200)^7` = 1/29.0 per
  chest, the Wiki's "scales up to 1/29". Each piece comes out at 1/350.1 per chest. The Wiki says
  1/350.14, because a chest can't give the same piece twice; that isn't modelled here.
- **The Gauntlet and the Corrupted Gauntlet.** These follow the reward chest's tables: two rolls on
  the regular table (24/24) or three on the corrupted one (48/48), crystal shards always, and
  separate rolls for the seeds, Youngllef and the elite clue. The Gauntlet cape is listed only on
  the corrupted table, as "Always", for players who don't already have one. It is kept as an
  always drop (like Araxxor's coagulated venom), so it is in the Corrupted Gauntlet's `log` only.
- **TzTok-Jad and TzKal-Zuk.** The cape and the Tokkul are given every run: 8,032 Tokkul for the
  Fight Caves, and the Inferno's stated maximum of 16,440 (a full run). The pets are the off-task
  rates, 1/200 and 1/100. Neither has a main table: `main` is empty, with `mainOutOf` 1.
- **Sol Heredit.** Wave 1's 80 splinters are always, waves 2-11 are shared tables rolled once each,
  and wave 12's table is `main`. Dizana's quiver is always, and Smol Heredit is a separate 1/200
  roll. This gives the Wiki's per-run figures: echo crystal 1/12.4 (the Wiki says 1/12.44) and
  Tonalztics of Ralos 1/83.6 (1/83.61). Each sunfire fanatic piece is 1/24.5, and uncut onyx is
  1/43.6 (wave 12 only). The sunfire pieces' duplicate protection isn't modelled. The wave 12 row
  "Dizana's quiver" is stored as the uncharged quiver, 28947, the log item.
- **Doom of Mokhaiotl.** Eight rolls on the 104-weight regular table, one per delve. The uniques'
  per-level rates come from the Wiki's unique-rate table (each level's overall chance is checked
  against its items). Over delves 1-8 they give cloth 1/140.1, Eye of Ayak 1/148.4, avernic treads
  1/160.2 and Dom 1/231.1 per run, matching the table's cumulative column (~1/140, ~1/148, ~1/160,
  ~1/231). The uniques are separate rolls; the Wiki doesn't say whether one replaces that level's
  regular roll. The elite clue is 1/75 on delves 1-2 and 1/50 after, so 1/7.27 per run; the Wiki
  doesn't say whether a run can give two. The 450 guaranteed demon tears (50 + 60 + 70 + 80 + 90 +
  100) are always. The Wiki's rows show delve 3 quantities. The per-level multipliers (-0.5 to
  +0.17) average -0.034 over delves 1-8, so the listed quantities are kept for every roll.
- **Wintertodt.** At 750 points the cart gives "2 rolls + 50% chance of an extra roll". The third
  roll is a 1/2 tertiary roll on `wintertodt_reward_cart`, the same standard table. Each roll goes
  down the unique chain in the Wiki's order: phoenix 1/5,000, dragon axe 1/10,000, tome 1/1,000,
  warm gloves, bruma torch and a pyromancer piece 1/150 each, then burnt pages 1/45, each only if
  the earlier ones failed. Each unique is a tertiary row at its chance per game,
  `1 - (1 - q)^2 × (1 - q/2)`. The standard table is reached with chance 0.957 per roll but is
  rolled every roll, so standard drops are overstated by 4.3% (the same trade as the Wiki
  pre-rolls above). The pyromancer piece is the one the player has fewest of, so each of the four
  gets a quarter of that step (as for Sarachnis's pages). The Wiki's "effective rate" column says
  1/10,000.2 for the dragon axe and 1/45.30 for burnt pages. The stated order gives 1/10,002.0
  and 1/45.97 (the other rows match it), so the chain values are used. At level 70, spirit,
  dwarf weed, harralander and tarromin seeds can't roll. Limestone uses the page's 3-7 (the module
  says 2-7). The player is assumed to have no phoenix and fewer than three warm gloves and bruma
  torches.
- **Tempoross.** Five reward-pool rolls. The unique rows (Mod Zuko's rates) are used exactly. The
  three templates' rows add up to 8,201/8,000, so the fish sub-table, "about 45/80" on the Wiki,
  gets what is left, 4,299/8,000. Its five rows keep their listed 900:810:720:630:540 ratio (each
  times 1433/1500). The reward-pool casket (1/20) is kept as an item. It isn't opened, because no
  log item comes from it. The template's soaked pages are 5-9; the Tempoross page shows 5-25. The
  player is assumed to own no fish barrel or tackle box.
- **Zalcano.** It drops 3 crystal shards (the MVP's), infernal ashes, one roll on the 36-weight
  table, and `zalcano_tool_seed_table` at 1/200. The Zalcano shard is 1/750 (full contribution),
  and Smolcano 1/2,250. The main table's quantities are the Wiki's crowdsourced ranges, which scale
  with points by a formula the Wiki doesn't give. The MVP's extra 10% isn't added.

### The four raids: standard setups

One "kill" is one completed raid like this. Each comes from the Wiki's money-making guide for the
raid:

| Raid | One run | Guide's sentence |
|---|---|---|
| Chambers of Xeric | Normal mode, solo, 33,000 points | "with the player earning 33,000 points in each" |
| Theatre of Blood | Normal mode, a trio, no deaths, equal contribution | "in trios with 0 deaths" |
| Tombs of Amascut | Expert, solo, raid level 300, no deaths, 20,000 reward points | "Completing Expert solo Tombs of Amascut (lvl 300)" |
| The Fractured Archive | A trio, no deaths, equal contribution | "roughly 1/13.46 for a deathless trio" |

No diaries or combat achievements are assumed (the elite combat achievements raise the Tombs'
clue chance). Full performance is assumed wherever a rate "scales down based on the individual's
performance" (the Theatre's tertiaries).

- **The Tombs' points.** The guide never states its reward points. It assumes a unique "roughly
  1/22.5" per raid, and its `regchance` is 0.95555, which is 1 - 1/22.5 to five places. The chest
  page's rule is 1% per `10,500 - 20 × RL` points at raid level 300 or below, so 1/22.5 is
  `100 × 4,500 / 22.5` = 20,000 points, after "the starting 5,000 points are subtracted". So the
  player ends the raid with 25,000 reward points, and every points rule uses 20,000.
- **Equal shares.** In the Theatre and the Archive a team's unique goes to one player, weighted by
  contribution. With equal contribution each of three players has a third of the team's chance, as
  both money-making guides assume.

### The four raids: per raid

- **Chambers of Xeric.** The unique chance is 33,000 / 867,600 = 55/1446 (1/26.29): "For every
  8,676 total points obtained, a 1% chance". In groups the player's own points decide how likely
  they are to get the team's unique, so below the 570,000-point cap a player's chance is their own
  points / 867,600 whatever the team size; solo it is the same thing. The unique table is the normal
  mode's 60 weights (the August 2026 weights now on the page; Challenge Mode's 56 are left out).
  Otherwise two rolls on the 33 common rows at 1/33 each. Each row's quantity is
  `floor(33,000 / divisor)` from the loot calculator, and 1 for the torn prayer scroll and dark
  relic:

  | Row | Divisor | At 33,000 |
  |---|---|---|
  | Death rune | 36 | 916 |
  | Blood rune | 32 | 1,031 |
  | Soul rune | 20 | 1,650 |
  | Rune arrow | 14 | 2,357 |
  | Dragon arrow | 202 | 163 |
  | Grimy ranarr weed, toadflax, irit leaf, avantoe, kwuarm | 946, 624, 194, 389, 454 | 34, 52, 170, 84, 72 (noted) |
  | Grimy snapdragon, cadantine, lantadyme, dwarf weed, torstol | 1,560, 396, 297, 240, 972 | 21, 83, 111, 137, 33 (noted) |
  | Silver ore, coal, gold ore, mithril ore, adamantite ore, runite ore | 20, 20, 44, 32, 166, 2,000 | 1,650, 1,650, 750, 1,031, 198, 16 (noted) |
  | Uncut sapphire, emerald, ruby, diamond | 188, 142, 242, 508 | 175, 232, 136, 64 (noted) |
  | Lizardman fang | 28 | 1,178 |
  | Pure essence, saltpetre, teak plank, mahogany plank, dynamite | 2, 28, 96, 238, 54 | 16,500, 1,178, 343, 138, 611 (noted) |
  | Torn prayer scroll, dark relic | - | 1 |

  The calculator's divisors were not guessed from the listed maximums, but they do match them: each
  row's maximum is `floor(131,071 / divisor)` ("Common loot awarded at the end of the raid scales up
  to 131,071 points"), except three herbs that are one higher on the chest page: avantoe 337
  (336), kwuarm 289 (288) and lantadyme 442 (441). Not modelled: the two common rolls "cannot end on
  the same drop", and the herb rows' "33% of these herbs will drop as seeds instead" (the
  calculator's seed rows). The elite clue (1/12) "is only rolled when the player does not get a
  broadcasted unique reward", so it is a tertiary at `(1 - p) / 12` = 1391/17352 (1/12.47). Olmlet
  (1/53) "is only rolled when the player gets a broadcasted unique reward", so it is a tertiary at
  `p / 53` = 55/76638 (1/1,393.4). The two are independent rolls here, so a run can give both,
  which the game doesn't allow. Their chances per run are the game's.
- **Chambers of Xeric: the ancient tablet and dark journal.** The tablet (1/10) "does not drop if
  one is already owned", and the dark journal is always given "unless it is stored in their bank".
  Neither is a collection log item. The precedent is that a one-off item given only while the
  player owns none is kept when it is a log item (Araxxor's coagulated venom, the Gauntlet cape)
  and left out when it isn't (the Mad Angel's sunstone crystal, the tattered KQ head). Both are left
  out.
- **Chambers of Xeric: Tekton's onyx.** The Onyx in the Chambers of Xeric log isn't chest loot:
  Tekton, one of the random combat rooms, drops it at 1/450 "in solo normal mode raids". The
  money-making guide counts it at 1/450 per raid, as if every raid had Tekton, so it is a 1/450
  tertiary here, and the log can be completed. The Wiki gives no chance of a raid having Tekton's
  room, so this is generous, and it doesn't match the hitpoints, which count only the Olm. No other
  room boss drop is included.
- **Theatre of Blood.** "Without any deaths, there is a 1/9.1 (~11%) chance of a unique reward
  across the team", "the same regardless of the number of players". Each player's third is 10/273
  (1/27.3). The unique table is the normal mode's 19 weights; the cabbage and the message are left
  out (only with 0 contribution points). Otherwise three rolls on the 29 common rows (out of 30;
  vial of blood is 2/30), with the listed quantities. The elite clue is 3/25, the normal-mode rate
  in the row's note ("1/25, 3/25, and 3.5/25" for Entry, Normal and Hard Mode; the row itself shows
  Entry Mode's 1/25). Lil' Zik is the normal-mode base rate, 1/650. The holy and sanguine ornament
  kits and sanguine dust are Hard Mode only.
- **Tombs of Amascut.** At 20,000 points and raid level 300 the unique chance is 20,000 /
  (100 × (10,500 - 20 × 300)) = 2/45 (1/22.5), far below the 55% cap. The unique table is the
  module's weights out of 240 for raid levels up to 300 (fang and lightbearer 70 each, ward 30,
  each Masori piece 20, shadow 10), which are the chest page's 7/24, 3/24, 2/24 and 1/24 and the
  "150-300" row of its weight table. All seven are allowed at raid level 300, so no 1/50 roll
  applies. Otherwise three rolls on the 27 common rows at 1/27 each. Each quantity is
  `max(floor(floor(20,000 / divisor) × 1.15), 1)`, computed exactly:

  | Rows | Divisor | At 20,000, level 300 |
  |---|---|---|
  | Coins | 1 | 23,000 |
  | Death rune, soul rune | 20, 40 | 1,150, 575 |
  | Gold ore, dragon dart tip, mahogany logs, sapphire | 90, 100, 180, 200 | 255, 230, 127, 115 |
  | Emerald, gold bar, potato cactus, raw shark | 250 | 92 each |
  | Ruby, diamond, raw manta ray | 300, 400, 450 | 75, 57, 50 |
  | Cactus spine, dragonstone | 600 | 37 each |
  | Battlestaff, coconut milk, lily of the Sands | 1,100 | 20 each |
  | Toadflax, ranarr, torstol, snapdragon seed | 2,000, 2,500, 3,200, 3,200 | 11, 9, 6, 6 |
  | Dragon med helm, magic seed, blood essence | 4,000, 6,500, 7,500 | 5, 3, 2 |
  | Cache of runes | - | 1 ("the quantity is set to one") |

  The calculator multiplies by 1.15 in floating point, which would give 229 dragon dart tips and
  114 sapphires; the page's formula gives 230 and 115, which are used. The noted rows stay noted.
  Tertiary: the thread of Elidinis at its base 1/10 (its bad-luck mitigation, and the 1/50 after
  the first, not modelled), each of the four jewels at its base 1/50 (bad-luck mitigation and the
  duplicate protection not modelled), the elite clue at 1% per 2,000 points, 10/100 = 1/10 (the cap
  is 25%), and Tumeken's guardian at 1% per `350,000 - 700 × 300` = 140,000 points, so 20,000 /
  14,000,000 = 1/700. The thread, jewels and clue come with a unique too ("Tertiary rewards are
  still obtainable"). No challenge reward is reachable at raid level 300: the lowest needs 350.
  The fossilised dung is only for a player with under 1,500 points.
- **The Fractured Archive.** "There is a 1/14 chance of successfully rolling for a unique drop", and
  in a trio "the drop rate becomes ~1/13.46 (1/14 x 1.04)". Each player's third is 1.04 / 42 =
  13/525 (1/40.38). The unique table is out of 15. Otherwise the standard table (330) is rolled
  three times, and "Each roll has a 1/51 chance to be multiplied five times". That is modelled
  exactly: each row is split into a row at its listed quantity with weight `w × 50` and a row at
  five times the quantity with weight `w`, out of 16,830 (330 × 51). The tertiaries are as listed:
  the elite clue 1/10, the archivist's folio 1/15 (it stops once owned), each fragment 1/80 (the
  1/20 for any fragment, with duplicate avoidance not modelled) and Orion 1/750. The page lists
  them under "Standard loot". They are rolled on every run, unique or not, so Orion stays at the
  Wiki's 1/750; if they were only rolled without a unique, Orion would be 1/769. The cabbage (junk
  table) is left out: it is only for a poor performance, and it replaces everything else.

### The four raids: checked against the Wiki's own figures

| Raid | Here, per run | The Wiki | Match |
|---|---|---|---|
| Chambers of Xeric | Twisted bow 1/788.7, prayer scrolls 1/112.7 each | The guide's 1/788.6 and 1/112.7 (it uses 1% per 8,675 points; the chest page says 8,676) | Off by 0.01% |
| Chambers of Xeric | Olmlet at 26,025 points: 1/1,766.9 | The chest page's note: "~1/1,765" | Off by 0.1%; the note's own numbers give 1/1,766.9 |
| Chambers of Xeric | Onyx 1/450 | The guide's 1/450 | Yes |
| Theatre of Blood | Scythe 1/518.7, hilt 1/64.8, each other unique 1/259.4 | The guide's `(w/172.9) × (1/3)` | Yes |
| Tombs of Amascut | Shadow 1/540, each Masori piece 1/270, ward 1/180, fang and lightbearer 1/77.1 | The guide's 1/540, 2/540, 3/540, 7/540 | Yes |
| Tombs of Amascut | Common quantities at 20,000 points (23,000 coins) | The guide's 21,981 coins, 1,098 death runes and so on | No: those are the Wiki calculator's quantities at its own estimate of 19,114 points for this raid (Walk the Path, the calculator's kill order), which would make the unique 1/23.5, not the guide's 1/22.5 |
| The Fractured Archive | Obligator 1/605.8, crossbows 1/302.9, Horn of Bran 1/201.9, tome 1/151.4, Rondache 1/121.2 | The guide's `1/14 × 1.04 × w/15 × 1/3` | Yes |
| The Fractured Archive | Common quantities × 55/51 on average | The chest page's `1 × 50/51 + 5 × 1/51` = 55/51 | Yes; the guide's `1 + 5/51` is 1.8% higher |

## Left out

| Boss | Drop | Reason |
|---|---|---|
| Giant Mole, Sarachnis, KBD, Kraken, Zulrah, Vorkath | Brimstone key | Only dropped on a Konar slayer task |
| King Black Dragon | Key (elite) | Only dropped during the elite clue step |
| Bryophyta | Moss giant bone (1/4) | Only dropped during Rag and Bone Man II |
| Obor, Bryophyta | Free-to-play world tables | Members' tables used instead |
| Scurrius | Non-MVP (group) table | Solo/MVP table used |
| Vorkath | Dragon Slayer II quest version | Post-quest table used |

None of these boss pages lists a Wilderness key, a Larran's key, or an Echo or Leagues drop
table, so nothing else was left out.

For the 44 added bosses:

| Boss | Drop | Reason |
|---|---|---|
| All 44 that list it | Brimstone key | Only dropped on a Konar slayer task |
| Callisto, Artio, Venenatis, Spindel, Vet'ion, Calvar'ion, Chaos Elemental, Chaos Fanatic, Crazy archaeologist, Scorpia | Wilderness Slayer table (Larran's key, Slayer's enchantment) | Only on a Krystilia task |
| Callisto, Venenatis, Vet'ion | Bones | Group fights only, for eligible players who didn't deal the most damage |
| Amoxliatl, Cerberus, Shellbane gryphon | Elite clue scroll and reward casket at "Always" | Only during an elite clue step for that monster (their 1/200 and 1/100 elite clues are kept) |
| Dagannoth Rex, Prime, Supreme | Dagannoth ribs (1/4) | Only during Rag and Bone Man II |
| Zilyana, Graardor, Kree'arra, K'ril | Frozen key piece | Only during The Frozen Door miniquest |
| Duke Sucellus, Vardorvis | Odd key, Temple key | Quest variant only |
| Duke Sucellus, Leviathan, Vardorvis, Whisperer | Ancient blood ornament kit | Awakened encounter only, as the last of the four |
| DT2 bosses | Awakened and quest versions | Normal post-quest fight used |
| Kalphite Queen | Kq head (tattered) | One-off reward on the 256th kill; not on the log tab |
| The Mad Angel | Sunstone crystal | Only while the bank chest is unbuilt and no crystal is owned |
| Brutus | Three free-to-play coin rows | Members' table used |
| Nex | 20 rows with only "Common"/"Uncommon" rarities | The Wiki gives no rate |
| Yama | Junk table (under 15% contribution), contract rewards, Yami at 1/100 under the Contract of Familiar Acquisition | Solo kill without a contract |
| Maggot King | "Take-eggs" table | Alternative to the loot; open-stomach used |
| Araxxor | "Destroy" option (Nid at 1/1,500) | Harvest used |
| Royal Titans | "Take pages" (10-19 desiccated pages) and "Sacrifice" (pet at 1/1,500) | "Loot" used |

None of the added pages lists an Echo, Leagues or event drop table. The Wilderness bosses' looting
bag (1/3) is a normal Wilderness drop and is kept.

For the 13 chest bosses:

| Boss | Drop | Reason |
|---|---|---|
| Blue Moon, Eclipse Moon, Blood Moon | The other two Moons' sets | One Moon is defeated per run |
| Blue Moon, Eclipse Moon, Blood Moon | 3 and 6 standard rolls | Only for two or three Moons in one chest |
| Barrows | Strange icon | Only during the His Faithful Servants miniquest |
| Barrows | 1/190 elite clue, 50% more runes | Need the elite combat achievements or the hard Morytania Diary |
| The Gauntlet, The Corrupted Gauntlet | Incomplete-loot and junk tables | Only for runs that don't kill Hunllef |
| TzTok-Jad | Pet at 1/100, doubled Tokkul, a second 1/200 pet roll for the cape | On a Slayer task; the Elite Karamja Diary; exchanging the cape |
| TzKal-Zuk | Pet at 1/75, doubled Tokkul, a second 1/100 pet roll for the cape | The same |
| Sol Heredit | Token (Varlamore) on wave 3 (rate "Varies") | A daily token, only on some days for some character-name lengths |
| Sol Heredit | Another 1/200 pet roll or 4,000 splinters for the quiver | Exchanging the quiver |
| Doom of Mokhaiotl | Deep delves (9 and deeper); charges added to a charged Eye of Ayak | Claimed after delve 8; not loot |
| Wintertodt | Magic seed and torstol seeds | Only in place of a 4th pair of warm gloves or bruma torch |
| Wintertodt | Extra supply crates from Ignisia | Trade-ins, not cart rolls |
| Tempoross | 25 soaked pages (two 1/400 rows) | Only in place of a fish barrel or tackle box already owned |
| Tempoross | The casket's own rewards and clue scrolls | The casket isn't opened (no log item comes from it) |
| Zalcano | Group shares of the tool-seed roll; the Mining-experience option | Solo kill, taking items |

None of these pages lists an Echo, Leagues or event drop table.

For the four raids:

| Raid | Drop | Reason |
|---|---|---|
| Chambers of Xeric | Challenge Mode unique table (out of 56) | Normal mode used |
| Chambers of Xeric | Twisted ancestral colour kit (1/75), metamorphic dust (1/400) | Challenge Mode only, in a timed raid |
| Chambers of Xeric | Ancient tablet (1/10), dark journal (always) | One-off items given only when none is owned; not log items |
| Chambers of Xeric | The herb rows' seeds, and the rule that the two rolls differ | Not modelled |
| Chambers of Xeric | The room bosses' own drops (books, potions, seeds) | Not chest loot; only Tekton's onyx, a log item, is kept |
| Theatre of Blood | Hard Mode unique table (out of 18), Entry Mode | Normal mode used |
| Theatre of Blood | Cabbage, message (always) | Only with 0 contribution points |
| Theatre of Blood | Holy ornament kit (1/100), sanguine ornament kit (1/150), sanguine dust (1/275) | Hard Mode only |
| Tombs of Amascut | Fossilised dung | Only under 1,500 reward points |
| Tombs of Amascut | Masori crafting kit, Menaphite ornament kit, cursed phalanx, the five remnants | Challenge rewards for raid level 350+, 425+, 500+ and 450+ |
| Tombs of Amascut | The thread's 1/50 after the first, and the thread and jewels' bad-luck mitigation and duplicate protection | Base rates used |
| The Fractured Archive | Cabbage (junk table) | Only for a poor performance; it replaces all other loot |

None of the raid pages lists an Echo, Leagues or event drop table.

## Collection log items from the lair chests

| Boss | Item | Source on the Wiki |
|---|---|---|
| Obor | Hill giant club (20756) | Obor's chest, 1/118 per opening with a giant key; Obor drops the key at 1/16 |
| Bryophyta | Bryophyta's essence (22372) | Bryophyta's chest, 1/118 per opening with a mossy key; Bryophyta drops the key at 1/16 |

No boss drop gives these two items; they come from a separate chest. Idle Bosses counts each kill
as one chest opening, so `tools/fetch_drops.py` adds each as a 1/118 tertiary roll (`CHEST_ROLLS`).
Modelling the key as well would make them 1/16 × 1/118 = 1/1888 per kill, too slow for the first
bosses on the ladder. The giant key and mossy key are in the in-game "Miscellaneous" log, not in the
boss tabs, so they aren't in `log`.

## Collection log items from a separate roll

| Boss | Item | How it's reached |
|---|---|---|
| Abyssal Sire | Abyssal orphan, head, bludgeon pieces, jar of miasma, dagger, whip | `font_of_consumption`, rolled by a 1/100 tertiary next to the 1/100 Unsired |
| Yama | Rite of vile transference (30806) | `dossier`, 1/15 per opening, rolled by a tertiary at the dossier's 119/1440 per kill (1/181.5 overall, as the Wiki says) |
| Yama | Oathplate helm, chest, legs | Also 1/403 each from `forgotten_lockbox`, rolled at the lockbox's 1309/43200 |

The rite of vile transference isn't on Yama's drop table at all. It only comes from opening a
dossier. Every other log item on the 44 bosses' tabs is reachable through their own drops.

One raid log item isn't chest loot:

| Raid | Item | How it's reached |
|---|---|---|
| Chambers of Xeric | Onyx (6573) | Tekton's 1/450, as a tertiary on every run (see the Chambers of Xeric notes above) |

## Collection log items no run can give

| Boss | Items | Why |
|---|---|---|
| Tempoross | Spirit angler headband, top, waders, boots | Bought from Gita Prymes for 1,200 spirit flakes plus the matching angler piece; no reward roll gives them |

These four are left out of `tempoross`'s `log` (`notInLog` in the script), so its log has 8 of
the tab's 12 items. The script checks that they really can't drop, and that every other item on
each of the 13 bosses' tabs can.

For the four raids:

| Raid | Items | Why |
|---|---|---|
| Chambers of Xeric | Xeric's guard, warrior, sentinel, general, champion | Capes from Captain Rimor for 100, 500, 1,000, 1,500 and 2,000 Challenge Mode completions |
| Chambers of Xeric | Metamorphic dust, twisted ancestral colour kit | Challenge Mode only |
| Theatre of Blood | Sinhaza shroud tiers 1-5 | Capes from the Mysterious Stranger for 100 to 2,000 Normal or Hard Mode completions |
| Theatre of Blood | Holy ornament kit, sanguine ornament kit, sanguine dust | Hard Mode only |
| Tombs of Amascut | Icthlarin's shroud tiers 1-5 | Capes from the lobby chest for 100 to 2,000 Normal or Expert completions |
| Tombs of Amascut | Masori crafting kit, Menaphite ornament kit, cursed phalanx, remnants of Akkha, Ba-Ba, Kephri and Zebak, ancient remnant | Challenge rewards for raid level 350+ to 500+ (the chest page's table) |
| The Fractured Archive | Living record tiers 1-5 | Capes for 100 to 2,000 completions |

So the logs have 16 of the Chambers of Xeric tab's 23 items, 9 of the Theatre of Blood's 17, 14
of the Tombs of Amascut's 27 and 12 of The Fractured Archive's 17. The script checks each of these
against the raid page's milestone or challenge table, and that every other item on the four tabs
can drop. The raid tabs share no item with any other boss's log.

## Collection log tabs shared by several bosses

These in-game tabs cover more than one boss here: Callisto and Artio, Venenatis and Spindel,
Vet'ion and Calvar'ion, Dagannoth Kings, and The Nightmare (with Phosani's Nightmare). Each boss's
`log` holds the tab's items that boss can drop, in tab order. The script checks that every item on
each tab is reachable from at least one of its bosses.

| Boss | Left out of its `log` |
|---|---|
| Dagannoth Rex | Prime's and Supreme's pets, archers ring, seers ring, seercull, mud battlestaff |
| Dagannoth Prime | Rex's and Supreme's pets, berserker ring, archers ring, warrior ring, seercull |
| Dagannoth Supreme | Prime's and Rex's pets, berserker ring, seers ring, warrior ring, mud battlestaff |
| The Nightmare | Slepey tablet, parasitic egg (Phosani's only) |

The Wilderness pairs drop every item on their tab, so their `log`s are the full tab. The dragon
pickaxe, dragon 2h sword, dragon axe, godsword shards, Virtus pieces, chromium ingot, awakener's orb
and the Nightmare uniques appear in several bosses' `log`s; the plugin counts each item once.

Two of the chest bosses' tabs are shared too: Moons of Peril (the three Moons) and The Gauntlet
(both variants).

| Boss | Left out of its `log` |
|---|---|
| Blue Moon | The Eclipse Moon and Blood Moon sets |
| Eclipse Moon | The Blue Moon and Blood Moon sets |
| Blood Moon | The Blue Moon and Eclipse Moon sets |
| The Gauntlet | Gauntlet cape (the Corrupted Gauntlet only) |

Items in more than one of these logs: atlatl darts (all three Moons), the crystal seeds and
Youngllef (both Gauntlets), uncut onyx (Sol Heredit and Zalcano, as well as Zulrah and Skotizo),
and the dragon axe (Wintertodt and the Dagannoth Kings).

## Item IDs to keep an eye on

All 909 distinct item IDs (273 for the first nine bosses, 754 before the 13 chest bosses, 855
before the four raids) exist in
RuneLite 1.13.6 `net.runelite.api.gameval.ItemID`, and each constant name matches its item (for
example `KBDPET`, `TOTS`, `SNAKEBOSS_SCALE`, `UNIDENTIFIED_MARENTILL`, `DUKESUCELLUSPET`,
`DEATH_CHARGE_SCROLL` for the rite of vile transference, `TWINFLAME_PIECE_1`/`_2` for the ice and
fire element staff crowns). These are worth knowing about:

- **Clue scrolls** use one step's item per tier. For example, elite is 12073
  (`TRAIL_ELITE_EMOTE_EXP1`). That is RuneLite's own generic clue ID, but in game each clue step
  has its own item.
- **Ensouled giant head** is 13474 (`ARCEUUS_CORPSE_GIANT_INITIAL`). The Wiki page also lists
  13475 (`ARCEUUS_CORPSE_GIANT`). 13474 was picked as the lower ID; the `_INITIAL` name suggests it
  is the freshly dropped head, but this isn't confirmed. The added bosses' ensouled dagannoth,
  demon, hellhound, kalphite and scorpion heads follow the same rule (`..._INITIAL`).
- **Giantsoul amulet.** The Royal Titans' row says "Giantsoul amulet", whose infobox ID is 30638
  (`GIANTSOUL_AMULET_CHARGED`). The titans drop the uncharged one, which is also the log item:
  30637 (`GIANTSOUL_AMULET_UNCHARGED`). `DROP_NAME_ALIASES` in the script maps the row to it.
- **Lower IDs for the plain versions.** Potions with two IDs use the lower one, for example
  prayer potion(4) 2434 rather than 20393 (`BR_PRAYER4`) and super combat potion(1) 12701 rather
  than 23549 (`BR_1DOSE2COMBAT`). The same goes for the looting bag (11941, not the open bag 22586),
  the granite maul (4153, not `GRANITE_MAUL_PRETTY` 12848) and Tangleroot (20661, the base form).
- **Odd-looking constants that are right.** Charged ice is 27643 (`MUSPAH_PET_MORPH`), coagulated
  venom 29781 (`ARAXXOR_PET_MORPH`) and the parasitic egg 25838 (`NIGHTMARE_CHALLENGE_MORPH`); all
  three turn a pet into its other form. The unholy symbol is 1724 (`BLESSEDSNAKE`), pure essence
  7936 (`BLANKRUNE_HIGH`), and the Pendant of Ates (inert) 29892 (`PENDANT_OF_ATES_EMPTY`).
- **The chest bosses' odd-looking constants.** The crystal weapon seed is 4207 (`CRYSTAL_SEED_OLD`;
  it reuses the old crystal seed's ID), the Zalcano shard 23908 (`ZALCANO_PICKAXE_KIT`), the
  Mokhaiotl waystone 31099 (`DOM_TELEPORT_ITEM`), Tokkul 6529 (`TZHAAR_TOKEN`), the bolt rack 4740
  (`BARROWS_KARIL_AMMO`), and the Blue Moon items `FROST_MOON_*`. The Barrows pieces are the
  undegraded items (`BARROWS_AHRIM_HEAD` and so on). All are the collection log's own IDs.
- **Names the log and the drop rows spell differently.** The log page says "Eye of Ayak
  (uncharged)" and data.json says "Eye of Ayak"; both are 31115 (`EYE_OF_AYAK_UNCHARGED`; the
  charged staff is 31113). The Colosseum's "Dizana's quiver" row is stored as the uncharged quiver,
  28947 (`DIZANAS_QUIVER_UNCHARGED`), the log item; the charged one is 28951.
  `CHEST_NAME_ALIASES` in the script maps the row.
- **The raids' odd-looking constants.** The prayer scrolls are `RAIDS_PRAYERSCROLL` (dexterous,
  21034), `RAIDS_PRAYERSCROLL_AUGURY` (arcane, 21079) and `RAIDS_PRAYERSCROLL_PRESERVE` (torn,
  21047); the dark relic is `RAIDS_ANCIENT_RELIC`, the avernic defender hilt
  `INFERNAL_DEFENDER_HILT`, the cache of runes `TOA_RUNE_CACHE`, the four fragments `TFA_MOTE_*`,
  the Horn of Bran `TFA_ZEAL_REWARD` and the archivist's folio (inert) `ARCHIVISTS_FOLIO_EMPTY`.
  The pets are `OLMPET`, `VERZIKPET`, `WARDENPET_TUMEKEN` and `OCELLUSPET` (Orion). Of the log
  items no run gives, the capes are `COX_CHALLENGE_CAPE_T1`-`T5` and `TFA_CAPE_T1`-`T5`, the
  metamorphic dust `RAIDS_CHALLENGE_MORPH` and the cursed phalanx `OSMUMTENS_FANG_ORNAMENT_KIT`.
  All are the collection log's own IDs. Of the common rows, the Tombs' blood essence is the inactive
  one, 26390 (`BLOOD_ESSENCE_INACTIVE`), and names the bosses above already used keep their IDs,
  for example dynamite 13573 (`LOVAKENGJ_DYNAMITE_FUSED`) and the aether catalyst 30771
  (`COSMIC_SOUL_CATALYST`). No name ended up with two different IDs.
