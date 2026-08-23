package net.crulim.cobblekantosurvival.generation;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GenerationPolicy {
    private static final Set<UUID> TEMPORARY_GENERATION_ONE_BYPASS = ConcurrentHashMap.newKeySet();

    private GenerationPolicy() {}

    /**
     * Temporary, player-specific Generation-I acquisition bypass.
     *
     * This is deliberately narrower than the administrative OP+Creative bypass:
     * it only affects player-aware acquisition gates whose resolved result is
     * Generation I. Gen III+ content remains locked and world spawn filtering is
     * never changed by this toggle.
     */
    public static boolean temporaryGenerationOneBypass(ServerPlayer player) {
        return player != null && TEMPORARY_GENERATION_ONE_BYPASS.contains(player.getUUID());
    }

    public static boolean toggleTemporaryGenerationOneBypass(ServerPlayer player) {
        if (player == null) return false;
        UUID id = player.getUUID();
        if (TEMPORARY_GENERATION_ONE_BYPASS.remove(id)) return false;
        TEMPORARY_GENERATION_ONE_BYPASS.add(id);
        return true;
    }

    public static void clearTemporaryGenerationOneBypass(ServerPlayer player) {
        if (player != null) TEMPORARY_GENERATION_ONE_BYPASS.remove(player.getUUID());
    }

    public static int temporaryGenerationOneBypassCount() {
        return TEMPORARY_GENERATION_ONE_BYPASS.size();
    }

    private static boolean temporaryGenerationOneAllows(ServerPlayer player, int requiredGeneration) {
        return requiredGeneration == 1 && temporaryGenerationOneBypass(player);
    }

    /**
     * Administrative gameplay bypass.
     *
     * By default the player must satisfy BOTH conditions:
     * - configured operator permission level;
     * - Creative mode.
     *
     * This lets server administrators remain OP while playing Survival without
     * accidentally bypassing Generation Gate mechanics. Administrative CKS
     * commands still use their own permission checks and do not require
     * Creative mode.
     */
    public static boolean bypass(ServerPlayer player) {
        if (player == null) return false;

        ServerConfig cfg = ConfigManager.get();
        if (!player.hasPermissions(cfg.adminBypassPermissionLevel)) return false;

        return !cfg.adminBypassRequiresCreative || player.isCreative();
    }

    public static boolean worldAllows(Pokemon pokemon) {
        int required = ConfigManager.get().naturalSpawns.gateRegionalFormsByDebutGeneration
            ? PokemonGenerationResolver.requiredGeneration(pokemon)
            : (pokemon == null ? 99 : FormGenerationResolver.baseGeneration(pokemon.getSpecies()));
        return exceptionAllows(PokemonGenerationResolver.speciesKey(pokemon), PokemonGenerationResolver.formKey(pokemon))
            || generationAllowed(required, true);
    }

    public static boolean worldAllows(PokemonProperties properties) {
        int required = ConfigManager.get().naturalSpawns.gateRegionalFormsByDebutGeneration
            ? PokemonGenerationResolver.requiredGeneration(properties)
            : PokemonGenerationResolver.baseGeneration(properties);
        return exceptionAllows(PokemonGenerationResolver.speciesKey(properties), PokemonGenerationResolver.formKey(properties))
            || generationAllowed(required, true);
    }

    public static boolean worldAllowsSpec(String spec) {
        try {
            PokemonProperties p = PokemonProperties.Companion.parse(spec);
            return worldAllows(p);
        } catch (Throwable ignored) {
            int gen = PokemonGenerationResolver.requiredGenerationFromSpec(spec);
            return generationAllowed(gen, true);
        }
    }

    public static boolean acquisitionAllows(Pokemon pokemon, ServerPlayer actor) {
        int requiredGeneration = PokemonGenerationResolver.requiredGeneration(pokemon);
        return bypass(actor)
            || temporaryGenerationOneAllows(actor, requiredGeneration)
            || exceptionAllows(PokemonGenerationResolver.speciesKey(pokemon), PokemonGenerationResolver.formKey(pokemon))
            || generationAllowed(requiredGeneration, true);
    }

    public static boolean acquisitionAllows(PokemonProperties properties, ServerPlayer actor) {
        int requiredGeneration = PokemonGenerationResolver.requiredGeneration(properties);
        return bypass(actor)
            || temporaryGenerationOneAllows(actor, requiredGeneration)
            || exceptionAllows(PokemonGenerationResolver.speciesKey(properties), PokemonGenerationResolver.formKey(properties))
            || generationAllowed(requiredGeneration, true);
    }

    public static boolean generationAllowed(int requiredGeneration, boolean blockGenerationOne) {
        ServerConfig cfg = ConfigManager.get();
        if (!cfg.enabled) return true;
        if (requiredGeneration < 1 || requiredGeneration > 9) return false;
        if (requiredGeneration == 1) {
            if (!blockGenerationOne) return true;
            return cfg.naturalSpawns.allowGenerationOne;
        }
        if (!cfg.naturalSpawns.cumulativeFromGenerationTwo) return requiredGeneration == cfg.currentGeneration;
        return requiredGeneration <= cfg.currentGeneration;
    }

    public static boolean sourceException(String source) {
        if (source == null) return false;
        String n = source.toLowerCase(Locale.ROOT);
        return ConfigManager.get().sourceExceptions.stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(n::equals);
    }

    private static boolean exceptionAllows(String species, String form) {
        ServerConfig cfg = ConfigManager.get();
        String s = PokemonGenerationResolver.normalize(species);
        String f = PokemonGenerationResolver.normalize(form);
        boolean speciesMatch = cfg.speciesExceptions.stream().map(PokemonGenerationResolver::normalize).anyMatch(s::equals);
        if (speciesMatch) return true;
        return cfg.formExceptions.stream().map(x -> x.toLowerCase(Locale.ROOT).trim()).anyMatch(x -> x.equalsIgnoreCase(form) || PokemonGenerationResolver.normalize(x).equals(f));
    }
}
