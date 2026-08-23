package net.crulim.cobblekantosurvival.spawn;

import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import net.crulim.cobblekantosurvival.CobbleKantoSurvival;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class WorldSpawnPoolGate {
    private final List<SpawnDetail> baseline = new ArrayList<>();
    private final Map<PokemonHerdSpawnDetail, List<PokemonHerdSpawnDetail.Herdable>> herdBaseline = new IdentityHashMap<>();
    private SpawnPool pool;
    private boolean subscribed;
    private boolean applying;
    private boolean ready;
    private boolean legacyConflict;
    private int lastBefore;
    private int lastAfter;

    public synchronized void initialize() {
        if (!ConfigManager.get().enabled || !ConfigManager.get().naturalSpawns.enabled) return;
        legacyConflict = LegacyServerFixesDetector.legacyKantoFilterEnabled();
        if (legacyConflict) {
            ready = false;
            CobbleKantoSurvival.LOGGER.error("Legacy CobbleKantoServerFixes Kanto spawn filter is still enabled. CKS spawn-pool gate will NOT snapshot/apply to avoid recording an already-filtered baseline. Disable blockKantoNaturalSpawns + logKantoNaturalSpawnFilter and cold restart.");
            return;
        }
        pool = CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL();
        if (pool == null) {
            CobbleKantoSurvival.LOGGER.error("Cobblemon WORLD_SPAWN_POOL is null; spawn pool gate unavailable.");
            return;
        }
        snapshotFullPool();
        if (!subscribed) {
            pool.getObservable().subscribe(reloaded -> {
                synchronized (WorldSpawnPoolGate.this) {
                    if (applying || LegacyServerFixesDetector.legacyKantoFilterEnabled()) return;
                    pool = reloaded;
                    snapshotFullPool();
                    applyFromBaseline();
                }
            });
            subscribed = true;
        }
        applyFromBaseline();
    }

    private void snapshotFullPool() {
        baseline.clear();
        herdBaseline.clear();
        baseline.addAll(pool.getDetails());
        for (SpawnDetail detail : baseline) {
            if (detail instanceof PokemonHerdSpawnDetail herd) {
                herdBaseline.put(herd, new ArrayList<>(herd.getHerdablePokemon()));
            }
        }
        ready = true;
    }

    public synchronized boolean reapply() {
        if (LegacyServerFixesDetector.legacyKantoFilterEnabled()) {
            legacyConflict = true;
            ready = false;
            return false;
        }
        if (!ready || pool == null) return false;
        applyFromBaseline();
        return true;
    }

    private void restoreBaseline() {
        pool.getDetails().clear();
        pool.getDetails().addAll(baseline);
        for (Map.Entry<PokemonHerdSpawnDetail, List<PokemonHerdSpawnDetail.Herdable>> e : herdBaseline.entrySet()) {
            e.getKey().setHerdablePokemon(new ArrayList<>(e.getValue()));
        }
    }

    private void applyFromBaseline() {
        applying = true;
        try {
            restoreBaseline();
            lastBefore = pool.getDetails().size();
            if (ConfigManager.get().enabled && ConfigManager.get().naturalSpawns.enabled) {
                pool.getDetails().removeIf(detail -> {
                    if (detail instanceof PokemonSpawnDetail pokemonDetail) {
                        return !GenerationPolicy.worldAllows(pokemonDetail.getPokemon());
                    }
                    if (detail instanceof PokemonHerdSpawnDetail herd) {
                        List<PokemonHerdSpawnDetail.Herdable> allowed = herd.getHerdablePokemon().stream()
                                .filter(h -> GenerationPolicy.worldAllows(h.getPokemon()))
                                .toList();
                        herd.setHerdablePokemon(new ArrayList<>(allowed));
                        return allowed.isEmpty();
                    }
                    return false;
                });
            }
            pool.precalculate();
            lastAfter = pool.getDetails().size();
            if (ConfigManager.get().debugLogging) {
                CobbleKantoSurvival.LOGGER.info("World spawn pool filtered: {} -> {} details (generation {})", lastBefore, lastAfter, ConfigManager.get().currentGeneration);
            }
        } finally {
            applying = false;
        }
    }

    public boolean isReady() { return ready; }
    public boolean hasLegacyConflict() { return legacyConflict || LegacyServerFixesDetector.legacyKantoFilterEnabled(); }
    public int getLastBefore() { return lastBefore; }
    public int getLastAfter() { return lastAfter; }
}
