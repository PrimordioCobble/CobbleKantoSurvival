package net.crulim.cobblekantosurvival.mixin.cobblesafari;

import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "maxigregrze.cobblesafari.network.GtsAppServerHandler", remap = false)
public abstract class CobbleSafariGtsHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cks$disableGts(ServerPlayer player, @Coerce Object payload, CallbackInfo ci) {
        if (!ConfigManager.get().enabled || !ConfigManager.get().services.disableCobbleSafariGts) return;
        player.sendSystemMessage(Component.literal(ConfigManager.get().messages.serviceDisabled));
        ci.cancel();
    }
}
