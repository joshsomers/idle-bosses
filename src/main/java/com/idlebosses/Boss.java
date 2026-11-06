package com.idlebosses;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;

// The boss ladder, easiest first. Animations were matched to each NPC's skeleton in the game cache.
// A boss with no defend animation just takes the hitsplat; one with no spawn animation appears in a smoke puff.
// Bosses with no walk use their idle animation, so they glide. Unlock slots are tuned with the kill requirement in
// place; ProgressionTest prints how long each unlock takes.
@Getter
@RequiredArgsConstructor
enum Boss
{
	OBOR("obor", "Obor", NpcID.HILLGIANT_BOSS, -1, 120, Skill.WOODCUTTING, 0,
		AnimationID.GIANT_UPDATE_BASIC_READY, AnimationID.GIANT_UPDATE_BASIC_WALK, AnimationID.GIANT_UPDATE_BASIC_DEFEND, AnimationID.GIANT_UPDATE_BASIC_DEATH, -1),
	BRYOPHYTA("bryophyta", "Bryophyta", NpcID.GB_MOSSGIANT, -1, 115, Skill.FARMING, 0,
		AnimationID.GIANT_UPDATE_MOSS_READY, AnimationID.GIANT_UPDATE_MOSS_WALK, AnimationID.GIANT_UPDATE_MOSS_DEFEND, AnimationID.GIANT_UPDATE_MOSS_DEATH, -1),
	BRUTUS("brutus", "Brutus", NpcID.COWBOSS, -1, 58, Skill.COOKING, 0,
		AnimationID.COW_BOSS_IDLE, AnimationID.COW_BOSS_WALK, AnimationID.COW_BOSS_DEFEND, AnimationID.COW_BOSS_DEATH, AnimationID.COW_BOSS_SPAWN),
	WINTERTODT("wintertodt", "Wintertodt", -1, 32247, 700, Skill.FIREMAKING, 3,
		AnimationID.WINTERTODT_IDLE_B, AnimationID.WINTERTODT_IDLE_B, -1, AnimationID.WINTERTODT_RESTING, -1),
	CHAOS_FANATIC("chaos_fanatic", "Chaos Fanatic", NpcID.CHAOS_FANATIC, -1, 225, Skill.MAGIC, 5,
		AnimationID.DRUNK_PLAYER_READY, AnimationID.DRUNK_PLAYER_WALK, AnimationID.HUMAN_UNARMEDBLOCK, AnimationID.HUMAN_DEATH, AnimationID.HUMAN_SMOKEPUFF_TELE_APPEAR),
	TEMPOROSS("tempoross", "Tempoross", NpcID.TEMPOROSS_BOSS_READY, -1, 350, Skill.FISHING, 6,
		AnimationID.FSB_READY, AnimationID.FSB_READY, -1, AnimationID.FSB_DEATH, AnimationID.FSB_ENRAGED_SPAWN),
	CRAZY_ARCHAEOLOGIST("crazy_archaeologist", "Crazy archaeologist", NpcID.CRAZY_ARCHAEOLOGIST, -1, 225, Skill.THIEVING, 9,
		AnimationID.BOOK_CHUCK_READY, AnimationID.HUMAN_WALK_F, AnimationID.HUMAN_UNARMEDBLOCK, AnimationID.HUMAN_DEATH, AnimationID.HUMAN_SMOKEPUFF_TELE_APPEAR),
	SCURRIUS("scurrius", "Scurrius", NpcID.RAT_BOSS_NORMAL, -1, 500, Skill.HUNTER, 10,
		AnimationID.NPC_RAT_BOSS_IDLE_01, AnimationID.NPC_RAT_BOSS_WALK_01, -1, AnimationID.NPC_RAT_BOSS_DEATH_01, AnimationID.NPC_RAT_BOSS_SPAWN_01),
	GIANT_MOLE("giant_mole", "Giant Mole", NpcID.MOLE_GIANT, -1, 200, Skill.MINING, 12,
		AnimationID.MOLE_READY, AnimationID.MOLE_WALK, AnimationID.MOLE_DEFEND, AnimationID.MOLE_DEATH, AnimationID.MOLE_BURROW_UP),
	SCORPIA("scorpia", "Scorpia", NpcID.SCORPIA, -1, 200, Skill.HERBLORE, 15,
		AnimationID.SCORPION_UPDATE_READY, AnimationID.SCORPION_UPDATE_FIGHT_ARENA_WALK, AnimationID.SCORPION_UPDATE_DEFEND, AnimationID.SCORPION_UPDATE_DEATH, -1),
	SHELLBANE_GRYPHON("shellbane_gryphon", "Shellbane Gryphon", NpcID.GRYPHON_BOSS, -1, 400, Skill.SAILING, 16,
		AnimationID.GRYPHON_BOSS_IDLE01, AnimationID.GRYPHON_BOSS_WALK01, -1, AnimationID.GRYPHON_BOSS_DEATH01, AnimationID.GRYPHON_BOSS_SPAWN01),
	DERANGED_ARCHAEOLOGIST("deranged_archaeologist", "Deranged archaeologist", NpcID.FOSSIL_CRAZY_ARCHAEOLOGIST, -1, 200, Skill.THIEVING, 18,
		AnimationID.BOOK_CHUCK_READY, AnimationID.HUMAN_WALK_F, AnimationID.HUMAN_UNARMEDBLOCK, AnimationID.HUMAN_DEATH, AnimationID.HUMAN_SMOKEPUFF_TELE_APPEAR),
	BARROWS("barrows", "Barrows", NpcID.BARROWS_DHAROK, -1, 600, Skill.PRAYER, 19,
		AnimationID.BARROW_DHAROK_READY, AnimationID.BARROW_DHAROK_WALK, AnimationID.HUMAN_DHSWORD_BLOCK, AnimationID.HUMAN_DEATH, AnimationID.HUMAN_SMOKEPUFF_TELE_APPEAR),
	KING_BLACK_DRAGON("king_black_dragon", "King Black Dragon", NpcID.KING_DRAGON, -1, 240, Skill.SMITHING, 25,
		AnimationID.DRAGON_READY, AnimationID.DRAGON_WALK_KBD, AnimationID.DRAGON_BLOCK_KBD, AnimationID.DRAGON_DEATH, -1),
	CALVARION("calvarion", "Calvar'ion", NpcID.VETION_SINGLE, -1, 420, Skill.PRAYER, 27,
		AnimationID.NPC_VETION_IDLE_01, AnimationID.NPC_VETION_WALK_01, AnimationID.NPC_VETION_DEFEND_SHIELD_01, AnimationID.NPC_VETION_DEATH_01, AnimationID.NPC_VETION_SPAWN_01),
	AMOXLIATL("amoxliatl", "Amoxliatl", NpcID.AMOXLIATL, -1, 520, Skill.FIREMAKING, 29,
		AnimationID.AMOXLIATL_IDLE, AnimationID.AMOXLIATL_WALK, -1, AnimationID.AMOXLIATL_DEATH, -1),
	HESPORI("hespori", "Hespori", NpcID.HESPORI, -1, 300, Skill.FARMING, 32,
		AnimationID.HESPORI_READY, AnimationID.HESPORI_READY, -1, AnimationID.HESPORI_DEATH, AnimationID.HESPORI_SPAWN),
	ZALCANO("zalcano", "Zalcano", NpcID.ZALCANO, -1, 1300, Skill.MINING, 38,
		AnimationID.ZALCANO_IDLE, AnimationID.ZALCANO_WALK, AnimationID.ZALCANO_DEFEND, AnimationID.ZALCANO_DEATH, -1),
	KRAKEN("kraken", "Kraken", NpcID.SLAYER_KRAKEN_BOSS, -1, 255, Skill.FISHING, 40,
		AnimationID.SWAN_QUEEN_READY, AnimationID.SWAN_QUEEN_READY, AnimationID.SWAN_QUEEN_DEFEND, AnimationID.SWAN_QUEEN_DEATH, AnimationID.SLAYER_KRAKEN_ARISE),
	THERMONUCLEAR_SMOKE_DEVIL("thermonuclear_smoke_devil", "Thermonuclear smoke devil", NpcID.SMOKE_DEVIL_BOSS, -1, 240, Skill.MAGIC, 41,
		AnimationID.LORE_DUST_DEVIL_READY, AnimationID.LORE_DUST_DEVIL_WALK, AnimationID.LORE_DUST_DEVIL_DEFEND, AnimationID.LORE_DUST_DEVIL_DEATH, AnimationID.LORE_LUST_DEVIL_SPAWN),
	SPINDEL("spindel", "Spindel", NpcID.VENENATIS_SINGLES, -1, 515, Skill.CRAFTING, 43,
		AnimationID.NPC_VENENATIS_IDLE_01, AnimationID.NPC_VENENATIS_RUN_01, -1, AnimationID.NPC_VENENATIS_DEATH_01, AnimationID.NPC_VENENATIS_SPAWN_01),
	DAGANNOTH_REX("dagannoth_rex", "Dagannoth Rex", NpcID.DAGCAVE_MELEE_BOSS, -1, 255, Skill.MAGIC, 45,
		AnimationID.DAGANNOTH_MEGANOTH_READY, AnimationID.DAGANNOTH_MEGANOTH_WALK, AnimationID.DAGANNOTH_MEGANOTH_DEFEND, AnimationID.DAGANNOTH_MEGANOTH_DEATH, -1),
	DAGANNOTH_PRIME("dagannoth_prime", "Dagannoth Prime", NpcID.DAGCAVE_MAGIC_BOSS, -1, 255, Skill.RANGED, 47,
		AnimationID.DAGANNOTH_MEGANOTH_READY, AnimationID.DAGANNOTH_MEGANOTH_WALK, AnimationID.DAGANNOTH_MEGANOTH_DEFEND, AnimationID.DAGANNOTH_MEGANOTH_DEATH, -1),
	DAGANNOTH_SUPREME("dagannoth_supreme", "Dagannoth Supreme", NpcID.DAGCAVE_RANGED_BOSS, -1, 255, Skill.ATTACK, 49,
		AnimationID.DAGANNOTH_MEGANOTH_READY, AnimationID.DAGANNOTH_MEGANOTH_WALK, AnimationID.DAGANNOTH_MEGANOTH_DEFEND, AnimationID.DAGANNOTH_MEGANOTH_DEATH, -1),
	BLUE_MOON("blue_moon", "Blue Moon", NpcID.PMOON_BOSS_BLUE_MOON_VIS, -1, 500, Skill.FIREMAKING, 52,
		AnimationID.NPC_DJINN_01_IDLE, AnimationID.NPC_DJINN_01_WALK, -1, AnimationID.NPC_DJINN_01_TELEPORT_IDLE_DISAPPEAR, AnimationID.NPC_DJINN_01_SPAWN),
	ECLIPSE_MOON("eclipse_moon", "Eclipse Moon", NpcID.PMOON_BOSS_ECLIPSE_MOON_VIS, -1, 500, Skill.HUNTER, 55,
		AnimationID.NPC_DJINN_02_IDLE, AnimationID.NPC_DJINN_02_IDLE, -1, AnimationID.NPC_DJINN_02_TELEPORT_IDLE_DISAPPEAR, AnimationID.NPC_DJINN_02_SPAWN),
	BLOOD_MOON("blood_moon", "Blood Moon", NpcID.PMOON_BOSS_BLOOD_MOON_VIS, -1, 500, Skill.HERBLORE, 56,
		AnimationID.NPC_DJINN_01_IDLE, AnimationID.NPC_DJINN_01_WALK, -1, AnimationID.NPC_DJINN_01_TELEPORT_IDLE_DISAPPEAR, AnimationID.NPC_DJINN_01_SPAWN),
	CHAOS_ELEMENTAL("chaos_elemental", "Chaos Elemental", NpcID.CHAOSELEMENTAL, -1, 250, Skill.RUNECRAFT, 58,
		AnimationID.CHAOSELEMENTAL_READY, AnimationID.CHAOSELEMENTAL_WALK, -1, AnimationID.CHAOSELEMENTAL_DEATH, -1),
	SARACHNIS("sarachnis", "Sarachnis", NpcID.SARACHNIS, -1, 400, Skill.CRAFTING, 60,
		AnimationID.HOSDUN_SPIDER_BOSS_IDLE, AnimationID.HODSUN_SPIDER_BOSS_WALK, -1, AnimationID.HOSDUN_SPIDER_BOSS_DEATH, -1),
	ARTIO("artio", "Artio", NpcID.CALLISTO_SINGLES, -1, 450, Skill.HUNTER, 62,
		AnimationID.NPC_CALLISTO_IDLE01, AnimationID.NPC_CALLISTO_WALK01, -1, AnimationID.NPC_CALLISTO_DEATH01, AnimationID.NPC_CALLISTO_SPAWN01),
	CERBERUS("cerberus", "Cerberus", NpcID.CERBERUS_ATTACKING, -1, 600, Skill.PRAYER, 64,
		AnimationID.CERBERUS_IDLE, AnimationID.CERBERUS_WALK, AnimationID.CERBERUS_DEFEND, AnimationID.CERBERUS_DEATH, -1),
	SKOTIZO("skotizo", "Skotizo", NpcID.CATA_BOSS, -1, 450, Skill.PRAYER, 66,
		AnimationID.DEMON_UPDATE_READY, AnimationID.DEMON_UPDATE_WALK, AnimationID.DEMON_UPDATE_DEFEND, AnimationID.DEMON_UPDATE_DEATH, AnimationID.DEMON_UPDATE_APPEAR),
	GROTESQUE_GUARDIANS("grotesque_guardians", "Grotesque Guardians", NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING, -1, 900, Skill.MINING, 71,
		AnimationID.GG_DUSK_IDLE, AnimationID.GG_DUSK_WALK, AnimationID.GG_DUSK_DEFEND, AnimationID.GG_DUSK_DEATH, AnimationID.GG_DUSK_SPAWN_JUMP),
	KALPHITE_QUEEN("kalphite_queen", "Kalphite Queen", NpcID.KALPHITE_QUEEN, -1, 510, Skill.HUNTER, 74,
		AnimationID.KALPHITE_UPDATE_QUEEN_READY, AnimationID.KALPHITE_UPDATE_QUEEN_WALK, -1, AnimationID.KALPHITE_UPDATE_QUEEN_DEATH, -1),
	ROYAL_TITANS("royal_titans", "Royal Titans", NpcID.RT_FIRE_QUEEN, -1, 1200, Skill.FIREMAKING, 75,
		AnimationID.FIRE_GIANT_BRANDA_QUEEN_IDLE01, AnimationID.FIRE_GIANT_BRANDA_QUEEN_WALK01, -1, AnimationID.FIRE_GIANT_BRANDA_QUEEN_DEATH01, AnimationID.FIRE_GIANT_BRANDA_QUEEN_SPAWN01),
	TZTOK_JAD("tztok_jad", "TzTok-Jad", NpcID.TZHAAR_FIGHTCAVE_SWARM_BOSS, -1, 11770, Skill.RANGED, 79,
		AnimationID.LORDMAGMUS_READY, AnimationID.LORDMAGMUS_WALK, AnimationID.LORDMAGMUS_DEFEND, AnimationID.LORDMAGMUS_DEATH, -1),
	ABYSSAL_SIRE("abyssal_sire", "Abyssal Sire", NpcID.ABYSSALSIRE_SIRE_WANDERING, -1, 425, Skill.RUNECRAFT, 82,
		AnimationID.SIRE_IDLE_TWO, AnimationID.SIRE_WALK, -1, AnimationID.SIRE_DEATH, -1),
	ALCHEMICAL_HYDRA("alchemical_hydra", "Alchemical Hydra", NpcID.HYDRABOSS_2, -1, 1100, Skill.HERBLORE, 84,
		AnimationID.HYDRA_STAGE_4_READY, AnimationID.HYDRA_STAGE_4_WALK, -1, AnimationID.HYDRA_STAGE_4_DEATH, AnimationID.HYDRA_STAGE_4_SPAWN),
	GAUNTLET("gauntlet", "The Gauntlet", NpcID.CRYSTAL_HUNLLEF_MELEE, -1, 600, Skill.COOKING, 86,
		AnimationID.HUNLLEF_READY, AnimationID.HUNLLEF_WALK, -1, AnimationID.HUNLLEF_DEATH_PART_A, AnimationID.HUNLLEF_SPAWN),
	VETION("vetion", "Vet'ion", NpcID.VETION, -1, 630, Skill.PRAYER, 88,
		AnimationID.NPC_VETION_IDLE_01, AnimationID.NPC_VETION_WALK_01, AnimationID.NPC_VETION_DEFEND_SHIELD_01, AnimationID.NPC_VETION_DEATH_01, AnimationID.NPC_VETION_SPAWN_01),
	VENENATIS("venenatis", "Venenatis", NpcID.VENENATIS, -1, 850, Skill.CRAFTING, 90,
		AnimationID.NPC_VENENATIS_IDLE_01, AnimationID.NPC_VENENATIS_RUN_01, -1, AnimationID.NPC_VENENATIS_DEATH_01, AnimationID.NPC_VENENATIS_SPAWN_01),
	CALLISTO("callisto", "Callisto", NpcID.CALLISTO, -1, 1000, Skill.HUNTER, 92,
		AnimationID.NPC_CALLISTO_IDLE01, AnimationID.NPC_CALLISTO_WALK01, -1, AnimationID.NPC_CALLISTO_DEATH01, AnimationID.NPC_CALLISTO_SPAWN01),
	KREEARRA("kreearra", "Kree'arra", NpcID.GODWARS_ARMADYL_AVATAR, -1, 255, Skill.FLETCHING, 93,
		AnimationID.GODWARS_ARMADYL_AVATAR_READY, AnimationID.GODWARS_ARMADYL_AVATAR_WALK, AnimationID.GODWARS_ARMADYL_AVATAR_DEFEND, AnimationID.GODWARS_ARMADYL_AVATAR_DEATH, -1),
	MAD_ANGEL("mad_angel", "The Mad Angel", NpcID.MAD_ANGEL, -1, 755, Skill.AGILITY, 96,
		AnimationID.NPC_MAD_ANGEL_IDLE, AnimationID.NPC_MAD_ANGEL_IDLE, -1, AnimationID.NPC_MAD_ANGEL_ATTACK_DEATH, AnimationID.NPC_MAD_ANGEL_SPAWN),
	COMMANDER_ZILYANA("commander_zilyana", "Commander Zilyana", NpcID.GODWARS_SARADOMIN_AVATAR, -1, 255, Skill.MAGIC, 98,
		AnimationID.GODWARS_SARADOMIN_READY, AnimationID.GODWARS_SARADOMIN_WALK, AnimationID.GODWARS_SARADOMIN_DEFEND, AnimationID.GODWARS_SARADOMIN_DEATH, -1),
	GENERAL_GRAARDOR("general_graardor", "General Graardor", NpcID.GODWARS_BANDOS_AVATAR, -1, 255, Skill.STRENGTH, 103,
		AnimationID.GODWARS_BANDOS_READY, AnimationID.GODWARS_BANDOS_WALK, AnimationID.GODWARS_BANDOS_DEFEND, AnimationID.GODWARS_BANDOS_DEATH, -1),
	HUEYCOATL("hueycoatl", "The Hueycoatl", NpcID.HUEY_HEAD, -1, 4050, Skill.CRAFTING, 104,
		AnimationID.HUEY_IDLE, AnimationID.HUEY_IDLE, -1, AnimationID.HUEY_KNOCKOUT, AnimationID.HUEY_AWAKEN),
	KRIL_TSUTSAROTH("kril_tsutsaroth", "K'ril Tsutsaroth", NpcID.GODWARS_ZAMORAK_AVATAR, -1, 255, Skill.FIREMAKING, 107,
		AnimationID.GODWARS_ZAMORAK_READY, AnimationID.GODWARS_ZAMORAK_WALK, AnimationID.GODWARS_ZAMORAK_DEFEND, AnimationID.GODWARS_ZAMORAK_DEATH, AnimationID.DEMON_UPDATE_APPEAR),
	ZULRAH("zulrah", "Zulrah", NpcID.SNAKEBOSS_BOSS_RANGED, -1, 500, Skill.HERBLORE, 111,
		AnimationID.SNAKEBOSS_IDLE, AnimationID.SNAKEBOSS_IDLE, AnimationID.SNAKEBOSS_DEFEND, AnimationID.SNAKEBOSS_DEATH, AnimationID.SNAKEBOSS_EMERGEFAST),
	VORKATH("vorkath", "Vorkath", NpcID.VORKATH, -1, 750, Skill.PRAYER, 116,
		AnimationID.DS2_VORKATH_IDLE, AnimationID.DS2_VORKATH_WALK, AnimationID.DS2_VORKATH_DEFEND_IDLE, AnimationID.DS2_VORKATH_DEATH, AnimationID.DS2_VORKATH_SPAWN),
	CORRUPTED_GAUNTLET("corrupted_gauntlet", "Corrupted Gauntlet", NpcID.CRYSTAL_HUNLLEF_MELEE_HM, -1, 1000, Skill.COOKING, 116,
		AnimationID.HUNLLEF_READY, AnimationID.HUNLLEF_WALK, -1, AnimationID.HUNLLEF_DEATH_PART_A, AnimationID.HUNLLEF_SPAWN),
	TOMBS_OF_AMASCUT("tombs_of_amascut", "Tombs of Amascut", NpcID.TOA_WARDEN_TUMEKEN_PHASE3, -1, 8241, Skill.THIEVING, 121,
		AnimationID.NPC_WARDENS_IDLE05, AnimationID.NPC_WARDENS_IDLE05, -1, AnimationID.NPC_WARDENS_DEATH01, AnimationID.NPC_WARDENS_WAKE01),
	PHANTOM_MUSPAH("phantom_muspah", "Phantom Muspah", NpcID.MUSPAH, -1, 850, Skill.RUNECRAFT, 128,
		AnimationID.NPC_MUSPAH_IDLE_01, AnimationID.NPC_MUSPAH_WALK_FORWARD_01, -1, AnimationID.NPC_MUSPAH_DEATH_01, AnimationID.NPC_MUSPAH_SPAWN_01),
	MAGGOT_KING("maggot_king", "Maggot King", NpcID.MAGGOT_KING, -1, 1500, Skill.FISHING, 134,
		AnimationID.NPC_MAGGOT_KING_IDLE_01, AnimationID.NPC_MAGGOT_KING_WALK_FORWARD_01, -1, AnimationID.NPC_MAGGOT_KING_DEATH_01, -1),
	DUKE_SUCELLUS("duke_sucellus", "Duke Sucellus", NpcID.DUKE_SUCELLUS_AWAKE, -1, 485, Skill.MINING, 136,
		AnimationID.NPC_DUKE_SUCELLUS01_RIG_IDLE_AWAKE_01, AnimationID.NPC_DUKE_SUCELLUS01_RIG_IDLE_AWAKE_01, -1, AnimationID.NPC_DUKE_SUCELLUS01_DEATH_01, AnimationID.NPC_DUKE_SUCELLUS01_WAKE_UP_01),
	CHAMBERS_OF_XERIC("chambers_of_xeric", "Chambers of Xeric", -1, 32523, 4400, Skill.FARMING, 139,
		AnimationID.OLM_HEAD_IDLE_FRONT, AnimationID.OLM_HEAD_IDLE_FRONT, -1, AnimationID.OLM_HEAD_DEATH_FRONT, AnimationID.OLM_HEAD_SPAWN),
	DOOM_OF_MOKHAIOTL("doom_of_mokhaiotl", "Doom of Mokhaiotl", NpcID.DOM_BOSS, -1, 4850, Skill.MINING, 145,
		AnimationID.DOM_IDLE, AnimationID.DOM_IDLE, -1, AnimationID.DOM_DESPAWN, AnimationID.DOM_BURROWED_EMERGE),
	VARDORVIS("vardorvis", "Vardorvis", NpcID.VARDORVIS, -1, 700, Skill.WOODCUTTING, 148,
		AnimationID.NPC_VARDORVIS_01_IDLE_01, AnimationID.NPC_VARDORVIS_01_WALK_FORWARD_01, -1, AnimationID.NPC_VARDORVIS_01_DEATH_01, AnimationID.NPC_VARDORVIS_01_SPAWN_01),
	CORPOREAL_BEAST("corporeal_beast", "Corporeal Beast", NpcID.CORP_BEAST, -1, 2000, Skill.DEFENCE, 152,
		AnimationID.CORPBEAST_IDLE, AnimationID.CORPBEAST_WALKING, AnimationID.CORPBEAST_DEFEND, AnimationID.CORPBEAST_DEATH, -1),
	WHISPERER("whisperer", "The Whisperer", NpcID.WHISPERER, -1, 900, Skill.MAGIC, 153,
		AnimationID.NPC_WHISPERER_01_IDLE_01, AnimationID.NPC_WHISPERER_01_WALK_01, -1, AnimationID.NPC_WHISPERER_01_DEATH_01, -1),
	LEVIATHAN("leviathan", "The Leviathan", NpcID.LEVIATHAN, -1, 900, Skill.FISHING, 157,
		AnimationID.NPC_LEVIATHAN_01_IDLE_01, AnimationID.NPC_LEVIATHAN_01_IDLE_01, -1, AnimationID.NPC_LEVIATHAN_01_DEATH, AnimationID.NPC_LEVIATHAN_01_SPAWN),
	NIGHTMARE("nightmare", "The Nightmare", NpcID.NIGHTMARE_PHASE_01, -1, 7800, Skill.CONSTRUCTION, 159,
		AnimationID.NIGHTMARE_IDLE, AnimationID.NIGHTMARE_WALK, -1, AnimationID.NIGHTMARE_DEATH, AnimationID.NIGHTMARE_RESPAWN),
	ARAXXOR("araxxor", "Araxxor", NpcID.ARAXXOR, -1, 1020, Skill.HERBLORE, 161,
		AnimationID.NPC_ARAXXOR_01_IDLE_01, AnimationID.NPC_ARAXXOR_01_WALK_01, -1, AnimationID.NPC_ARAXXOR_01_DEATH_01, AnimationID.NPC_ARAXXOR_01_SPAWN_01),
	NEX("nex", "Nex", NpcID.NEX, -1, 5400, Skill.DEFENCE, 167,
		AnimationID.NEX_READY, AnimationID.NEX_RUN, AnimationID.NEX_DEFEND, AnimationID.NEX_DEATH, -1),
	THEATRE_OF_BLOOD("theatre_of_blood", "Theatre of Blood", NpcID.VERZIK_PHASE3, -1, 19500, Skill.STRENGTH, 168,
		AnimationID.VERZIK_PHASE3_IDLE, AnimationID.VERZIK_PHASE3_WALK, -1, AnimationID.VERZIK_PHASE3_DEATH_A, -1),
	SOL_HEREDIT("sol_heredit", "Sol Heredit", NpcID.COLOSSEUM_SOL_P1, -1, 12363, Skill.ATTACK, 173,
		AnimationID.NPC_COLOSSI_FINALBOSS_01_IDLE, AnimationID.NPC_COLOSSI_FINALBOSS_01_WALK, -1, AnimationID.NPC_COLOSSI_FINALBOSS_01_DEATH, -1),
	PHOSANIS_NIGHTMARE("phosanis_nightmare", "Phosani's Nightmare", NpcID.NIGHTMARE_CHALLENGE_PHASE_01, -1, 2550, Skill.CONSTRUCTION, 179,
		AnimationID.NIGHTMARE_IDLE, AnimationID.NIGHTMARE_WALK, -1, AnimationID.NIGHTMARE_DEATH, AnimationID.NIGHTMARE_RESPAWN),
	YAMA("yama", "Yama", NpcID.YAMA, -1, 3300, Skill.SMITHING, 182,
		AnimationID.NPC_YAMA01_IDLE01, AnimationID.NPC_YAMA01_WALK01, -1, AnimationID.NPC_YAMA01_DESPAWN01, -1),
	TZKAL_ZUK("tzkal_zuk", "TzKal-Zuk", NpcID.INFERNO_TZKALZUK_PLACEHOLDER, -1, 23510, Skill.MAGIC, 189,
		AnimationID.ZUK_IDLE, AnimationID.ZUK_IDLE, AnimationID.ZUK_DEFEND, AnimationID.ZUK_DEATH, AnimationID.ZUK_SPAWN_NO_ROCK),
	FRACTURED_ARCHIVE("fractured_archive", "The Fractured Archive", NpcID.TFA_OCELLUS_P3, -1, 23510, Skill.RUNECRAFT, 192,
		AnimationID.NPC_OCELLUS_PHASE3_IDLE, AnimationID.NPC_OCELLUS_PHASE3_WALK, -1, AnimationID.NPC_OCELLUS_PHASE3_DEATH, -1);

	private static final int REFERENCE_HITPOINTS = 120;
	// Unlocking a boss takes about this much fighting the one before it, at a typical pace.
	private static final double UNLOCK_FIGHT_SECONDS = 10 * 60;
	private static final int MIN_UNLOCK_KILLS = 5;
	private static final Boss[] LADDER = values();

	// Matches the "id" of the boss's entry in drops.json, and is the key progress is saved under.
	private final String id;
	private final String displayName;
	// -1 for a boss that isn't an NPC, like the Wintertodt storm, which is scenery.
	private final int npcId;
	// The model for a boss that isn't an NPC, or -1. There are no gameval constants for models.
	private final int modelId;
	// The real boss's hitpoints.
	private final int hitpoints;
	// Takes double damage from this skill.
	private final Skill weakness;
	// Collection log slots, across every boss, needed to fight this one. It also needs unlockKills() of the one before.
	private final int unlockSlots;
	private final int idleAnimation;
	private final int walkAnimation;
	private final int defendAnimation;
	private final int deathAnimation;
	private final int spawnAnimation;

	// Big bosses take bigger hits so they still die in minutes: the square root of how much tougher than Obor they
	// are. The Nightmare's 7,800 hitpoints take about 4 minutes of training instead of 32.
	double damageScale()
	{
		return Math.max(1, Math.sqrt(hitpoints / (double) REFERENCE_HITPOINTS));
	}

	// How long a kill takes at a typical pace, without the weakness bonus.
	double typicalKillSeconds()
	{
		return hitpoints / (SkillRates.DAMAGE_PER_HOUR / 3600 * damageScale());
	}

	// The boss before this one on the ladder, or null for the first.
	Boss previous()
	{
		return ordinal() == 0 ? null : LADDER[ordinal() - 1];
	}

	// Kills of the boss before this one needed to fight it, so you meet each boss on the way up: about 10 minutes of
	// fighting it, rounded up to a multiple of 5. None for the bosses you start with.
	int unlockKills()
	{
		Boss previous = previous();
		if (previous == null || unlockSlots == 0)
		{
			return 0;
		}
		int kills = (int) Math.ceil(UNLOCK_FIGHT_SECONDS / previous.typicalKillSeconds() / 5) * 5;
		return Math.max(MIN_UNLOCK_KILLS, kills);
	}

	static Boss byId(String id)
	{
		for (Boss boss : values())
		{
			if (boss.id.equals(id))
			{
				return boss;
			}
		}
		return null;
	}
}
