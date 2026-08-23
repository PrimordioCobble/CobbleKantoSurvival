package net.crulim.cobblekantosurvival.spawn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LegacyServerFixesDetector {
    private LegacyServerFixesDetector() {}

    public static boolean legacyKantoFilterEnabled() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("cobblekanto/server_fixes.json");
        if (!Files.exists(path)) return false;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return root.has("blockKantoNaturalSpawns") && root.get("blockKantoNaturalSpawns").getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }
}
