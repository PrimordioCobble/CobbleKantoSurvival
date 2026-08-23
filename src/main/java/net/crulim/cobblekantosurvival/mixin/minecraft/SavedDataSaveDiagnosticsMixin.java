package net.crulim.cobblekantosurvival.mixin.minecraft;

import net.crulim.cobblekantosurvival.performance.SaveDiagnostics;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(SavedData.class)
public abstract class SavedDataSaveDiagnosticsMixin {
    @Unique
    private long cks$saveStartedAtNanos;

    @Inject(
        method = "save(Ljava/io/File;Lnet/minecraft/core/HolderLookup$Provider;)V",
        at = @At("HEAD")
    )
    private void cks$beginSavedDataTiming(File file, HolderLookup.Provider registries, CallbackInfo ci) {
        SavedData self = (SavedData) (Object) this;
        cks$saveStartedAtNanos = SaveDiagnostics.isSaveInProgress() && self.isDirty()
            ? System.nanoTime()
            : 0L;
    }

    @Inject(
        method = "save(Ljava/io/File;Lnet/minecraft/core/HolderLookup$Provider;)V",
        at = @At("RETURN")
    )
    private void cks$endSavedDataTiming(File file, HolderLookup.Provider registries, CallbackInfo ci) {
        long startedAt = cks$saveStartedAtNanos;
        cks$saveStartedAtNanos = 0L;
        if (startedAt == 0L) return;

        SaveDiagnostics.recordSavedData((Object) this, file, System.nanoTime() - startedAt);
    }
}
