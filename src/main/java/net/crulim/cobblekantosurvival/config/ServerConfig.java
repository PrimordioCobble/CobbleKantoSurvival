package net.crulim.cobblekantosurvival.config;

import java.util.ArrayList;
import java.util.List;

public final class ServerConfig {
    public boolean enabled = true;
    public int currentGeneration = 2;
    public NaturalSpawns naturalSpawns = new NaturalSpawns();
    public Acquisition acquisition = new Acquisition();
    public TmTrGate tmTrGate = new TmTrGate();
    public Gimmicks gimmicks = new Gimmicks();
    public Services services = new Services();
    public PokeSnacks pokeSnacks = new PokeSnacks();
    public Performance performance = new Performance();
    public int adminBypassPermissionLevel = 4;
    /**
     * When true, gameplay gate bypass requires both the configured permission
     * level and Creative mode. OP players in Survival remain subject to all
     * Generation Gate mechanics.
     */
    public boolean adminBypassRequiresCreative = true;
    public List<String> speciesExceptions = new ArrayList<>();
    public List<String> formExceptions = new ArrayList<>();
    public List<String> sourceExceptions = new ArrayList<>();
    public Messages messages = new Messages();
    public boolean debugLogging = false;

    public static final class NaturalSpawns {
        public boolean enabled = true;
        public boolean allowGenerationOne = false;
        public boolean cumulativeFromGenerationTwo = true;
        public boolean gateRegionalFormsByDebutGeneration = true;
        public boolean blockLockedSourceSpawns = true;
    }

    public static final class Acquisition {
        public boolean blockLockedCaptures = true;
        public boolean blockLockedEvolutions = true;
        public boolean blockLockedEggResults = true;
        public boolean blockLockedRaidRewards = true;
        public boolean blockLockedStarters = true;
        public boolean blockLockedFossilRevival = true;
        public boolean blockKantoNpcPokemonRewards = true;
        public boolean blockKantoNpcPokemonShopRewards = true;
        public boolean blockKantoNpcPokemonTrades = true;
        public boolean blockLockedCobbleSafariIncubatorResults = true;
        public boolean blockCobblemonQuestsGivePokemonRewards = true;
        public boolean blockRadGymsPokemonRewards = true;
        public boolean blockRadGymsCachePokemonRewards = true;
        public boolean allowExistingOwnedLockedPokemon = true;
        public boolean allowPlayerTrades = true;
        public boolean allowExternalTransfers = true;
    }

    public static final class TmTrGate {
        public boolean enabled = true;
        public boolean gateByMoveDebutGeneration = true;
        public boolean allowNaturalLevelUpMovesFromAnyGeneration = true;
        public String unknownMovePolicy = "ALLOW_AND_LOG";
    }

    public static final class Gimmicks {
        public int megaUnlockGeneration = 6;
        public int zMovesUnlockGeneration = 7;
        public int ultraBurstUnlockGeneration = 7;
        public int dynamaxUnlockGeneration = 8;
        public int terastallizationUnlockGeneration = 9;
    }

    public static final class Services {
        public boolean disableCobbleSafariGts = true;
        public boolean disableCobbleSafariWonderTrade = true;
    }

    /**
     * Poké Snack balancing for special species. Normal Pokémon and non-rarity
     * seasoning effects are intentionally left untouched.
     */
    public static final class PokeSnacks {
        /** Enable the Legendary/Mythical rarity-tier cap. */
        public boolean balanceLegendaryAndMythicalSpawns = true;
        /**
         * Highest rarity seasoning tier that Legendary/Mythical Poké Snack
         * spawns may benefit from. Tier 1 is the default middle ground:
         * ordinary rarity seasoning still helps, while high stacked/Tier-10
         * recipes cannot turn ultra-rare into a special-species farm.
         * Set to 0 to remove rarity-seasoning benefit for these species only.
         */
        public int maxLegendaryMythicalRarityTier = 1;
    }

    /**
     * Server performance controls and low-overhead save diagnostics.
     * These settings do not change manual saves, shutdown saves, or saves requested by backup mods.
     */
    public static final class Performance {
        /** Override only Minecraft's periodic autosave interval. */
        public boolean overrideAutosaveInterval = true;
        /** Periodic autosave interval in real-time minutes at the normal 20 TPS. */
        public int autosaveIntervalMinutes = 15;
        /**
         * Keep Fabric Waystones state dirty but defer its global PersistentStateManager.save()
         * to the next normal server save. This avoids flushing every dirty SavedData when a
         * waystone is added/removed/renamed/recolored.
         */
        public boolean deferFabricWaystonesImmediateSave = true;
        /** Log one compact timing summary for each full server save. */
        public boolean saveDiagnostics = true;
        /** Saves at or above this duration are logged as warnings. */
        public int slowSaveWarningMs = 200;
        /** Number of slowest dirty SavedData files/classes included in each save summary. */
        public int savedDataTopEntries = 5;
    }

    public static final class Messages {
        public String captureLocked = "Este Pokémon ainda não está liberado no Survival (requer Geração %d).";
        public String evolutionLocked = "Esta evolução ainda não está liberada no Survival (requer Geração %d).";
        public String eggLocked = "Este ovo resultaria em conteúdo ainda não liberado (requer Geração %d).";
        public String tmLocked = "Este golpe por TM/TR ainda não está liberado (requer Geração %d).";
        public String raidRewardLocked = "A recompensa Pokémon desta raid ainda não está liberada (requer Geração %d).";
        public String starterLocked = "Este inicial ainda não está liberado no Survival (requer Geração %d).";
        public String fossilLocked = "Este fóssil resultaria em um Pokémon ainda não liberado (requer Geração %d).";
        public String npcRewardLocked = "Este Pokémon de recompensa ainda não está liberado (requer Geração %d).";
        public String serviceDisabled = "Este serviço está temporariamente indisponível no CobbleKanto Survival.";
        public String incubatorLocked = "O resultado deste incubador ainda pode gerar conteúdo bloqueado nesta fase do Survival.";
        public String questRewardLocked = "Esta recompensa Pokémon de quest ainda não está liberada (requer Geração %d).";
        public String gymRewardLocked = "Esta recompensa Pokémon de ginásio ainda não está liberada (requer Geração %d).";
        public String gymCacheLocked = "Este Poké Cache não possui nenhum Pokémon liberado neste pool para a geração atual.";
        public String sourceSpawnLocked = "Este encontro ainda não está liberado no Survival (requer Geração %d).";
    }

    public void validate() {
        if (naturalSpawns == null) naturalSpawns = new NaturalSpawns();
        if (acquisition == null) acquisition = new Acquisition();
        if (tmTrGate == null) tmTrGate = new TmTrGate();
        if (gimmicks == null) gimmicks = new Gimmicks();
        if (services == null) services = new Services();
        if (pokeSnacks == null) pokeSnacks = new PokeSnacks();
        if (performance == null) performance = new Performance();
        if (currentGeneration < 2) currentGeneration = 2;
        if (currentGeneration > 9) currentGeneration = 9;
        adminBypassPermissionLevel = Math.max(0, Math.min(4, adminBypassPermissionLevel));
        pokeSnacks.maxLegendaryMythicalRarityTier = Math.max(0, Math.min(30, pokeSnacks.maxLegendaryMythicalRarityTier));
        performance.autosaveIntervalMinutes = Math.max(5, Math.min(120, performance.autosaveIntervalMinutes));
        performance.slowSaveWarningMs = Math.max(50, Math.min(60_000, performance.slowSaveWarningMs));
        performance.savedDataTopEntries = Math.max(1, Math.min(10, performance.savedDataTopEntries));
        gimmicks.megaUnlockGeneration = clampGen(gimmicks.megaUnlockGeneration);
        gimmicks.zMovesUnlockGeneration = clampGen(gimmicks.zMovesUnlockGeneration);
        gimmicks.ultraBurstUnlockGeneration = clampGen(gimmicks.ultraBurstUnlockGeneration);
        gimmicks.dynamaxUnlockGeneration = clampGen(gimmicks.dynamaxUnlockGeneration);
        gimmicks.terastallizationUnlockGeneration = clampGen(gimmicks.terastallizationUnlockGeneration);
        if (tmTrGate.unknownMovePolicy == null) tmTrGate.unknownMovePolicy = "ALLOW_AND_LOG";
        tmTrGate.unknownMovePolicy = tmTrGate.unknownMovePolicy.trim().toUpperCase();
        if (!tmTrGate.unknownMovePolicy.equals("ALLOW_AND_LOG") && !tmTrGate.unknownMovePolicy.equals("BLOCK_AND_LOG")) {
            tmTrGate.unknownMovePolicy = "ALLOW_AND_LOG";
        }
        if (speciesExceptions == null) speciesExceptions = new ArrayList<>();
        if (formExceptions == null) formExceptions = new ArrayList<>();
        if (sourceExceptions == null) sourceExceptions = new ArrayList<>();
        if (messages == null) messages = new Messages();
    }

    private static int clampGen(int value) { return Math.max(2, Math.min(9, value)); }
}
