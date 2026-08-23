package net.crulim.cobblekantosurvival.mixin.cobblemon;

import com.cobblemon.mod.common.api.fossil.Fossil;
import com.cobblemon.mod.common.block.multiblock.FossilMultiblockStructure;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.PokemonGenerationResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cobblemon 1.7.3 emits FOSSIL_REVIVED only after adding the Pokémon to party and after
 * the retrieval Poké Ball is consumed. Gate the completed machine at interaction HEAD
 * instead, preserving the fossil result and the player's item until the generation unlocks.
 */
@Mixin(value = FossilMultiblockStructure.class, remap = false)
public abstract class FossilRetrievalMixin {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void cks$gateFossilRetrieval(BlockState state, Level world, BlockPos pos, Player player,
                                         BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (world.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return;
        if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedFossilRevival || GenerationPolicy.bypass(serverPlayer)) return;
        FossilMultiblockStructure self = (FossilMultiblockStructure) (Object) this;
        if (!self.getHasCreatedPokemon()) return;
        Fossil fossil = self.getResultingFossil();
        if (fossil == null || fossil.getResult() == null || GenerationPolicy.acquisitionAllows(fossil.getResult(), serverPlayer)) return;
        int gen = PokemonGenerationResolver.requiredGeneration(fossil.getResult());
        serverPlayer.sendSystemMessage(Component.literal(ConfigManager.get().messages.fossilLocked.formatted(gen)));
        cir.setReturnValue(InteractionResult.FAIL);
    }
}
