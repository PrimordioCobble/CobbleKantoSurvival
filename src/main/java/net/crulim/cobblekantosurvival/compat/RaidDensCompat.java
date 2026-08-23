package net.crulim.cobblekantosurvival.compat;

import com.necro.raid.dens.common.events.RaidEvents;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.PokemonGenerationResolver;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public final class RaidDensCompat {
    private RaidDensCompat() {}

    public static boolean isLoaded() { return FabricLoader.getInstance().isModLoaded("cobblemonraiddens") && CompatibilityVersions.exactAuditedVersion("cobblemonraiddens"); }

    public static void register() {
        if (!isLoaded()) return;
        try {
            RaidEvents.REWARD_POKEMON.subscribe(event -> {
                if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedRaidRewards) return;
                if (!GenerationPolicy.acquisitionAllows(event.getPokemon(), event.getPlayer())) {
                    int gen = PokemonGenerationResolver.requiredGeneration(event.getPokemon());
                    event.cancel();
                    event.getPlayer().sendSystemMessage(Component.literal(ConfigManager.get().messages.raidRewardLocked.formatted(gen)));
                }
            });
        } catch (Throwable t) {
            net.crulim.cobblekantosurvival.CobbleKantoSurvival.LOGGER.error("Raid Dens compatibility registration failed; reward gate is NOT active", t);
        }
    }
}
