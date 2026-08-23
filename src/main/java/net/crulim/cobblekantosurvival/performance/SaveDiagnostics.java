package net.crulim.cobblekantosurvival.performance;

import net.crulim.cobblekantosurvival.CobbleKantoSurvival;
import net.crulim.cobblekantosurvival.config.ConfigManager;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Low-overhead instrumentation for the server's full save path.
 *
 * <p>There is intentionally no per-tick work here. A context is created only when
 * Minecraft performs a full save, and individual SavedData timing is recorded only
 * for dirty data that Minecraft was already going to write.</p>
 */
public final class SaveDiagnostics {
    private static final double NANOS_TO_MS = 1.0 / 1_000_000.0;
    private static final ThreadLocal<SaveContext> ACTIVE = new ThreadLocal<>();
    private static final Object STATS_LOCK = new Object();
    private static final EnumMap<Source, Aggregate> SOURCE_STATS = new EnumMap<>(Source.class);
    private static final Map<String, DataAggregate> DATA_STATS = new HashMap<>();
    private static final Map<String, ClassAggregate> CLASS_STATS = new HashMap<>();
    private static long deferredWaystonesImmediateSaves;

    private SaveDiagnostics() {}

    public static void beginSave(MinecraftServer server, boolean suppressLog, boolean flush, boolean forced) {
        if (!ConfigManager.get().performance.saveDiagnostics) {
            ACTIVE.remove();
            return;
        }

        ACTIVE.set(new SaveContext(
            System.nanoTime(),
            detectSource(),
            suppressLog,
            flush,
            forced,
            server.getPlayerCount()
        ));
    }

    public static boolean isSaveInProgress() {
        return ACTIVE.get() != null;
    }

    public static void recordSavedData(Object savedData, File file, long durationNanos) {
        SaveContext context = ACTIVE.get();
        if (context == null || durationNanos < 0L) return;

        long sizeBytes = file.isFile() ? file.length() : -1L;
        String className = savedData.getClass().getName();
        String simpleClassName = savedData.getClass().getSimpleName();
        if (simpleClassName == null || simpleClassName.isBlank()) simpleClassName = className;

        context.savedData.add(new DataSample(
            file.getName(),
            className,
            simpleClassName,
            durationNanos,
            sizeBytes
        ));
    }

    /** Records that Fabric Waystones skipped its immediate global SavedData flush. */
    public static void recordDeferredWaystonesSave() {
        synchronized (STATS_LOCK) {
            deferredWaystonesImmediateSaves++;
        }
    }

    public static void endSave(boolean success) {
        SaveContext context = ACTIVE.get();
        ACTIVE.remove();
        if (context == null) return;

        long totalNanos = Math.max(0L, System.nanoTime() - context.startNanos);
        long savedDataNanos = context.savedData.stream().mapToLong(DataSample::durationNanos).sum();
        long otherNanos = Math.max(0L, totalNanos - savedDataNanos);

        Map<String, CurrentClassAggregate> currentClasses = aggregateClasses(context.savedData);

        synchronized (STATS_LOCK) {
            SOURCE_STATS.computeIfAbsent(context.source, ignored -> new Aggregate()).add(totalNanos);
            for (DataSample sample : context.savedData) {
                String key = sample.fileName + "|" + sample.className;
                DATA_STATS.computeIfAbsent(
                    key,
                    ignored -> new DataAggregate(sample.fileName, sample.className, sample.simpleClassName)
                ).add(sample.durationNanos, sample.sizeBytes);
            }
            for (CurrentClassAggregate current : currentClasses.values()) {
                CLASS_STATS.computeIfAbsent(
                    current.className,
                    ignored -> new ClassAggregate(current.className, current.simpleClassName)
                ).addSave(current.count, current.totalNanos, current.totalSizeBytes);
            }
        }

        int topCount = ConfigManager.get().performance.savedDataTopEntries;
        List<DataSample> topFiles = context.savedData.stream()
            .sorted(Comparator.comparingLong(DataSample::durationNanos).reversed())
            .limit(topCount)
            .toList();
        List<CurrentClassAggregate> topClasses = currentClasses.values().stream()
            .sorted(Comparator.comparingLong((CurrentClassAggregate value) -> value.totalNanos).reversed())
            .limit(topCount)
            .toList();

        String message = String.format(
            Locale.ROOT,
            "[CKS-SAVE] source=%s total=%.1fms savedData=%.1fms other=%.1fms dirtySavedData=%d players=%d flush=%s forced=%s success=%s topClasses=%s top=%s",
            context.source,
            totalNanos * NANOS_TO_MS,
            savedDataNanos * NANOS_TO_MS,
            otherNanos * NANOS_TO_MS,
            context.savedData.size(),
            context.playerCount,
            context.flush,
            context.forced,
            success,
            formatTopClasses(topClasses),
            formatTop(topFiles)
        );

        if (totalNanos * NANOS_TO_MS >= ConfigManager.get().performance.slowSaveWarningMs) {
            CobbleKantoSurvival.LOGGER.warn(message);
        } else {
            CobbleKantoSurvival.LOGGER.info(message);
        }
    }

    public static List<String> auditLines() {
        List<String> lines = new ArrayList<>();
        synchronized (STATS_LOCK) {
            lines.add("[CKS-SAVE] waystonesDeferredImmediateSaves=" + deferredWaystonesImmediateSaves);

            if (SOURCE_STATS.isEmpty()) {
                lines.add("[CKS-SAVE] Ainda não há saves completos medidos desde este restart.");
                return lines;
            }

            for (Source source : Source.values()) {
                Aggregate aggregate = SOURCE_STATS.get(source);
                if (aggregate == null || aggregate.count == 0) continue;
                lines.add(String.format(
                    Locale.ROOT,
                    "[CKS-SAVE] %s count=%d last=%.1fms avg=%.1fms max=%.1fms",
                    source,
                    aggregate.count,
                    aggregate.lastNanos * NANOS_TO_MS,
                    aggregate.averageNanos() * NANOS_TO_MS,
                    aggregate.maxNanos * NANOS_TO_MS
                ));
            }

            CLASS_STATS.values().stream()
                .sorted(Comparator.comparingLong((ClassAggregate value) -> value.totalNanos).reversed())
                .limit(ConfigManager.get().performance.savedDataTopEntries)
                .forEach(value -> lines.add(String.format(
                    Locale.ROOT,
                    "[CKS-SAVE] class=%s saves=%d writes=%d total=%.1fms avgPerWrite=%.1fms maxSave=%.1fms lastBytes=%s",
                    value.simpleClassName,
                    value.saveCount,
                    value.writeCount,
                    value.totalNanos * NANOS_TO_MS,
                    value.averageWriteNanos() * NANOS_TO_MS,
                    value.maxSaveNanos * NANOS_TO_MS,
                    formatBytes(value.lastSaveBytes)
                )));

            DATA_STATS.values().stream()
                .sorted(Comparator.comparingLong((DataAggregate value) -> value.totalNanos).reversed())
                .limit(ConfigManager.get().performance.savedDataTopEntries)
                .forEach(value -> lines.add(String.format(
                    Locale.ROOT,
                    "[CKS-SAVE] data=%s class=%s writes=%d total=%.1fms avg=%.1fms max=%.1fms lastSize=%s",
                    value.fileName,
                    value.simpleClassName,
                    value.count,
                    value.totalNanos * NANOS_TO_MS,
                    value.averageNanos() * NANOS_TO_MS,
                    value.maxNanos * NANOS_TO_MS,
                    formatBytes(value.lastSizeBytes)
                )));
        }
        return lines;
    }

    private static Map<String, CurrentClassAggregate> aggregateClasses(List<DataSample> samples) {
        Map<String, CurrentClassAggregate> classes = new HashMap<>();
        for (DataSample sample : samples) {
            CurrentClassAggregate aggregate = classes.computeIfAbsent(
                sample.className,
                ignored -> new CurrentClassAggregate(sample.className, sample.simpleClassName)
            );
            aggregate.add(sample.durationNanos, sample.sizeBytes);
        }
        return classes;
    }

    private static Source detectSource() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        for (StackTraceElement element : stack) {
            String className = element.getClassName().toLowerCase(Locale.ROOT);
            if (className.contains("advancedbackups")) return Source.ADVANCED_BACKUPS;
        }
        for (StackTraceElement element : stack) {
            String className = element.getClassName().toLowerCase(Locale.ROOT);
            if (className.contains("saveallcommand")) return Source.MANUAL;
        }
        for (StackTraceElement element : stack) {
            if (!element.getClassName().equals("net.minecraft.server.MinecraftServer")) continue;
            String method = element.getMethodName();
            if (method.equals("stopServer") || method.equals("shutdown") || method.equals("method_3782")) {
                return Source.SHUTDOWN;
            }
        }
        for (StackTraceElement element : stack) {
            if (!element.getClassName().equals("net.minecraft.server.MinecraftServer")) continue;
            String method = element.getMethodName();
            if (method.equals("tickServer") || method.equals("tick") || method.equals("method_3748")) {
                return Source.AUTOSAVE;
            }
        }
        return Source.EXTERNAL;
    }

    private static String formatTop(List<DataSample> samples) {
        if (samples.isEmpty()) return "[]";
        return samples.stream()
            .map(sample -> String.format(
                Locale.ROOT,
                "%s:%s=%.1fms/%s",
                sample.fileName,
                sample.simpleClassName,
                sample.durationNanos * NANOS_TO_MS,
                formatBytes(sample.sizeBytes)
            ))
            .toList()
            .toString();
    }

    private static String formatTopClasses(List<CurrentClassAggregate> classes) {
        if (classes.isEmpty()) return "[]";
        return classes.stream()
            .map(value -> String.format(
                Locale.ROOT,
                "%s=%d/%.1fms/%s",
                value.simpleClassName,
                value.count,
                value.totalNanos * NANOS_TO_MS,
                formatBytes(value.totalSizeBytes)
            ))
            .toList()
            .toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0L) return "?";
        if (bytes < 1024L) return bytes + "B";
        double kib = bytes / 1024.0;
        if (kib < 1024.0) return String.format(Locale.ROOT, "%.1fKiB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024.0) return String.format(Locale.ROOT, "%.1fMiB", mib);
        return String.format(Locale.ROOT, "%.2fGiB", mib / 1024.0);
    }

    public enum Source {
        AUTOSAVE,
        ADVANCED_BACKUPS,
        MANUAL,
        SHUTDOWN,
        EXTERNAL
    }

    private record SaveContext(
        long startNanos,
        Source source,
        boolean suppressLog,
        boolean flush,
        boolean forced,
        int playerCount,
        List<DataSample> savedData
    ) {
        private SaveContext(
            long startNanos,
            Source source,
            boolean suppressLog,
            boolean flush,
            boolean forced,
            int playerCount
        ) {
            this(startNanos, source, suppressLog, flush, forced, playerCount, new ArrayList<>());
        }
    }

    private record DataSample(
        String fileName,
        String className,
        String simpleClassName,
        long durationNanos,
        long sizeBytes
    ) {}

    private static final class CurrentClassAggregate {
        private final String className;
        private final String simpleClassName;
        private int count;
        private long totalNanos;
        private long totalSizeBytes;

        private CurrentClassAggregate(String className, String simpleClassName) {
            this.className = className;
            this.simpleClassName = simpleClassName;
        }

        private void add(long nanos, long sizeBytes) {
            count++;
            totalNanos += nanos;
            if (sizeBytes >= 0L) totalSizeBytes += sizeBytes;
        }
    }

    private static final class Aggregate {
        private long count;
        private long totalNanos;
        private long maxNanos;
        private long lastNanos;

        private void add(long nanos) {
            count++;
            totalNanos += nanos;
            maxNanos = Math.max(maxNanos, nanos);
            lastNanos = nanos;
        }

        private double averageNanos() {
            return count == 0 ? 0.0 : (double) totalNanos / count;
        }
    }

    private static final class DataAggregate {
        private final String fileName;
        private final String className;
        private final String simpleClassName;
        private long count;
        private long totalNanos;
        private long maxNanos;
        private long lastSizeBytes = -1L;

        private DataAggregate(String fileName, String className, String simpleClassName) {
            this.fileName = fileName;
            this.className = className;
            this.simpleClassName = simpleClassName;
        }

        private void add(long nanos, long sizeBytes) {
            count++;
            totalNanos += nanos;
            maxNanos = Math.max(maxNanos, nanos);
            lastSizeBytes = sizeBytes;
        }

        private double averageNanos() {
            return count == 0 ? 0.0 : (double) totalNanos / count;
        }
    }

    private static final class ClassAggregate {
        private final String className;
        private final String simpleClassName;
        private long saveCount;
        private long writeCount;
        private long totalNanos;
        private long maxSaveNanos;
        private long lastSaveBytes;

        private ClassAggregate(String className, String simpleClassName) {
            this.className = className;
            this.simpleClassName = simpleClassName;
        }

        private void addSave(long writes, long nanos, long bytes) {
            saveCount++;
            writeCount += writes;
            totalNanos += nanos;
            maxSaveNanos = Math.max(maxSaveNanos, nanos);
            lastSaveBytes = bytes;
        }

        private double averageWriteNanos() {
            return writeCount == 0 ? 0.0 : (double) totalNanos / writeCount;
        }
    }
}
