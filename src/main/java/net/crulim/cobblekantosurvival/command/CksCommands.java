package net.crulim.cobblekantosurvival.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.crulim.cobblekantosurvival.CobbleKantoSurvival;
import net.crulim.cobblekantosurvival.compat.CompatibilityManager;
import net.crulim.cobblekantosurvival.compat.CompatibilityVersions;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.PokemonGenerationResolver;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.performance.SaveDiagnostics;
import net.crulim.cobblekantosurvival.spawn.WorldSpawnPoolGate;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public final class CksCommands {
    private static final ResourceLocation COBBLEMON_TRIAL_SPAWNER = ResourceLocation.fromNamespaceAndPath(
        "cobblemontrialsedition",
        "cobblemon_trial_spawner"
    );
    private static final String LEGACY_TRIAL_SPAWN_DATA = "spawn_data";
    private static final String LEGACY_TRIAL_MARKER = "CKSBlockedGeneration";

    private CksCommands() {}

    public static void register(WorldSpawnPoolGate spawnGate) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            Commands.literal("cks")
                .requires(src -> src.hasPermission(ConfigManager.get().adminBypassPermissionLevel))
                .then(Commands.literal("status").executes(ctx -> {
                    var cfg = ConfigManager.get();
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] enabled=" + cfg.enabled + ", generation=" + cfg.currentGeneration + ", spawnGate=" + (spawnGate.isReady() ? "READY" : "NOT_READY") + ", legacyConflict=" + spawnGate.hasLegacyConflict()), false);
                    return 1;
                }))
                .then(Commands.literal("generation")
                    .then(Commands.literal("get").executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("[CKS] currentGeneration=" + ConfigManager.get().currentGeneration), false);
                        return ConfigManager.get().currentGeneration;
                    }))
                    .then(Commands.literal("set")
                        .then(Commands.argument("generation", IntegerArgumentType.integer(2, 9)).executes(ctx -> {
                            int gen = IntegerArgumentType.getInteger(ctx, "generation");
                            if (!ConfigManager.setGeneration(gen)) {
                                ctx.getSource().sendFailure(Component.literal("[CKS] Falha ao persistir generation=" + gen));
                                return 0;
                            }
                            PokemonGenerationResolver.clearCache();
                            boolean live = spawnGate.reapply();
                            CompatibilityManager.applyDynamic(ctx.getSource().getServer());
                            CobbleKantoSurvival.LOGGER.info("Generation unlocked/set to {}", gen);
                            String suffix = live ? "spawn pool reaplicado ao vivo" : "spawn pool NÃO reaplicado; confira /cks audit e migração";
                            ctx.getSource().sendSuccess(() -> Component.literal("[CKS] Geração atual: " + gen + " — " + suffix + "."), true);
                            return 1;
                        }))))
                .then(Commands.literal("reload").executes(ctx -> {
                    ConfigManager.load();
                    PokemonGenerationResolver.clearCache();
                    boolean live = spawnGate.reapply();
                    CompatibilityManager.applyDynamic(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] Config recarregada. spawnGateReapplied=" + live), false);
                    return 1;
                }))
                .then(Commands.literal("bypass")
                    .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        boolean enabled = GenerationPolicy.toggleTemporaryGenerationOneBypass(target);
                        String playerName = target.getGameProfile().getName();

                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "[CKS] Bypass temporário da Geração 1 para " + playerName + ": "
                                + (enabled ? "ATIVADO" : "DESATIVADO")
                                + ". Gen 3+ continua bloqueada."
                        ), true);

                        target.sendSystemMessage(Component.literal(
                            "[CKS] Bypass temporário da Geração 1 "
                                + (enabled ? "ATIVADO" : "DESATIVADO")
                                + "."
                        ));
                        return 1;
                    })))
                .then(Commands.literal("trials")
                    .then(Commands.literal("repair").executes(ctx -> repairLegacyTrialSpawners(ctx.getSource()))))
                .then(Commands.literal("saves").executes(ctx -> {
                    var perf = ConfigManager.get().performance;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "[CKS-SAVE] autosaveOverride=" + perf.overrideAutosaveInterval
                            + ", interval=" + perf.autosaveIntervalMinutes + "m"
                            + ", waystonesDeferredSave=" + perf.deferFabricWaystonesImmediateSave
                            + ", diagnostics=" + perf.saveDiagnostics
                            + ", slowWarning=" + perf.slowSaveWarningMs + "ms"
                            + ", top=" + perf.savedDataTopEntries
                    ), false);
                    SaveDiagnostics.auditLines().forEach(line ->
                        ctx.getSource().sendSuccess(() -> Component.literal(line), false)
                    );
                    return 1;
                }))
                .then(Commands.literal("audit").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] config=" + ConfigManager.path()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] pool=" + spawnGate.getLastBefore() + " -> " + spawnGate.getLastAfter() + ", ready=" + spawnGate.isReady() + ", legacyConflict=" + spawnGate.hasLegacyConflict()), false);
                    CompatibilityManager.detected().forEach((id, loaded) -> ctx.getSource().sendSuccess(() -> Component.literal("[CKS] compat " + id + "=" + loaded), false));
                    CompatibilityVersions.status().forEach((id, status) -> ctx.getSource().sendSuccess(() -> Component.literal("[CKS] version " + id + "=" + status), false));
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] CobbleSafari incubator gate=" + ConfigManager.get().acquisition.blockLockedCobbleSafariIncubatorResults + " (mixed/random outputs use conservative deny policy)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] Cobblemon Quests givepokemon gate=" + ConfigManager.get().acquisition.blockCobblemonQuestsGivePokemonRewards), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] Rad Gyms Pokémon reward gate=" + ConfigManager.get().acquisition.blockRadGymsPokemonRewards), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] Rad Gyms Poké Cache gate=" + ConfigManager.get().acquisition.blockRadGymsCachePokemonRewards + " (same type/rarity weighted reroll; Legendary/Mythical excluded)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "[CKS] PokéSnack Legendary/Mythical balance="
                            + ConfigManager.get().pokeSnacks.balanceLegendaryAndMythicalSpawns
                            + ", maxRarityTier=" + ConfigManager.get().pokeSnacks.maxLegendaryMythicalRarityTier
                    ), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] temporary Gen-I player bypasses=" + GenerationPolicy.temporaryGenerationOneBypassCount()), false);
                    var perf = ConfigManager.get().performance;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "[CKS] autosave override=" + perf.overrideAutosaveInterval
                            + ", interval=" + perf.autosaveIntervalMinutes + "m"
                            + ", waystonesDeferredSave=" + perf.deferFabricWaystonesImmediateSave
                            + ", saveDiagnostics=" + perf.saveDiagnostics
                    ), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("[CKS] Daycare+ status: NOT AUDITED/NOT ACTIVE — no Daycare+ JAR exists in the supplied bundle (config residue only)."), false);
                    return 1;
                }))
        ));
    }

    private static int repairLegacyTrialSpawners(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        // Automatic radius: exactly the server's current view-distance in chunks.
        // getChunkNow() is intentionally used so this maintenance command never loads/generates chunks.
        int viewDistance = Math.max(0, source.getServer().getPlayerList().getViewDistance());
        int centerChunkX = player.getBlockX() >> 4;
        int centerChunkZ = player.getBlockZ() >> 4;

        int loadedChunksScanned = 0;
        int trialSpawnersSeen = 0;
        int contaminated = 0;
        int repaired = 0;

        for (int chunkX = centerChunkX - viewDistance; chunkX <= centerChunkX + viewDistance; chunkX++) {
            for (int chunkZ = centerChunkZ - viewDistance; chunkZ <= centerChunkZ + viewDistance; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                loadedChunksScanned++;

                // Snapshot because repairing a block entity may notify the chunk while we are iterating it.
                for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
                    if (!COBBLEMON_TRIAL_SPAWNER.equals(blockId)) {
                        continue;
                    }

                    trialSpawnersSeen++;

                    CompoundTag tag = blockEntity.saveCustomOnly(level.registryAccess());
                    if (!hasLegacyTrialMarker(tag)) {
                        continue;
                    }

                    contaminated++;
                    tag.remove(LEGACY_TRIAL_SPAWN_DATA);

                    blockEntity.loadCustomOnly(tag, level.registryAccess());
                    blockEntity.setChanged();

                    BlockPos pos = blockEntity.getBlockPos();
                    level.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);

                    repaired++;
                    CobbleKantoSurvival.LOGGER.info(
                        "Repaired legacy Trials spawn_data at {} {} {} in {}",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        level.dimension().location()
                    );
                }
            }
        }

        int finalLoadedChunksScanned = loadedChunksScanned;
        int finalTrialSpawnersSeen = trialSpawnersSeen;
        int finalContaminated = contaminated;
        int finalRepaired = repaired;

        source.sendSuccess(() -> Component.literal(
            "[CKS] Trials repair: " + finalRepaired + " reparado(s), "
                + finalContaminated + " contaminado(s), "
                + finalTrialSpawnersSeen + " Trial Spawner(s) verificado(s), "
                + finalLoadedChunksScanned + " chunk(s) carregado(s) escaneado(s). "
                + "Raio automático=view-distance " + viewDistance + " chunks. Nenhum chunk foi carregado/gerado pelo comando."
        ), true);

        return 1;
    }

    private static boolean hasLegacyTrialMarker(CompoundTag blockEntityTag) {
        CompoundTag spawnData = blockEntityTag.getCompound(LEGACY_TRIAL_SPAWN_DATA);
        CompoundTag entity = spawnData.getCompound("entity");
        return entity.contains(LEGACY_TRIAL_MARKER);
    }
}
