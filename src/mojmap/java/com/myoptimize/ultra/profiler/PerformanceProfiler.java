package com.myoptimize.ultra.profiler;

import com.myoptimize.ultra.adaptive.AdaptiveEngine;
import com.myoptimize.ultra.memory.GcMonitor;
import com.myoptimize.ultra.scheduler.UltraFrameScheduler;
import net.minecraft.client.Minecraft;

/**
 * Центральный профилировщик — вариант для Minecraft 26.x (mojmap).
 * Отличия от yarn-варианта: getFps/getGpuUtilization, options.framerateLimit().
 */
public final class PerformanceProfiler {

    private static final PerformanceProfiler INSTANCE = new PerformanceProfiler();

    private final FrameTimeTracker frameTimes = new FrameTimeTracker(600);
    private final KillerFinder killerFinder = new KillerFinder();
    private final GcMonitor gcMonitor = new GcMonitor();

    private volatile FrameTimeTracker.FrameStats stats = new FrameTimeTracker.FrameStats();
    private volatile int currentFps;
    private volatile double cpuUsagePercent = -1;
    private volatile long gpuUtilizationPercent = -1;

    private volatile int entityCount;
    private volatile int particleCount;
    private volatile int loadedChunkCount;
    private volatile int blockChangesThisFrame;
    private volatile int changedSectionsThisFrame;
    private volatile int chunkUploadsThisFrame;
    private volatile int particlePacketsQueued;
    private volatile int particlePacketsProcessed;
    private volatile long lastFrameNanos;
    private volatile long frameStartNanos;

    private volatile AllocationTracker allocationTracker;

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

    /** Вызывается на границе кадра (render thread). */
    public void onFrameStart() {
        frameStartNanos = System.nanoTime();
        killerFinder.beginFrame();
        blockChangesThisFrame = 0;
        changedSectionsThisFrame = 0;
        chunkUploadsThisFrame = 0;
    }

    /** Завершить кадр: записать frametime. Вызывается из MixinGameRenderer (render thread). */
    public void recordFrame(long frameNanos) {
        frameTimes.recordFrame(frameNanos);
        lastFrameNanos = frameNanos;
        Minecraft client = Minecraft.getInstance();
        currentFps = client.getFps();
        gpuUtilizationPercent = Math.max(0, (long) client.getGpuUtilization());
        int fps = client.options.framerateLimit().get();
        if (fps > 0) {
            cpuUsagePercent = Math.min(100.0, lastFrameNanos / 1_000_000.0 / (1000.0 / fps) * 100.0);
        }
    }

    /**
     * Запросить расчёт статистики frametime на фоновом потоке планировщика.
     * Тяжёлая часть (сортировка 600 сэмплов) не должна попадать в render thread.
     */
    public void requestStatsUpdate(UltraFrameScheduler scheduler) {
        long[] snapshot = frameTimes.snapshot();
        Minecraft client = Minecraft.getInstance();
        int fps = client.options.framerateLimit().get();
        long budget = fps > 0 ? 1_000_000_000L / fps : 16_666_666L;
        scheduler.schedule("frametime-stats", UltraFrameScheduler.Priority.BACKGROUND, () -> {
            stats = FrameTimeTracker.compute(snapshot, budget);
        });
    }

    public FrameTimeTracker.FrameStats stats() {
        return stats;
    }

    public double pacingScore() {
        return stats.pacingScore;
    }

    public int currentFps() {
        return currentFps;
    }

    /** Приблизительная загрузка CPU render thread, % (0..100). -1 если неизвестно. */
    public double cpuUsagePercent() {
        return cpuUsagePercent;
    }

    public long heapUsedBytes() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
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

    /** Загрузка GPU, % (0..100). -1 если драйвер не отдаёт. */
    public long gpuUtilizationPercent() {
        return gpuUtilizationPercent;
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

    /** Адаптивный потолок частиц: минимум из конфига и лимита адаптации. */
    public int effectiveParticleCap(int configCap) {
        int adaptive = AdaptiveEngine.state().particleCap;
        return adaptive > 0 ? Math.min(configCap, adaptive) : configCap;
    }

    /** Адаптивный лимит аплоадов чанков за кадр: 0 = без лимита. */
    public int effectiveChunkUploadBatch(int configBatch) {
        int adaptive = AdaptiveEngine.state().chunkUploadBatch;
        return adaptive > 0 ? Math.min(configBatch, adaptive) : configBatch;
    }
}
