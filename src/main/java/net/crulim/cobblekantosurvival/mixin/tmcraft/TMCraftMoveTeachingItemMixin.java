package net.crulim.cobblekantosurvival.mixin.tmcraft;

import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.MoveGenerationResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "kiwiapollo.tmcraft.item.MoveTeachingItem", remap = false)
public abstract class TMCraftMoveTeachingItemMixin {
    @Shadow @Final private String move;

    @Inject(method = {"interactLivingEntity", "method_7847"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void cks$gateOnlyTmSubclass(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (!getClass().getName().equals("kiwiapollo.tmcraft.item.tmmove.TMMoveTeachingItem")) return;
        if (!(user instanceof ServerPlayer player)) return;
        if (!ConfigManager.get().enabled || !ConfigManager.get().tmTrGate.enabled || GenerationPolicy.bypass(player)) return;
        if (MoveGenerationResolver.isManualTeachingAllowed(move)) return;
        int gen = MoveGenerationResolver.requiredGeneration(move);
        player.sendSystemMessage(Component.literal(ConfigManager.get().messages.tmLocked.formatted(gen)));
        cir.setReturnValue(InteractionResult.FAIL);
    }
}
