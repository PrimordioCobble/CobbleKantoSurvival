package net.crulim.cobblekantosurvival.generation;

import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.Locale;
import java.util.Set;

public final class FormGenerationResolver {
    private FormGenerationResolver() {}

    public static int requiredGeneration(Species species, FormData form) {
        if (species == null || form == null || form == species.getStandardForm()) return baseGeneration(species);
        int required = baseGeneration(species);
        for (String label : form.getLabels()) required = Math.max(required, generationFromToken(label));
        for (String aspect : form.getAspects()) required = Math.max(required, generationFromToken(aspect));
        return required;
    }

    public static int requiredGeneration(Species species, Set<String> aspects, String formName) {
        if (species == null) return 99;
        FormData form = null;
        if (formName != null && !formName.isBlank()) form = species.getFormByName(formName);
        if (form == null && aspects != null && !aspects.isEmpty()) form = species.getForm(aspects);
        if (form == null) form = species.getStandardForm();
        return requiredGeneration(species, form);
    }

    private static int generationFromToken(String raw) {
        if (raw == null) return 0;
        String token = raw.toLowerCase(Locale.ROOT).replace('-', '_');
        if (token.equals("alolan") || token.contains("alola")) return 7;
        if (token.equals("galarian") || token.contains("galar")) return 8;
        if (token.equals("hisuian") || token.contains("hisui")) return 8;
        if (token.equals("paldean") || token.contains("paldea")) return 9;
        if (token.equals("mega") || token.equals("mega_x") || token.equals("mega_y") || token.equals("primal")) return 6;
        if (token.equals("gmax") || token.contains("gigantamax")) return 8;
        if (token.startsWith("gen8a")) return 8;
        if (token.matches("gen[1-9].*")) return Character.digit(token.charAt(3), 10);
        return 0;
    }

    public static int baseGeneration(Species species) {
        if (species == null) return 99;
        int n = species.getNationalPokedexNumber();
        if (n <= 151) return 1;
        if (n <= 251) return 2;
        if (n <= 386) return 3;
        if (n <= 493) return 4;
        if (n <= 649) return 5;
        if (n <= 721) return 6;
        if (n <= 809) return 7;
        if (n <= 905) return 8;
        if (n <= 1025) return 9;
        return 99;
    }
}
