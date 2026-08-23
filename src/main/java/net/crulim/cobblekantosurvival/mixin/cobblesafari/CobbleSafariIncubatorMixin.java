package net.crulim.cobblekantosurvival.mixin.cobblesafari;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.item.PokeBallItem;
import maxigregrze.cobblesafari.block.incubator.IncubatorBlock;
import maxigregrze.cobblesafari.block.incubator.IncubatorBlockEntity;
import maxigregrze.cobblesafari.incubator.CobbreedingCompat;
import maxigregrze.cobblesafari.incubator.EggIncubatorRecipe;
import maxigregrze.cobblesafari.incubator.EggIncubatorRegistry;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CobbleSafari 0.3.2.field17test1 retrieval branch decrements the player's Poké Ball
 * before it creates/inserts the resulting Pokémon. Gate at interaction HEAD instead.
 *
 * Standard recipes can contain multiple random outputs, so retrieval is allowed only
 * when every declared output is currently legal. Missing/empty recipes fall back to
 * CobbleSafari's unrestricted "random" output and are therefore denied conservatively.
 */
@Pseudo
@Mixin(targets = "maxigregrze.cobblesafari.block.incubator.IncubatorBlock", remap = false)
public abstract class CobbleSafariIncubatorMixin {
    @Inject(method = {"useItemOn", "method_55765"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void cks$gateIncubatorRetrieval(ItemStack stack, BlockState state, Level world, BlockPos pos,
                                             Player user, InteractionHand hand, BlockHitResult hit,
                                             CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (world.isClientSide || !(user instanceof ServerPlayer player)) return;
        if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedCobbleSafariIncubatorResults) return;
        if (GenerationPolicy.bypass(player) || GenerationPolicy.sourceException("cobblesafari_incubator")) return;
        if (state.getValue(IncubatorBlock.STATE) != 6 || !(stack.getItem() instanceof PokeBallItem)) return;
        if (!(world.getBlockEntity(pos) instanceof IncubatorBlockEntity incubator)) return;

        boolean safe;
        if (incubator.isCobbreedingEgg()) {
            PokemonProperties properties;
            try {
                properties = CobbreedingCompat.extractProperties(incubator.getInputItem());
            } catch (Throwable ignored) {
                properties = null;
            }
            safe = properties != null && GenerationPolicy.acquisitionAllows(properties, player);
        } else {
            EggIncubatorRecipe recipe = EggIncubatorRegistry.getRecipe(incubator.getInputItem());
            safe = recipe != null && recipe.outputs() != null && !recipe.outputs().isEmpty();
            if (safe) {
                for (String output : recipe.outputs()) {
                    if (!allowsOutput(output, player)) {
                        safe = false;
                        break;
                    }
                }
            }
        }

        if (safe) return;
        player.sendSystemMessage(Component.literal(ConfigManager.get().messages.incubatorLocked));
        // HEAD cancellation: no Poké Ball decrement, no party insertion and no incubator reset.
        cir.setReturnValue(ItemInteractionResult.FAIL);
    }

    private static boolean allowsOutput(String output, ServerPlayer player) {
        if (output == null || output.isBlank() || output.equalsIgnoreCase("random")) return false;
        try {
            return GenerationPolicy.acquisitionAllows(PokemonProperties.Companion.parse(output), player);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
