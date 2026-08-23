package net.crulim.cobblekantosurvival.mixin.radgyms;

import lol.gito.radgyms.common.api.dto.reward.PokemonReward;
import lol.gito.radgyms.common.api.event.GymEvents;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.PokemonGenerationResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rad Gyms 0.4.4 supports PokemonReward entries that bypass Cobblemon capture
 * and directly call PlayerPartyStore.add. Filter those reward entries before
 * Rad Gyms creates the Pokemon. Item/loot/advancement/command rewards are not
 * modified.
 */
@Pseudo
@Mixin(targets = "lol.gito.radgyms.common.event.gyms.GenerateRewardHandler", remap = false)
public abstract class RadGymsPokemonRewardMixin {
    @Shadow(remap = false) @Final private GymEvents.GenerateRewardEvent event;

    @ModifyVariable(method = "handlePokemonRewards", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private List<PokemonReward> cks$filterPokemonRewards(List<PokemonReward> original) {
        if (!ConfigManager.get().enabled
            || !ConfigManager.get().acquisition.blockRadGymsPokemonRewards
            || GenerationPolicy.sourceException("rad_gyms_reward")) return original;

        ServerPlayer player = event.getPlayer();
        if (GenerationPolicy.bypass(player)) return original;

        List<PokemonReward> allowed = new ArrayList<>(original.size());
        for (PokemonReward reward : original) {
            if (reward == null || reward.getPokemon() == null) continue;
            if (GenerationPolicy.acquisitionAllows(reward.getPokemon(), player)) {
                allowed.add(reward);
                continue;
            }
            int generation = PokemonGenerationResolver.requiredGeneration(reward.getPokemon());
            player.sendSystemMessage(Component.literal(ConfigManager.get().messages.gymRewardLocked.formatted(generation)));
        }
        return allowed;
    }
}
