package net.crulim.cobblekantosurvival.mixin.raiddens;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.PokemonGenerationResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Raid Dens 0.11.3+1.21.1.field17test1 consumes the client-side reward choice
 * immediately after the player answers the reward overlay. If a future-generation
 * Pokemon is rejected only at REWARD_POKEMON, the Pokeball is preserved but the
 * client can no longer choose the item reward for that raid.
 *
 * Gate one step earlier by changing only the catch-rate value sent to the reward
 * overlay. A catch rate of 0 is the addon's native "not catchable" state, so the
 * overlay exposes only its item-reward button. RewardHandler/REWARD_QUEUE remains
 * fully native. RaidDensCompat's REWARD_POKEMON listener stays registered as a
 * server-side safety net for any alternate or forged catch path.
 */
@Pseudo
@Mixin(targets = "com.necro.raid.dens.common.raids.rewards.RewardHandler", remap = false)
public abstract class RaidDensRewardPreviewMixin {
    @Shadow(remap = false) @Final private Pokemon pokemonReward;

    @ModifyArgs(
        method = "sendRewardMessage",
        at = @At(
            value = "INVOKE",
            target = "Lorg/apache/logging/log4j/util/TriConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
            remap = false
        ),
        remap = false
    )
    private void cks$hideLockedPokemonCatch(Args args) {
        Object playerArg = args.get(0);
        Object catchRateArg = args.get(1);

        if (!(playerArg instanceof ServerPlayer player)) return;
        if (!(catchRateArg instanceof Float)) return;
        if (!ConfigManager.get().enabled || !ConfigManager.get().acquisition.blockLockedRaidRewards) return;
        if (pokemonReward == null || GenerationPolicy.acquisitionAllows(pokemonReward, player)) return;

        int gen = PokemonGenerationResolver.requiredGeneration(pokemonReward);
        player.sendSystemMessage(Component.literal(ConfigManager.get().messages.raidRewardLocked.formatted(gen)));
        args.set(1, Float.valueOf(0.0F));
    }
}
