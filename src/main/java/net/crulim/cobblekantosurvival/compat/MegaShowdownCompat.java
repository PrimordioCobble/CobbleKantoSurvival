package net.crulim.cobblekantosurvival.compat;

import com.github.yajatkaul.mega_showdown.config.MegaShowdownConfig;
import com.github.yajatkaul.mega_showdown.gimmick.GimmickTurnCheck;
import net.crulim.cobblekantosurvival.CobbleKantoSurvival;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public final class MegaShowdownCompat {
    private static boolean captured;
    private static boolean baseMega, baseZ, baseDynamax, baseTera, baseOutsideMega, baseOutsideUltra;

    private MegaShowdownCompat() {}

    public static boolean isLoaded() { return FabricLoader.getInstance().isModLoaded("mega_showdown") && CompatibilityVersions.exactAuditedVersion("mega_showdown"); }

    public static void apply(MinecraftServer server) {
        if (!isLoaded()) return;
        try {
            if (!captured) {
                baseMega = MegaShowdownConfig.mega;
                baseZ = MegaShowdownConfig.zMoves;
                baseDynamax = MegaShowdownConfig.dynamax;
                baseTera = MegaShowdownConfig.teralization;
                baseOutsideMega = MegaShowdownConfig.outSideMega;
                baseOutsideUltra = MegaShowdownConfig.outSideUltraBurst;
                captured = true;
            }
            int gen = ConfigManager.get().currentGeneration;
            var g = ConfigManager.get().gimmicks;
            boolean gateEnabled = ConfigManager.get().enabled;
            MegaShowdownConfig.mega = baseMega && (!gateEnabled || gen >= g.megaUnlockGeneration);
            MegaShowdownConfig.outSideMega = baseOutsideMega && (!gateEnabled || gen >= g.megaUnlockGeneration);
            MegaShowdownConfig.zMoves = baseZ && (!gateEnabled || gen >= g.zMovesUnlockGeneration);
            MegaShowdownConfig.outSideUltraBurst = baseOutsideUltra && (!gateEnabled || gen >= g.ultraBurstUnlockGeneration);
            MegaShowdownConfig.dynamax = baseDynamax && (!gateEnabled || gen >= g.dynamaxUnlockGeneration);
            MegaShowdownConfig.teralization = baseTera && (!gateEnabled || gen >= g.terastallizationUnlockGeneration);
            if (server != null) server.getPlayerList().getPlayers().forEach(GimmickTurnCheck::check);
        } catch (Throwable t) {
            CobbleKantoSurvival.LOGGER.error("Mega Showdown compatibility bridge failed; gimmick gating is NOT trustworthy until resolved", t);
        }
    }
}
