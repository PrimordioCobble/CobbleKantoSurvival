package net.crulim.cobblekantosurvival.generation;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import net.crulim.cobblekantosurvival.CobbleKantoSurvival;
import net.crulim.cobblekantosurvival.config.ConfigManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cobblemon 1.7.3 MoveTemplate exposes the canonical Showdown move number but no debut-generation field.
 * The installed TM mods identify moves by Showdown/Cobblemon move name. Number ranges are stable by
 * introduction order; custom/unknown moves fall through to the configured safe policy.
 */
public final class MoveGenerationResolver {
    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<String> LOGGED_UNKNOWN = ConcurrentHashMap.newKeySet();
    private MoveGenerationResolver() {}

    public static int requiredGeneration(String moveName) {
        if (moveName == null || moveName.isBlank()) return 0;
        return CACHE.computeIfAbsent(moveName.toLowerCase(), MoveGenerationResolver::resolve);
    }

    private static int resolve(String name) {
        MoveTemplate move = Moves.getByName(name);
        if (move == null) return 0;
        int num = move.getNum();
        if (num >= 1 && num <= 165) return 1;
        if (num <= 251) return 2;
        if (num <= 354) return 3;
        if (num <= 467) return 4;
        if (num <= 559) return 5;
        if (num <= 621) return 6;
        if (num <= 742) return 7;
        if (num <= 850) return 8;
        if (num > 850) return 9;
        return 0;
    }

    public static boolean isManualTeachingAllowed(String moveName) {
        if (!ConfigManager.get().enabled || !ConfigManager.get().tmTrGate.enabled || !ConfigManager.get().tmTrGate.gateByMoveDebutGeneration) return true;
        int required = requiredGeneration(moveName);
        if (required == 0) {
            boolean allow = !"BLOCK_AND_LOG".equals(ConfigManager.get().tmTrGate.unknownMovePolicy);
            if (LOGGED_UNKNOWN.add(moveName.toLowerCase())) {
                CobbleKantoSurvival.LOGGER.warn("TM/TR generation unknown for move '{}' -> {} (logged once for this runtime)", moveName, allow ? "ALLOW" : "BLOCK");
            }
            return allow;
        }
        // Gen-I moves are not treated as species acquisition; old moves remain valid manual techniques.
        return required == 1 || required <= ConfigManager.get().currentGeneration;
    }
}
