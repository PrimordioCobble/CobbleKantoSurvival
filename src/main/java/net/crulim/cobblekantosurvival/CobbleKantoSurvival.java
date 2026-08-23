package net.crulim.cobblekantosurvival;

import net.crulim.cobblekantosurvival.command.CksCommands;
import net.crulim.cobblekantosurvival.command.EnvironmentCommands;
import net.crulim.cobblekantosurvival.compat.CompatibilityManager;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.gate.AcquisitionGates;
import net.crulim.cobblekantosurvival.gate.FossilBreakGuard;
import net.crulim.cobblekantosurvival.gate.PokeSnackLegendaryBalancer;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.spawn.WorldSpawnPoolGate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobbleKantoSurvival implements ModInitializer {
    public static final String MOD_ID = "cobblekantosurvival";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final WorldSpawnPoolGate SPAWN_GATE = new WorldSpawnPoolGate();

    @Override
    public void onInitialize() {
        ConfigManager.load();
        AcquisitionGates.register();
        FossilBreakGuard.register();
        PokeSnackLegendaryBalancer.register();
        CompatibilityManager.register();
        CksCommands.register(SPAWN_GATE);
        EnvironmentCommands.register();

        // Safety: this bypass is meant for a supervised, short admin action.
        // Disconnecting the target clears it automatically; nothing is persisted.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            GenerationPolicy.clearTemporaryGenerationOneBypass(handler.getPlayer())
        );

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SPAWN_GATE.initialize();
            CompatibilityManager.applyDynamic(server);
            LOGGER.info("CobbleKantoSurvival started. generation={}, spawnGateReady={}", ConfigManager.get().currentGeneration, SPAWN_GATE.isReady());
        });
    }
}
