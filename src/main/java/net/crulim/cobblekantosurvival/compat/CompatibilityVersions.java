package net.crulim.cobblekantosurvival.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact bytecode versions audited for optional integration points. */
public final class CompatibilityVersions {
    private static final Map<String, String> EXACT = Map.ofEntries(
        Map.entry("cobbreeding", "2.2.2"),
        Map.entry("simpletms", "2.3.3"),
        Map.entry("tmcraft", "1.4.18+1.7.3"),
        Map.entry("cobblesafari", "0.3.2.field17test1"),
        Map.entry("mega_showdown", "1.9.2+1.7.3+1.21.1-hotfix"),
        Map.entry("cobblemonraiddens", "0.11.3+1.21.1.field17test1"),
        Map.entry("cobblemon_quests", "1.2.0"),
        Map.entry("rad_gyms", "0.4.4"),
        Map.entry("fwaystones", "3.3.4+mc1.21.1")
    );

    private CompatibilityVersions() {}

    public static String expected(String id) { return EXACT.get(id); }

    public static String installed(String id) {
        return FabricLoader.getInstance().getModContainer(id)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse(null);
    }

    public static boolean exactAuditedVersion(String id) {
        String expected = expected(id);
        String installed = installed(id);
        return expected == null || expected.equals(installed);
    }

    public static Map<String, String> status() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : EXACT.entrySet()) {
            String installed = installed(e.getKey());
            if (installed == null) out.put(e.getKey(), "not-loaded (audited=" + e.getValue() + ")");
            else if (installed.equals(e.getValue())) out.put(e.getKey(), "OK " + installed);
            else out.put(e.getKey(), "UNSUPPORTED installed=" + installed + " audited=" + e.getValue());
        }
        return out;
    }
}
