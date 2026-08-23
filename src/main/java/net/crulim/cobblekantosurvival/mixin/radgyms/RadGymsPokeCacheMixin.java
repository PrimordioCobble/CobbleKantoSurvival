package net.crulim.cobblekantosurvival.mixin.radgyms;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.reactive.EventObservable;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import lol.gito.radgyms.common.api.event.GymEvents;
import lol.gito.radgyms.common.cache.CacheDTO;
import lol.gito.radgyms.common.extension.CobblemonExtensionsKt;
import lol.gito.radgyms.common.registry.RadGymsSpeciesRegistry;
import net.crulim.cobblekantosurvival.CobbleKantoSurvival;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rad Gyms 0.4.4 Poké Caches bypass the normal gym PokemonReward path:
 * PokeCache rolls a Pokemon, emits CACHE_ROLL_POKE, and Rad Gyms' LOWEST
 * listener inserts event.poke directly into the player's party.
 *
 * Gate exactly at that event emission. A blocked roll is replaced from the
 * SAME cache type + rarity using the datapack's original weights. If no legal
 * candidate exists, the event is not emitted; Rad Gyms therefore neither
 * grants a Pokemon nor consumes the cache item.
 *
 * There is no persistent pool mutation and no retry-until-success loop.
 */
@Pseudo
@Mixin(targets = "lol.gito.radgyms.common.item.PokeCache", remap = false)
public abstract class RadGymsPokeCacheMixin {
    /**
     * National Dex IDs officially classified as Legendary or Mythical through Gen IX.
     *
     * This is intentionally cache-specific: it does not change the global generation
     * policy or other intended legendary acquisition paths. Pseudo-legendaries,
     * Ultra Beasts and Paradox Pokemon are not included by category alone.
     */
    private static final Set<Integer> CKS_CACHE_BLOCKED_LEGENDARY_MYTHICAL_DEX = Set.of(
        // Gen I
        144, 145, 146, 150, 151,
        // Gen II
        243, 244, 245, 249, 250, 251,
        // Gen III
        377, 378, 379, 380, 381, 382, 383, 384, 385, 386,
        // Gen IV
        480, 481, 482, 483, 484, 485, 486, 487, 488, 489, 490, 491, 492, 493,
        // Gen V
        494, 638, 639, 640, 641, 642, 643, 644, 645, 646, 647, 648, 649,
        // Gen VI
        716, 717, 718, 719, 720, 721,
        // Gen VII
        772, 773, 785, 786, 787, 788, 789, 790, 791, 792, 800, 801, 802, 807, 808, 809,
        // Gen VIII
        888, 889, 890, 891, 892, 893, 894, 895, 896, 897, 898, 905,
        // Gen IX
        1001, 1002, 1003, 1004, 1007, 1008, 1014, 1015, 1016, 1017, 1024, 1025
    );
    @Redirect(
        method = {"use", "method_7836"},
        at = @At(
            value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/reactive/EventObservable;emit([Ljava/lang/Object;)V",
            remap = false
        ),
        // Rad Gyms is exact-version gated by CobbleKantoSurvivalMixinPlugin.
        // Keep this optional integration fail-soft rather than crashing startup
        // if another transformer changes this single invocation unexpectedly.
        require = 0,
        remap = false
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void cks$gatePokeCacheRoll(EventObservable observable, Object[] events) {
        if (events == null || events.length != 1 || !(events[0] instanceof GymEvents.CacheRollPokeEvent event)) {
            observable.emit(events);
            return;
        }

        var cfg = ConfigManager.get();
        ServerPlayer player = event.getPlayer();
        if (!cfg.enabled || !cfg.acquisition.blockRadGymsCachePokemonRewards) {
            observable.emit(events);
            return;
        }

        // Legendary/Mythical exclusion is stronger than a generation bypass for
        // Poké Caches: even OP+Creative cannot roll one while this cache gate is on.
        boolean generationBypassed = GenerationPolicy.bypass(player)
            || GenerationPolicy.sourceException("rad_gyms_cache");

        if (!cks$isLegendaryOrMythical(event.getPoke())
            && (generationBypassed || GenerationPolicy.acquisitionAllows(event.getPoke(), player))) {
            observable.emit(events);
            return;
        }

        try {
            Pokemon replacement = cks$rollAllowedReplacement(event, player, generationBypassed);
            if (replacement != null) {
                event.setPoke(replacement);
                observable.emit(events);
                return;
            }

            // CacheRollPokeHandler is a subscriber to this event and is where
            // Rad Gyms grants the Pokemon, consumes the cache, and awards its stat.
            // Suppressing emission therefore leaves the item intact.
            player.sendSystemMessage(Component.literal(cfg.messages.gymCacheLocked));
        } catch (Throwable t) {
            // Fail closed for progression, but do not crash a player action/server.
            CobbleKantoSurvival.LOGGER.error(
                "Rad Gyms Poké Cache generation gate failed for player {}; cache was left unconsumed",
                player.getGameProfile().getName(),
                t
            );
            player.sendSystemMessage(Component.literal(cfg.messages.gymCacheLocked));
        }
    }

    private static Pokemon cks$rollAllowedReplacement(GymEvents.CacheRollPokeEvent event, ServerPlayer player, boolean generationBypassed) {
        CacheDTO cache = RadGymsSpeciesRegistry.INSTANCE.getSpeciesByRarity().get(event.getType());
        if (cache == null) return null;

        String rarity = event.getRarity().getSerializedName().toLowerCase(Locale.ROOT);
        Map<String, Integer> configuredPool = cache.getPools().get(rarity);
        if (configuredPool == null || configuredPool.isEmpty()) return null;

        // LinkedHashMap keeps datapack iteration deterministic while we remove
        // malformed/unimplemented candidates. Weighting remains exactly relative
        // to the candidates that are legal for the current generation.
        Map<String, Integer> allowed = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : configuredPool.entrySet()) {
            String spec = entry.getKey();
            Integer weight = entry.getValue();
            if (spec == null || spec.isBlank() || weight == null || weight <= 0) continue;

            try {
                PokemonProperties properties = PokemonProperties.Companion.parse(spec);
                if (!cks$isLegendaryOrMythical(properties)
                    && (generationBypassed || GenerationPolicy.acquisitionAllows(properties, player))) {
                    allowed.put(spec, weight);
                }
            } catch (Throwable ignored) {
                // Rad Gyms' own CacheHandler would fail on malformed properties;
                // for the gate, simply do not consider that entry a safe fallback.
            }
        }

        while (!allowed.isEmpty()) {
            String selected = cks$weightedPick(allowed);
            if (selected == null) return null;

            try {
                Pokemon replacement = PokemonProperties.Companion.parse(selected).create();
                if (!replacement.getSpecies().getImplemented()) {
                    allowed.remove(selected);
                    continue;
                }

                // Mirror CacheHandler#getPoke after PokemonProperties#create().
                CobblemonExtensionsKt.shinyRoll(replacement, player, event.getShinyBoost());
                replacement.updateAspects();
                replacement.updateForm();
                return replacement.initialize();
            } catch (Throwable ignored) {
                // Bounded: every failed candidate is removed once, so there is no
                // retry loop that can spin forever like the old Trials integration.
                allowed.remove(selected);
            }
        }

        return null;
    }

    private static boolean cks$isLegendaryOrMythical(Pokemon pokemon) {
        if (pokemon == null || pokemon.getSpecies() == null) return false;
        return CKS_CACHE_BLOCKED_LEGENDARY_MYTHICAL_DEX.contains(
            pokemon.getSpecies().getNationalPokedexNumber()
        );
    }

    private static boolean cks$isLegendaryOrMythical(PokemonProperties properties) {
        if (properties == null || properties.getSpecies() == null || properties.getSpecies().isBlank()) {
            return false;
        }

        Species species = PokemonSpecies.getByName(properties.getSpecies());
        if (species == null && properties.getSpecies().contains(":")) {
            species = PokemonSpecies.getByName(
                properties.getSpecies().substring(properties.getSpecies().indexOf(':') + 1)
            );
        }

        return species != null
            && CKS_CACHE_BLOCKED_LEGENDARY_MYTHICAL_DEX.contains(species.getNationalPokedexNumber());
    }

    private static String cks$weightedPick(Map<String, Integer> pool) {
        long total = 0L;
        for (Integer weight : pool.values()) {
            if (weight != null && weight > 0) total += weight;
        }
        if (total <= 0L || total > Integer.MAX_VALUE) return null;

        int roll = ThreadLocalRandom.current().nextInt((int) total);
        for (Map.Entry<String, Integer> entry : pool.entrySet()) {
            int weight = Math.max(0, entry.getValue());
            if (roll < weight) return entry.getKey();
            roll -= weight;
        }
        return null;
    }
}
