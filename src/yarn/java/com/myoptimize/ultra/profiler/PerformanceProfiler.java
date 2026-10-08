package com.myoptimize.ultra.profiler;

import com.myoptimize.ultra.adaptive.AdaptiveEngine;
import com.myoptimize.ultra.memory.GcMonitor;
import com.myoptimize.ultra.scheduler.UltraFrameScheduler;
import net.minecraft.client.MinecraftClient;

/**
 * Встроенный профилировщик мода — центр сбора всех метрик.
 *
 * Метрики: FPS, frametime (средний/p99/1% low/0.1% low/max), frame pacing score,
 * CPU (JVM), RAM, GC, аллокации по потокам, количество сущностей/частиц/чанков,
 * аплоады чанков, время по категориям (KillerFinder), состояние планировщика.
 *
 * Тяжёлый расчёт статистики frametime вынесен на фоновый поток
 * (UltraFrameScheduler) — см. requestStatsUpdate().
 */
public final class PerformanceProfiler {

    private static final PerformanceProfiler INSTANCE = new PerformanceProfiler();

    private final FrameTimeTracker frameTimes = new FrameTimeTracker(600);
    private final KillerFinder killerFinder = new KillerFinder();
    private final GcMonitor gcMonitor = new GcMonitor();

    private volatile AllocationTracker allocationTracker;
    private volatile FrameTimeTracker.FrameStats lastStats = new FrameTimeTracker.FrameStats();
    private volatile long lastFrameStartNanos = -1;
    private volatile long lastFrameNanos = 16_666_666L; // дефолт 60 FPS

    // Счётчики (обновляются из mixins / подсистем)
    private volatile int entityCount;
    private volatile int particleCount;
    private volatile int loadedChunkCount;
    private volatile int blockChangesThisFrame;
    private volatile int changedSectionsThisFrame;
    private volatile int chunkUploadsThisFrame;
    private volatile int particlePacketsQueued;
    private volatile int particlePacketsProcessed;

    private PerformanceProfiler() {
    }

    public static PerformanceProfiler get() {
        return INSTANCE;
    }

    public FrameTimeTracker frameTimes() {
        return frameTimes;
    }

    public KillerFinder killerFinder() {
        return killerFinder;
    }

    public GcMonitor gcMonitor() {
        return gcMonitor;
    }

    public void setAllocationTracker(AllocationTracker tracker) {
        this.allocationTracker = tracker;
    }

    public AllocationTracker allocationTracker() {
        return allocationTracker;
    }

    // ===== Границы кадра (вызываются из MixinGameRenderer) =====

    public void onFrameStart() {
        long now = System.nanoTime();
        if (lastFrameStartNanos > 0) {
            lastFrameNanos = now - lastFrameStartNanos;
            frameTimes.recordFrame(lastFrameNanos);
        }
        lastFrameStartNanos = now;
        killerFinder.beginFrame();
        blockChangesThisFrame = 0;
        changedSectionsThisFrame = 0;
        chunkUploadsThisFrame = 0;
    }

    public long lastFrameNanos() {
        return lastFrameNanos;
    }

    public double lastFrameMs() {
        return lastFrameNanos / 1_000_000.0;
    }

    // ===== Асинхронный расчёт статистики =====

    /** Поставить пересчёт статистики frametime в очередь планировщика. */
    public void requestStatsUpdate(UltraFrameScheduler scheduler) {
        long[] snapshot = frameTimes.snapshot();
        long budget = targetBudgetNanos();
        scheduler.schedule("frametime-stats", UltraFrameScheduler.Priority.LOW,
            () -> lastStats = FrameTimeTracker.compute(snapshot, budget));
    }

    private long targetBudgetNanos() {
        int fps = MinecraftClient.getInstance().options.getMaxFps().getValue();
        if (fps <= 0) {
            return 16_666_666L;
        }
        return 1_000_000_000L / fps;
    }

    // ===== Геттеры для F3 / AdaptiveEngine =====

    public FrameTimeTracker.FrameStats stats() {
        return lastStats;
    }

    public double pacingScore() {
        return lastStats.pacingScore;
    }

    public int currentFps() {
        return MinecraftClient.getInstance().getCurrentFps();
    }

    public double cpuUsagePercent() {
        // OperatingSystemMXBean — доля процессорного времени JVM-процесса.
        try {
            java.lang.management.OperatingSystemMXBean os =
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                double cpu = sunOs.getCpuLoad();
                if (cpu >= 0) {
                    return cpu * 100.0;
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public long heapUsedBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public long heapMaxBytes() {
        return Runtime.getRuntime().maxMemory();
    }

    public long gcTimeMsTotal() {
        return gcMonitor.totalGcTimeMs();
    }

    public long gcCountTotal() {
        return gcMonitor.totalGcCount();
    }

    public long gcTimeLastMs() {
        return gcMonitor.lastGcTimeMs();
    }

    public double gpuUtilizationPercent() {
        try {
            return MinecraftClient.getInstance().getGpuUtilizationPercentage();
        } catch (Throwable t) {
            return -1;
        }
    }

    public void setEntityCount(int count) {
        this.entityCount = count;
    }

    public int entityCount() {
        return entityCount;
    }

    public void setParticleCount(int count) {
        this.particleCount = count;
    }

    public int particleCount() {
        return particleCount;
    }

    public void setLoadedChunkCount(int count) {
        this.loadedChunkCount = count;
    }

    public int loadedChunkCount() {
        return loadedChunkCount;
    }

    public void addBlockChange(int sections) {
        blockChangesThisFrame++;
        changedSectionsThisFrame += sections;
    }

    public int blockChangesThisFrame() {
        return blockChangesThisFrame;
    }

    public int changedSectionsThisFrame() {
        return changedSectionsThisFrame;
    }

    public void addChunkUpload() {
        chunkUploadsThisFrame++;
    }

    public int chunkUploadsThisFrame() {
        return chunkUploadsThisFrame;
    }

    public void setParticlePacketsQueued(int count) {
        this.particlePacketsQueued = count;
    }

    public int particlePacketsQueued() {
        return particlePacketsQueued;
    }

    public void addParticlePacketProcessed() {
        particlePacketsProcessed++;
    }

    public int particlePacketsProcessed() {
        return particlePacketsProcessed;
    }

    /** Адаптивно подстроенные значения (или -1, если адаптация неактивна). */
    public int effectiveParticleCap(int configCap) {
        int adaptive = AdaptiveEngine.state().particleCap;
        return adaptive > 0 ? Math.min(configCap, adaptive) : configCap;
    }

    public int effectiveChunkUploadBatch(int configBatch) {
        int adaptive = AdaptiveEngine.state().chunkUploadBatch;
        return adaptive > 0 ? adaptive : configBatch;
    }
}
