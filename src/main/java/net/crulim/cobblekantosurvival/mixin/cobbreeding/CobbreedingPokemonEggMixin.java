package net.crulim.cobblekantosurvival.mixin.cobbreeding;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import ludichat.cobbreeding.EggUtilities;
import ludichat.cobbreeding.PokemonEgg;
import net.crulim.cobblekantosurvival.compat.CobbreedingHatchContext;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.PokemonGenerationResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cobbreeding 2.2.2 hatch safety gate.
 *
 * In 2.2.2 the actual hatch happens from PokemonEgg#inventoryTick once the
 * egg timer reaches zero. Cancelling PokemonEgg#use is not sufficient because
 * use() is only used for the optional egg-decryption interaction.
 *
 * We intercept only when the egg is actually ready to attempt a hatch:
 * timer <= 0 and Cobbreeding's one-second accumulator reached its hatch check.
 * Cancelling at HEAD in that exact state happens before:
 *
 * - Cobbreeding posts HATCH_EGG_PRE;
 * - PokemonProperties#create();
 * - Party/PC insertion;
 * - the final ItemStack#shrink(1).
 *
 * SECOND is reset to zero on a denied attempt so a locked ready egg is checked
 * at Cobbreeding's normal one-second cadence instead of every server tick.
 */
@Pseudo
@Mixin(targets = "ludichat.cobbreeding.PokemonEgg", remap = false)
public abstract class CobbreedingPokemonEggMixin {

    /**
     * Suppresses chat spam while a ready locked egg is re-checked once per
     * second. Weak keys ensure discarded/consumed ItemStacks do not create a
     * permanent cache. The stored value is the generation at which that exact
     * stack was last reported.
     */
    private static final Map<ItemStack, Integer> cks$eggLockNoticeGeneration =
        Collections.synchronizedMap(new WeakHashMap<>());


    @Inject(method = "hatchEgg", at = @At("HEAD"), remap = false)
    private void cks$enterCobbreedingHatchContext(
        net.minecraft.world.entity.player.Player entity,
        PokemonProperties properties,
        CallbackInfo ci
    ) {
        CobbreedingHatchContext.enter();
    }

    @Inject(method = "hatchEgg", at = @At("RETURN"), remap = false)
    private void cks$exitCobbreedingHatchContext(
        net.minecraft.world.entity.player.Player entity,
        PokemonProperties properties,
        CallbackInfo ci
    ) {
        CobbreedingHatchContext.exit();
    }

    @Inject(
        method = {"inventoryTick", "method_7888"},
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void cks$blockLockedCobbreedingHatch(
        ItemStack stack,
        Level world,
        Entity entity,
        int slot,
        boolean selected,
        CallbackInfo ci
    ) {
        if (world.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedEggResults) return;

        // Cobbreeding 2.2.2 only attempts the real hatch when both conditions
        // below are met. Reading them here prevents unnecessary generation
        // resolution on every inventory tick.
        final int timer;
        final int second;
        try {
            timer = stack.getOrDefault(PokemonEgg.Companion.getTIMER(), 600);
            second = stack.getOrDefault(PokemonEgg.Companion.getSECOND(), 0);
        } catch (Throwable ignored) {
            // Optional compat must fail soft if the audited addon changes.
            return;
        }

        if (timer > 0 || second < 20) return;

        final PokemonProperties properties;
        try {
            properties = EggUtilities.extractProperties(stack);
        } catch (Throwable ignored) {
            return;
        }

        if (properties == null || properties.getSpecies() == null || properties.getSpecies().isBlank()) return;

        // Cobbreeding's optional Ditto+Ditto random egg does not resolve its
        // concrete species until the same hatch tick. Its default is disabled.
        // While generation gating is active, be conservative before Gen IX;
        // once Gen IX is unlocked every canonical generation is available.
        if ("random".equalsIgnoreCase(properties.getSpecies())) {
            if (GenerationPolicy.bypass(player) || ConfigManager.get().currentGeneration >= 9) return;

            try {
                stack.set(PokemonEgg.Companion.getSECOND(), 0);
            } catch (Throwable ignored) {
                // The cancellation below is the safety-critical action.
            }

            cks$notifyEggLockedOnce(stack, player, 9);
            ci.cancel();
            return;
        }

        if (GenerationPolicy.acquisitionAllows(properties, player)) {
            cks$eggLockNoticeGeneration.remove(stack);
            return;
        }

        int gen = PokemonGenerationResolver.requiredGeneration(properties);

        // Keep the egg ready, but re-check at Cobbreeding's normal ~1 second
        // cadence rather than canceling the item's inventory tick every tick.
        try {
            stack.set(PokemonEgg.Companion.getSECOND(), 0);
        } catch (Throwable ignored) {
            // Do not let a cosmetic cadence reset defeat the actual gate.
        }

        cks$notifyEggLockedOnce(stack, player, gen);

        // Critical: returning from inventoryTick here also skips Cobbreeding's
        // final stack.shrink(1), so a denied egg is not consumed.
        ci.cancel();
    }
    /**
     * A locked ready egg is intentionally re-evaluated at Cobbreeding's normal
     * one-second cadence so it can hatch automatically after a live generation
     * unlock. The player, however, should only receive one notice for that
     * ItemStack at the current generation.
     */
    private static void cks$notifyEggLockedOnce(ItemStack stack, ServerPlayer player, int requiredGeneration) {
        int currentGeneration = ConfigManager.get().currentGeneration;
        Integer previous = cks$eggLockNoticeGeneration.put(stack, currentGeneration);
        if (previous != null && previous == currentGeneration) return;

        player.sendSystemMessage(Component.literal(
            ConfigManager.get().messages.eggLocked.formatted(requiredGeneration)
        ));
    }

}
