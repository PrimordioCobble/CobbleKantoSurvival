package net.crulim.cobblekantosurvival.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;


public final class EnvironmentCommands {
    private static final String[] MOON_PHASE_NAMES = {
            "Lua Cheia",
            "Gibosa Minguante",
            "Quarto Minguante",
            "Minguante",
            "Lua Nova",
            "Crescente",
            "Quarto Crescente",
            "Gibosa Crescente"
    };

    private static final String[] LEGENDARY_BIOME_CATEGORIES = {
            "arid",
            "cave",
            "coast",
            "floral",
            "forest",
            "frozen",
            "highlands",
            "jungle",
            "magical_forest",
            "mountain",
            "ocean",
            "plains",
            "swamp",
            "volcanic",
            "wasteland"
    };

    private EnvironmentCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("lua")
                            .requires(EnvironmentCommands::isPlayerSource)
                            .executes(ctx -> sendEnvironmentInfo(ctx.getSource()))
            );

            dispatcher.register(
                    Commands.literal("clima")
                            .requires(EnvironmentCommands::isPlayerSource)
                            .executes(ctx -> sendEnvironmentInfo(ctx.getSource()))
            );
        });
    }

    private static boolean isPlayerSource(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer;
    }

    private static int sendEnvironmentInfo(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        int timeTick = (int) Math.floorMod(level.getDayTime(), 24000L);

        int moonPhase = Math.floorMod(level.getMoonPhase(), 8);
        String moonName = MOON_PHASE_NAMES[moonPhase];

        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();
        String weather = thundering ? "Tempestade" : (raining ? "Chuva" : "Limpo");
        ChatFormatting weatherColor = thundering ? ChatFormatting.GOLD : (raining ? ChatFormatting.AQUA : ChatFormatting.GREEN);

        boolean canSeeSky = level.canSeeSkyFromBelowWater(pos);
        int totalLight = level.getMaxLocalRawBrightness(pos);

        Holder<Biome> biome = level.getBiome(pos);
        String biomeId = biome.unwrapKey()
                .map(key -> key.location().toString())
                .orElse("desconhecido");

        List<String> categories = matchingLegendaryBiomeCategories(biome);
        String categoryText = categories.isEmpty() ? "nenhuma" : String.join(", ", categories);

        String timeRanges = String.join(" · ", activeCobblemonTimeRanges(timeTick));

        player.sendSystemMessage(
                Component.literal("◆ ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("CONDIÇÕES DE SPAWN").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
        );

        player.sendSystemMessage(line("Lua", ChatFormatting.GRAY,
                moonName + " (" + (moonPhase + 1) + "/8)",
                ChatFormatting.LIGHT_PURPLE));

        player.sendSystemMessage(line("Períodos", ChatFormatting.GRAY,
                timeRanges,
                ChatFormatting.YELLOW));

        player.sendSystemMessage(line("Clima", ChatFormatting.GRAY,
                weather,
                weatherColor));

        player.sendSystemMessage(line("Bioma", ChatFormatting.GRAY,
                biomeId,
                ChatFormatting.AQUA));

        player.sendSystemMessage(line("Categorias", ChatFormatting.GRAY,
                categoryText,
                categories.isEmpty() ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN));

        player.sendSystemMessage(
                Component.literal("Altitude: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("Y " + pos.getY()).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("  |  Céu aberto: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(canSeeSky ? "Sim" : "Não").withStyle(canSeeSky ? ChatFormatting.GREEN : ChatFormatting.RED))
        );

        player.sendSystemMessage(line("Luz", ChatFormatting.GRAY,
                Integer.toString(totalLight),
                ChatFormatting.YELLOW));

        return 1;
    }

    private static Component line(String label, ChatFormatting labelColor, String value, ChatFormatting valueColor) {
        return Component.literal(label + ": ").withStyle(labelColor)
                .append(Component.literal(value).withStyle(valueColor));
    }


    private static List<String> activeCobblemonTimeRanges(int tick) {
        List<String> active = new ArrayList<>();

        if (inWrappedRange(tick, 23460, 12541)) active.add("Dia");
        if (inRange(tick, 12542, 23459)) active.add("Noite");
        if (inWrappedRange(tick, 23000, 4999)) active.add("Manhã");
        if (inRange(tick, 5000, 6999)) active.add("Meio-dia");
        if (inRange(tick, 7000, 12999)) active.add("Tarde");
        if (inRange(tick, 13000, 16999)) active.add("Anoitecer");
        if (inRange(tick, 17000, 18999)) active.add("Meia-noite");
        if (inRange(tick, 19000, 22999)) active.add("Pré-amanhecer");
        if (inWrappedRange(tick, 22300, 166)) active.add("Amanhecer");
        if (inRange(tick, 11834, 13701)) active.add("Entardecer");
        if (inRange(tick, 11834, 13701) || inWrappedRange(tick, 22300, 166)) active.add("Crepúsculo");

        return active;
    }

    private static boolean inRange(int tick, int min, int max) {
        return tick >= min && tick <= max;
    }

    private static boolean inWrappedRange(int tick, int min, int max) {
        return tick >= min || tick <= max;
    }

    private static List<String> matchingLegendaryBiomeCategories(Holder<Biome> biome) {
        List<String> matches = new ArrayList<>();
        for (String category : LEGENDARY_BIOME_CATEGORIES) {
            TagKey<Biome> legendaryTag = TagKey.create(
                    Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath("cobblekanto", "legendary/" + category)
            );
            if (biome.is(legendaryTag)) {
                matches.add(category);
            }
        }
        return matches;
    }
}
