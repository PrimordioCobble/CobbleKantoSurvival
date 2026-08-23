package net.crulim.cobblekantosurvival.gate;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.fishing.SpawnBait;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.pokemon.Species;
import net.crulim.cobblekantosurvival.CobbleKantoSurvival;
import net.crulim.cobblekantosurvival.config.ConfigManager;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps Poké Snacks useful without allowing high rarity-tier seasonings to turn
 * them into a Legendary/Mythical shortcut.
 *
 * Cobblemon 1.7.3 gives every Poké Snack its normal bucket multipliers and, when
 * a rarity seasoning is present, adds BucketNormalizingInfluence before those
 * multipliers. This listener runs at the official PRE event, after Cobblemon has
 * selected a SpawnAction but before it is completed.
 *
 * For Legendary/Mythical selections only, rarity tiers above the configured cap
 * are probabilistically neutralized back to the configured cap. The normal
 * Poké Snack behavior, typing/egg-group/EV targeting, shiny/HA effects, bite
 * timing, and every non-Legendary/non-Mythical spawn remain untouched.
 */
public final class PokeSnackLegendaryBalancer {
    // Cobblemon 1.7.3 data/cobblemon/spawning/best-spawner-config.json.
    private static final double COMMON_WEIGHT = 94.3D;
    private static final double UNCOMMON_WEIGHT = 5.0D;
    private static final double RARE_WEIGHT = 0.5D;
    private static final double ULTRA_RARE_WEIGHT = 0.2D;

    // Cobblemon 1.7.3 PokeSnackBlockEntity native bucket multipliers.
    private static final double COMMON_MULTIPLIER = 1.0D;
    private static final double UNCOMMON_MULTIPLIER = 2.25D;
    private static final double RARE_MULTIPLIER = 5.5D;
    private static final double ULTRA_RARE_MULTIPLIER = 5.5D;

    // Cobblemon 1.7.3 rarity BucketNormalizingInfluence used by Poké Snacks.
    private static final double NORMALIZATION_FIRST_TIER = 1.2D;
    private static final double NORMALIZATION_GRADIENT = 0.2D;

    private PokeSnackLegendaryBalancer() {}

    public static void register() {
        CobblemonEvents.POKE_SNACK_SPAWN_POKEMON_PRE.subscribe(event -> {
            var cfg = ConfigManager.get();
            if (!cfg.enabled || !cfg.pokeSnacks.balanceLegendaryAndMythicalSpawns) return;

            if (!(event.getSpawnAction().getDetail() instanceof PokemonSpawnDetail detail)) return;

            Species species = resolveSpecies(detail.getPokemon());
            if (species == null || !isLegendaryOrMythical(species)) return;

            int actualRarityTier = rarityTier(event.getPokeSnackBlockEntity().getBaitEffects());
            int cappedRarityTier = Math.min(actualRarityTier, cfg.pokeSnacks.maxLegendaryMythicalRarityTier);

            // Tier is already inside the allowed range: preserve Cobblemon exactly.
            if (actualRarityTier <= cappedRarityTier) return;

            String bucketName = event.getSpawnAction().getBucket().getName();
            double actualBucketChance = bucketChance(bucketName, actualRarityTier);
            double cappedBucketChance = bucketChance(bucketName, cappedRarityTier);

            // Unknown/custom buckets are deliberately left alone rather than guessed.
            if (actualBucketChance <= 0.0D || cappedBucketChance <= 0.0D) return;

            double acceptance = Math.min(1.0D, cappedBucketChance / actualBucketChance);
            if (ThreadLocalRandom.current().nextDouble() < acceptance) return;

            event.cancel();

            if (cfg.debugLogging) {
                CobbleKantoSurvival.LOGGER.info(
                    "[CKS] PokéSnack Legendary/Mythical rarity tier capped: species={}, bucket={}, actualTier={}, capTier={}, actualBucketChance={}%, capBucketChance={}%, acceptance={}%",
                    species.getName(),
                    bucketName,
                    actualRarityTier,
                    cappedRarityTier,
                    String.format(Locale.ROOT, "%.3f", actualBucketChance * 100.0D),
                    String.format(Locale.ROOT, "%.3f", cappedBucketChance * 100.0D),
                    String.format(Locale.ROOT, "%.2f", acceptance * 100.0D)
                );
            }
        });
    }

    private static int rarityTier(java.util.List<SpawnBait.Effect> effects) {
        double rawTier = effects.stream()
            .filter(effect -> SpawnBait.Effects.INSTANCE.getRARITY_BUCKET().equals(effect.getType()))
            .mapToDouble(SpawnBait.Effect::getValue)
            .sum();

        if (!Double.isFinite(rawTier) || rawTier <= 0.0D) return 0;
        if (rawTier >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) rawTier;
    }

    /**
     * Reproduces the Cobblemon 1.7.3 Poké Snack bucket math for one named bucket.
     * The intermediate normalize-to-100 operation can be omitted because it
     * multiplies every bucket by the same scalar, which cancels in the final ratio.
     */
    public static double bucketChance(String bucketName, int rarityTier) {
        double common = COMMON_WEIGHT;
        double uncommon = UNCOMMON_WEIGHT;
        double rare = RARE_WEIGHT;
        double ultraRare = ULTRA_RARE_WEIGHT;

        if (rarityTier > 0) {
            double normalizationFactor = NORMALIZATION_FIRST_TIER
                + (NORMALIZATION_GRADIENT * (rarityTier - 1.0D));
            double exponent = 1.0D / normalizationFactor;

            common = Math.pow(common, exponent);
            uncommon = Math.pow(uncommon, exponent);
            rare = Math.pow(rare, exponent);
            ultraRare = Math.pow(ultraRare, exponent);
        }

        common *= COMMON_MULTIPLIER;
        uncommon *= UNCOMMON_MULTIPLIER;
        rare *= RARE_MULTIPLIER;
        ultraRare *= ULTRA_RARE_MULTIPLIER;

        double total = common + uncommon + rare + ultraRare;
        if (total <= 0.0D) return 0.0D;

        return switch (bucketName) {
            case "common" -> common / total;
            case "uncommon" -> uncommon / total;
            case "rare" -> rare / total;
            case "ultra-rare" -> ultraRare / total;
            default -> 0.0D;
        };
    }

    private static Species resolveSpecies(PokemonProperties properties) {
        if (properties == null || properties.getSpecies() == null || properties.getSpecies().isBlank()) {
            return null;
        }

        String speciesName = properties.getSpecies();
        Species species = PokemonSpecies.getByName(speciesName);
        if (species == null && speciesName.contains(":")) {
            species = PokemonSpecies.getByName(speciesName.substring(speciesName.indexOf(':') + 1));
        }
        return species;
    }

    private static boolean isLegendaryOrMythical(Species species) {
        return species.getLabels().stream().anyMatch(label ->
            "legendary".equalsIgnoreCase(label) || "mythical".equalsIgnoreCase(label)
        );
    }
}
