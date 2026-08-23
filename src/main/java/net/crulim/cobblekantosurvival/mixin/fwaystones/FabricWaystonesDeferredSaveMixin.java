package net.crulim.cobblekantosurvival.mixin.fwaystones;

import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.performance.SaveDiagnostics;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fabric Waystones 3.3.4 calls the overworld DimensionDataStorage#save() from
 * saveWaystones(). That persists every dirty SavedData, not only the waystone
 * state, and can synchronously stall the server thread.
 *
 * The original method has already marked its own state dirty and, when asked,
 * synchronized players before this invocation. We only defer the final global
 * data-storage save to Minecraft's normal autosave/backup/manual/shutdown path.
 */
@Pseudo
@Mixin(targets = "wraith.fwaystones.util.WaystoneStorage", remap = false)
public abstract class FabricWaystonesDeferredSaveMixin {
    @Redirect(
        method = "saveWaystones(Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/DimensionDataStorage;save()V",
            remap = true
        ),
        // Fail-soft by design: exact version gating is the primary guard, while
        // require=0 prevents an unexpected transformed bytecode shape from taking
        // the whole server down. /cks saves confirms live application via counter.
        require = 0,
        remap = false
    )
    private void cks$deferImmediateGlobalSavedDataSave(DimensionDataStorage storage) {
        var config = ConfigManager.get();
        if (!config.enabled || !config.performance.deferFabricWaystonesImmediateSave) {
            storage.save();
            return;
        }

        SaveDiagnostics.recordDeferredWaystonesSave();
    }
}
