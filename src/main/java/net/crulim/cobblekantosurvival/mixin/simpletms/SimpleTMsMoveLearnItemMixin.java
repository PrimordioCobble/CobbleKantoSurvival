package net.crulim.cobblekantosurvival.mixin.simpletms;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.MoveGenerationResolver;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResultHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dragomordor.simpletms.item.custom.MoveLearnItem", remap = false)
public abstract class SimpleTMsMoveLearnItemMixin {
    @Shadow public abstract String getMoveName$common();

    @Inject(method = "applyToPokemon", at = @At("HEAD"), cancellable = true, remap = false)
    private void cks$gateManualTeaching(ServerPlayer player, ItemStack stack, Pokemon pokemon,
                                        CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!ConfigManager.get().enabled || !ConfigManager.get().tmTrGate.enabled || GenerationPolicy.bypass(player)) return;
        String move = getMoveName$common();
        if (MoveGenerationResolver.isManualTeachingAllowed(move)) return;
        int gen = MoveGenerationResolver.requiredGeneration(move);
        player.sendSystemMessage(Component.literal(ConfigManager.get().messages.tmLocked.formatted(gen)));
        // HEAD cancellation means no stack decrement, no durability damage and no cooldown.
        cir.setReturnValue(InteractionResultHolder.fail(stack));
    }
}
