package net.crulim.cobblekantosurvival.compat;

/**
 * Marks the narrow server-thread window in which Cobbreeding 2.2.2 is posting
 * its HATCH_EGG_PRE event from PokemonEgg#hatchEgg.
 *
 * Cobbreeding 2.2.2 posts that cancelable event but does not inspect the
 * cancellation result before creating the Pokemon. CKS therefore uses its
 * dedicated PokemonEgg#inventoryTick mixin as the authoritative Cobbreeding
 * hatch gate and suppresses only the generic HATCH_EGG_PRE safety-net handler
 * while this context is active.
 *
 * ThreadLocal keeps this scoped to the thread executing the hatch and avoids
 * weakening HATCH_EGG_PRE protection for Cobblemon/base or other egg sources.
 */
public final class CobbreedingHatchContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private CobbreedingHatchContext() {}

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }
}
