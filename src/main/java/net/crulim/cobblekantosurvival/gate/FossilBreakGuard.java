package net.crulim.cobblekantosurvival.gate;

import com.cobblemon.mod.common.api.fossil.Fossil;
import com.cobblemon.mod.common.api.multiblock.MultiblockEntity;
import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.PokemonGenerationResolver;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Prevents a completed, generation-locked fossil result from being released as
 * a wild Pokemon by breaking any block that belongs to the Resurrection Machine.
 *
 * Normal dismantling remains untouched unless the machine currently holds a
 * completed locked result. OP+Creative uses the central gameplay bypass.
 */
public final class FossilBreakGuard {
    private FossilBreakGuard() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return true;

            if (!ConfigManager.get().enabled
                || !ConfigManager.get().acquisition.blockLockedFossilRevival
                || GenerationPolicy.bypass(serverPlayer)) {
                return true;
            }

            if (!(blockEntity instanceof MultiblockEntity multiblockEntity)) return true;
            if (!(multiblockEntity.getMultiblockStructure() instanceof FossilMultiblockStructure fossilStructure)) return true;
            if (!fossilStructure.getHasCreatedPokemon()) return true;

            Fossil fossil = fossilStructure.getResultingFossil();
            if (fossil == null || fossil.getResult() == null
                || GenerationPolicy.acquisitionAllows(fossil.getResult(), serverPlayer)) {
                return true;
            }

            int generation = PokemonGenerationResolver.requiredGeneration(fossil.getResult());
            serverPlayer.sendSystemMessage(Component.literal(
                ConfigManager.get().messages.fossilLocked.formatted(generation)
            ));
            return false;
        });
    }
}
