package net.crulim.cobblekantosurvival.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.crulim.cobblekantosurvival.CobbleKantoSurvival;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("cobblekantosurvival");
    private static final Path FILE = DIR.resolve("server.json");
    private static volatile ServerConfig config = new ServerConfig();

    private ConfigManager() {}

    public static synchronized ServerConfig load() {
        try {
            Files.createDirectories(DIR);
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    ServerConfig loaded = GSON.fromJson(reader, ServerConfig.class);
                    if (loaded != null) config = loaded;
                }
            }
            config.validate();
            save();
        } catch (Exception e) {
            CobbleKantoSurvival.LOGGER.error("Failed to load {}, using last/default config", FILE, e);
        }
        return config;
    }

    public static synchronized void save() throws IOException {
        Files.createDirectories(DIR);
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(config, writer);
        }
    }

    public static ServerConfig get() { return config; }
    public static Path path() { return FILE; }

    public static synchronized boolean setGeneration(int generation) {
        if (generation < 2 || generation > 9) return false;
        config.currentGeneration = generation;
        try {
            save();
            return true;
        } catch (IOException e) {
            CobbleKantoSurvival.LOGGER.error("Could not persist generation {}", generation, e);
            return false;
        }
    }
}
