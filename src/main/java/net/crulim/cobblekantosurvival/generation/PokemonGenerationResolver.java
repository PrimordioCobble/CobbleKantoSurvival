package net.crulim.cobblekantosurvival.generation;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PokemonGenerationResolver {
    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();

    private PokemonGenerationResolver() {}

    public static int requiredGeneration(Pokemon pokemon) {
        if (pokemon == null) return 99;
        Species species = pokemon.getSpecies();
        FormData form = pokemon.getForm();
        return FormGenerationResolver.requiredGeneration(species, form);
    }

    public static int requiredGeneration(PokemonProperties properties) {
        if (properties == null || properties.getSpecies() == null || properties.getSpecies().isBlank()) return 99;
        String key = canonical(properties);
        return CACHE.computeIfAbsent(key, ignored -> resolveUncached(properties));
    }

    public static int baseGeneration(PokemonProperties properties) {
        if (properties == null || properties.getSpecies() == null || properties.getSpecies().isBlank()) return 99;
        Species species = PokemonSpecies.getByName(properties.getSpecies());
        if (species == null && properties.getSpecies().contains(":")) {
            species = PokemonSpecies.getByName(properties.getSpecies().substring(properties.getSpecies().indexOf(':') + 1));
        }
        return FormGenerationResolver.baseGeneration(species);
    }

    public static int requiredGenerationFromSpec(String spec) {
        try {
            return requiredGeneration(PokemonProperties.Companion.parse(spec));
        } catch (Throwable ignored) {
            String first = spec == null ? "" : spec.trim().split("\\s+")[0];
            Species species = PokemonSpecies.getByName(first);
            return FormGenerationResolver.baseGeneration(species);
        }
    }

    private static int resolveUncached(PokemonProperties p) {
        Species species = PokemonSpecies.getByName(p.getSpecies());
        if (species == null && p.getSpecies().contains(":")) {
            species = PokemonSpecies.getByName(p.getSpecies().substring(p.getSpecies().indexOf(':') + 1));
        }
        if (species == null) return 99;
        return FormGenerationResolver.requiredGeneration(species, p.getAspects(), p.getForm());
    }

    public static String speciesKey(Pokemon pokemon) {
        return pokemon == null || pokemon.getSpecies() == null ? "" : normalize(pokemon.getSpecies().getName());
    }

    public static String speciesKey(PokemonProperties p) {
        return p == null ? "" : normalize(p.getSpecies());
    }

    public static String formKey(Pokemon pokemon) {
        if (pokemon == null || pokemon.getSpecies() == null || pokemon.getForm() == null) return "";
        return normalize(pokemon.getSpecies().getName()) + "#" + normalize(pokemon.getForm().getName());
    }

    public static String formKey(PokemonProperties p) {
        if (p == null) return "";
        String species = normalize(p.getSpecies());
        String form = normalize(p.getForm());
        if (!form.isBlank()) return species + "#" + form;
        if (p.getAspects() != null && !p.getAspects().isEmpty()) return species + "#" + String.join("+", p.getAspects().stream().sorted().toList());
        return species;
    }

    private static String canonical(PokemonProperties p) {
        return normalize(p.getSpecies()) + '|' + normalize(p.getForm()) + '|' + (p.getAspects() == null ? "" : String.join(",", p.getAspects().stream().sorted().toList()));
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String v = value.toLowerCase(Locale.ROOT).trim();
        int colon = v.indexOf(':');
        if (colon >= 0) v = v.substring(colon + 1);
        return v.replace(' ', '_');
    }

    public static void clearCache() { CACHE.clear(); }
}
