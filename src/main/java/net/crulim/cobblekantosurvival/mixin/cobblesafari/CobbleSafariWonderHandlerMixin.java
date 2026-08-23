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
@Mixin(targets = "maxigregrze.cobblesafari.network.WonderAppServerHandler", remap = false)
public abstract class CobbleSafariWonderHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cks$disableWonderTrade(ServerPlayer player, @Coerce Object payload, CallbackInfo ci) {
        if (!ConfigManager.get().enabled || !ConfigManager.get().services.disableCobbleSafariWonderTrade) return;
        player.sendSystemMessage(Component.literal(ConfigManager.get().messages.serviceDisabled));
        ci.cancel();
    }
}
