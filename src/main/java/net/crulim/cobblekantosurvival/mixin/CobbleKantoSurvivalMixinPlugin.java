package net.crulim.cobblekantosurvival.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.crulim.cobblekantosurvival.compat.CompatibilityVersions;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class CobbleKantoSurvivalMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String id = null;
        if (mixinClassName.contains(".cobbreeding.")) id = "cobbreeding";
        else if (mixinClassName.contains(".simpletms.")) id = "simpletms";
        else if (mixinClassName.contains(".tmcraft.")) id = "tmcraft";
        else if (mixinClassName.contains(".cobblesafari.")) id = "cobblesafari";
        else if (mixinClassName.contains(".raiddens.")) id = "cobblemonraiddens";
        else if (mixinClassName.contains(".cobblemonquests.")) id = "cobblemon_quests";
        else if (mixinClassName.contains(".radgyms.")) id = "rad_gyms";
        else if (mixinClassName.contains(".fwaystones.")) id = "fwaystones";
        if (id == null) return true;
        if (!FabricLoader.getInstance().isModLoaded(id)) return false;
        if (!CompatibilityVersions.exactAuditedVersion(id)) {
            System.err.println("[CobbleKantoSurvival] Skipping optional mixin " + mixinClassName
                + " because " + id + " version is " + CompatibilityVersions.installed(id)
                + " but audited version is " + CompatibilityVersions.expected(id));
            return false;
        }
        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
