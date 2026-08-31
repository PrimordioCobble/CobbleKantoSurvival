package net.crulim.cobblekantosurvival.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
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
        Map.entry("fwaystones", "3.3.4+mc1.21.1"),
        // The distributed file is named cobblemon-cards-fabric-1.0.4.jar,
        // but its Fabric metadata intentionally/still reports 1.0.0.
        Map.entry("cobblemon-cards", "1.0.0")
    );

    /**
     * Some third-party artifacts do not expose a release-unique metadata
     * version. For those integrations, fingerprint only the exact target class
     * we audited instead of trusting the JAR filename or widening the contract.
     */
    private static final Map<String, BytecodeFingerprint> BYTECODE = Map.of(
        "cobblemon-cards",
        new BytecodeFingerprint(
            "com/howlite/cobblemoncards/event/BinderSpawnModifier.class",
            "76bfb63e20352199734bbc3935c055d8d9f7d300ffb054bd005a17dfbb8ba302"
        )
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
        if (expected != null && !expected.equals(installed)) return false;
        return auditedBytecodeMatches(id);
    }

    public static Map<String, String> status() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : EXACT.entrySet()) {
            String installed = installed(e.getKey());
            if (installed == null) {
                out.put(e.getKey(), "not-loaded (audited=" + e.getValue() + ")");
            } else if (!installed.equals(e.getValue())) {
                out.put(e.getKey(), "UNSUPPORTED installed=" + installed + " audited=" + e.getValue());
            } else if (!auditedBytecodeMatches(e.getKey())) {
                out.put(e.getKey(), "UNSUPPORTED target-bytecode fingerprint (metadata=" + installed + ")");
            } else {
                out.put(e.getKey(), "OK " + installed + (BYTECODE.containsKey(e.getKey()) ? " (target-bytecode OK)" : ""));
            }
        }
        return out;
    }

    private static boolean auditedBytecodeMatches(String id) {
        BytecodeFingerprint fingerprint = BYTECODE.get(id);
        if (fingerprint == null) return true;

        ClassLoader loader = CompatibilityVersions.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(fingerprint.resource())) {
            if (in == null) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            return HexFormat.of().formatHex(digest).equals(fingerprint.sha256());
        } catch (Exception ignored) {
            return false;
        }
    }

    private record BytecodeFingerprint(String resource, String sha256) {}
}
