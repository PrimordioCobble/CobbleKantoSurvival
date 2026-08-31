package net.crulim.cobblekantosurvival.mixin.cobblemoncards;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cobblemon Cards' Binder spawn stats run after a wild Pokemon has already
 * entered the world and may replace it with an arbitrary implemented species
 * of the selected elemental type. That bypasses CobbleKanto Survival's normal
 * world spawn pool and therefore its generation/biome/weight rules.
 *
 * Keep the Cards event, Binder inventory and every non-spawn CardStat intact;
 * cancel only the private method whose sole job in the audited build is to
 * choose a replacement Species and call PokemonEntity#setPokemon(...).
 */
@Pseudo
@Mixin(targets = "com.howlite.cobblemoncards.event.BinderSpawnModifier", remap = false)
public abstract class CobblemonCardsBinderSpawnModifierMixin {
    @Inject(
        method = "transformPokemon",
        at = @At("HEAD"),
        cancellable = true,
        // Cobblemon Cards is optional and exact-target-bytecode gated by the
        // mixin plugin. Keep the injection itself fail-soft as a final guard.
        require = 0,
        remap = false
    )
    private static void cks$disableBinderSpeciesReplacement(
        PokemonEntity pokemonEntity,
        Pokemon oldPokemon,
        ElementalType targetType,
        float finalChance,
        CallbackInfo ci
    ) {
        ci.cancel();
    }
}
