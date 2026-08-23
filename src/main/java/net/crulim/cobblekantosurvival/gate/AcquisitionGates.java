package net.crulim.cobblekantosurvival.gate;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.crulim.cobblekantosurvival.compat.CobbreedingHatchContext;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.PokemonGenerationResolver;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public final class AcquisitionGates {
    private AcquisitionGates() {}

    public static void register() {
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(event -> {
            if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedCaptures) return;
            Entity owner = event.getPokeBall().getOwner();
            ServerPlayer player = owner instanceof ServerPlayer sp ? sp : null;
            if (!GenerationPolicy.acquisitionAllows(event.getPokemon().getPokemon(), player)) {
                int gen = PokemonGenerationResolver.requiredGeneration(event.getPokemon().getPokemon());
                event.cancel();
                if (player != null) player.sendSystemMessage(Component.literal(ConfigManager.get().messages.captureLocked.formatted(gen)));
            }
        });

        CobblemonEvents.EVOLUTION_ACCEPTED.subscribe(event -> {
            if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedEvolutions) return;
            ServerPlayer owner = event.getPokemon().getOwnerPlayer();
            if (!GenerationPolicy.acquisitionAllows(event.getEvolution().getResult(), owner)) {
                int gen = PokemonGenerationResolver.requiredGeneration(event.getEvolution().getResult());
                event.cancel();
                if (owner != null) owner.sendSystemMessage(Component.literal(ConfigManager.get().messages.evolutionLocked.formatted(gen)));
            }
        });

        CobblemonEvents.COLLECT_EGG.subscribe(event -> {
            if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedEggResults) return;

            /*
             * Cobbreeding 2.2.2 emits COLLECT_EGG from its PastureBlock mixin
             * only AFTER it has already removed the egg from the Pasture and
             * inserted the ItemStack into the player's inventory. It also
             * substitutes a blank PokemonProperties when extraction fails.
             *
             * Therefore its COLLECT_EGG event is neither an authoritative
             * cancellation point nor a reliable payload for generation
             * gating. The dedicated PokemonEgg#inventoryTick mixin is the
             * authoritative Cobbreeding gate and blocks the actual hatch
             * before Pokemon creation and before ItemStack#shrink(1).
             *
             * Skip only the exact Cobbreeding emission. Other COLLECT_EGG
             * sources keep the generic safety net below.
             */
            if (isCobbreedingPastureCollectEmission()) return;

            if (!GenerationPolicy.acquisitionAllows(event.getEgg(), event.getPlayer())) {
                int gen = PokemonGenerationResolver.requiredGeneration(event.getEgg());
                event.cancel();
                event.getPlayer().sendSystemMessage(Component.literal(ConfigManager.get().messages.eggLocked.formatted(gen)));
            }
        });

        CobblemonEvents.HATCH_EGG_PRE.subscribe(event -> {
            if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedEggResults) return;

            // Cobbreeding 2.2.2 posts HATCH_EGG_PRE but does not honor its
            // cancellation result before creating the Pokemon. Its dedicated
            // inventoryTick mixin gates the hatch earlier, before creation and
            // before ItemStack#shrink(1). Do not run this generic safety net a
            // second time for that exact Cobbreeding call.
            if (CobbreedingHatchContext.isActive()) return;

            if (!GenerationPolicy.acquisitionAllows(event.getEgg(), event.getPlayer())) {
                int gen = PokemonGenerationResolver.requiredGeneration(event.getEgg());
                event.cancel();
                event.getPlayer().sendSystemMessage(Component.literal(ConfigManager.get().messages.eggLocked.formatted(gen)));
            }
        });


        CobblemonEvents.STARTER_CHOSEN.subscribe(event -> {
            if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedStarters) return;
            if (!GenerationPolicy.acquisitionAllows(event.getPokemon(), event.getPlayer())) {
                int gen = PokemonGenerationResolver.requiredGeneration(event.getPokemon());
                event.cancel();
                event.getPlayer().sendSystemMessage(Component.literal(ConfigManager.get().messages.starterLocked.formatted(gen)));
            }
        });

        // Safety net for Cobblemon's own spawn event only. We intentionally do NOT hook
        // generic Minecraft entity spawning or POKEMON_SENT_PRE, because owned locked
        // Pokémon are explicitly allowed to be sent out.
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(event -> {
            if (!ConfigManager.get().enabled || !ConfigManager.get().naturalSpawns.enabled) return;
            if (!GenerationPolicy.worldAllows(event.getEntity().getPokemon())) event.cancel();
        });
    }

    /**
     * Detects only the late COLLECT_EGG emission performed by Cobbreeding
     * 2.2.2's PastureBlockMixin.
     *
     * Important Mixin detail: PastureBlockMixin itself is not a runtime stack
     * frame. Its injected handler is merged into Cobblemon's target class:
     * com.cobblemon.mod.common.block.PastureBlock. Therefore the stack must be
     * matched against the target class, not the source mixin class.
     *
     * This keeps the workaround narrow and preserves the generic COLLECT_EGG
     * safety net for events emitted elsewhere. The walk runs only on egg
     * collection events, never per tick.
     */
    private static boolean isCobbreedingPastureCollectEmission() {
        if (!FabricLoader.getInstance().isModLoaded("cobbreeding")) return false;

        return StackWalker.getInstance().walk(frames ->
            frames.limit(32).anyMatch(frame ->
                "com.cobblemon.mod.common.block.PastureBlock".equals(frame.getClassName())
            )
        );
    }
}
