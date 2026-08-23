package net.crulim.cobblekantosurvival.mixin.cobblemonquests;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.command.argument.PokemonPropertiesArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.crulim.cobblekantosurvival.generation.GenerationPolicy;
import net.crulim.cobblekantosurvival.generation.PokemonGenerationResolver;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cobblemon Quests 1.2.0 exposes a givepokemon command that can create a
 * PokemonProperties result and insert it directly into PlayerPartyStore.
 * We stop only invocations whose should_give argument is true, before the
 * addon creates the Pokemon, processes quest actions, or mutates storage.
 */
@Pseudo
@Mixin(targets = "winterwolfsv.cobblemon_quests.commands.GivePokemonCommand", remap = false)
public abstract class CobblemonQuestsGivePokemonMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cks$gateGivePokemon(CommandContext<CommandSourceStack> context,
                                            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        if (!ConfigManager.get().enabled
            || !ConfigManager.get().acquisition.blockCobblemonQuestsGivePokemonRewards
            || GenerationPolicy.sourceException("cobblemon_quests_givepokemon")) return;

        // should_give=false is used as an event/task-processing surface and does not
        // grant ownership, so it remains untouched.
        if (!BoolArgumentType.getBool(context, "should_give")) return;

        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        if (GenerationPolicy.bypass(player)) return;

        PokemonProperties properties = PokemonPropertiesArgumentType.Companion.getPokemonProperties(context, "properties");
        if (GenerationPolicy.acquisitionAllows(properties, player)) return;

        int generation = PokemonGenerationResolver.requiredGeneration(properties);
        player.sendSystemMessage(Component.literal(ConfigManager.get().messages.questRewardLocked.formatted(generation)));
        cir.setReturnValue(0);
    }
}
