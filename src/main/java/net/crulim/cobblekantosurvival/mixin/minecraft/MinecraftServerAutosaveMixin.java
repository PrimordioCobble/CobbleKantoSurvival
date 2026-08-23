package net.crulim.cobblekantosurvival.mixin.minecraft;

import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.performance.SaveDiagnostics;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerAutosaveMixin {
    private static final int CKS_TICKS_PER_MINUTE = 20 * 60;

    @Inject(method = "computeNextAutosaveInterval", at = @At("RETURN"), cancellable = true)
    private void cks$overridePeriodicAutosaveInterval(CallbackInfoReturnable<Integer> cir) {
        var config = ConfigManager.get();
        if (!config.enabled || !config.performance.overrideAutosaveInterval) return;

        cir.setReturnValue(config.performance.autosaveIntervalMinutes * CKS_TICKS_PER_MINUTE);
    }

    @Inject(method = "saveEverything", at = @At("HEAD"))
    private void cks$beginSaveDiagnostics(
        boolean suppressLog,
        boolean flush,
        boolean forced,
        CallbackInfoReturnable<Boolean> cir
    ) {
        SaveDiagnostics.beginSave((MinecraftServer) (Object) this, suppressLog, flush, forced);
    }

    @Inject(method = "saveEverything", at = @At("RETURN"))
    private void cks$endSaveDiagnostics(
        boolean suppressLog,
        boolean flush,
        boolean forced,
        CallbackInfoReturnable<Boolean> cir
    ) {
        SaveDiagnostics.endSave(Boolean.TRUE.equals(cir.getReturnValue()));
    }
}
