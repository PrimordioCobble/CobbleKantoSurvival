package net.crulim.cobblekantosurvival.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CompatibilityManager {
    private CompatibilityManager() {}

    public static void register() { RaidDensCompat.register(); }
    public static void applyDynamic(MinecraftServer server) { MegaShowdownCompat.apply(server); }

    public static Map<String, Boolean> detected() {
        String[] ids = {"mega_showdown","cobblesafari","simpletms","tmcraft","cobbreeding","cobblemonraiddens","cobblemontrialsedition","cobblemon_quests","rad_gyms","cobblemon_home","daycareplus"};
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (String id : ids) map.put(id, FabricLoader.getInstance().isModLoaded(id));
        return map;
    }
}
