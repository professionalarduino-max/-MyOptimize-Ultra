package com.myoptimize.ultra.adaptive;

import com.myoptimize.ultra.MyOptimize;
import com.myoptimize.ultra.cache.CacheManager;
import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.memory.MemoryAdvisor;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.MinecraftClient;

/**
 * MyOptimize Adaptive Engine.
 *
 * Раз в секунду снимает показания профилировщика и подстраивает "ручки",
 * которые безопасно крутить на лету:
 *  - бюджет кэшей (CacheManager) — режем при давлении на память/GC;
 *  - лимит аплоадов чанков за кадр — уменьшаем при низком pacing score;
 *  - лимит частиц — уменьшаем при большом количестве сущностей/частиц;
 *  - frame budget планировщика — подстраиваем под целевой FPS.
 *
 * НИКОГДА не меняет игровые механики и не ломает совместимость: крутятся
 * только производные ручки наших собственных подсистем.
 *
 * Потокобезопасность: все поля — volatile; конфиг читается через ConfigManager.
 */
public final class AdaptiveEngine {

    /** Текущие адаптивные множители/значения, читаются фичами на render thread. */
    public static final class AdaptiveState {
        public volatile int particleCap = -1;        // -1 = использовать конфиг
        public volatile int chunkUploadBatch = -1;   // -1 = использовать конфиг
        public volatile long cacheBudgetBytes = -1;  // -1 = использовать конфиг
        public volatile double schedulerFrameBudgetMs = -1; // -1 = дефолт планировщика
    }

    private static final AdaptiveState STATE = new AdaptiveState();
    private static volatile boolean running = false;
    private static Thread thread;

    private AdaptiveEngine() {
    }

    public static AdaptiveState state() {
        return STATE;
    }

    public static void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(AdaptiveEngine::loop, "MyOptimize-Adaptive");
        thread.setDaemon(true);
        thread.start();
        MyOptimize.LOGGER.info("Adaptive Engine запущен");
    }

    public static void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void loop() {
        // Короткая пауза перед первым тиком, чтобы не мешать старту игры.
        sleep(2000);
        while (running) {
            try {
                tick();
            } catch (Throwable t) {
                MyOptimize.LOGGER.warn("Adaptive Engine: ошибка тика: {}", t.toString());
            }
            sleep(1000);
        }
    }

    private static void tick() {
        ModConfig cfg = ConfigManager.config();
        if (!cfg.adaptiveEngine) {
            return;
        }
        PerformanceProfiler profiler = PerformanceProfiler.get();
        MinecraftClient client = MinecraftClient.getInstance();

        // --- Память: если GC давит или heap заполнен — режем кэши. ---
        long maxHeap = Runtime.getRuntime().maxMemory();
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double heapUsage = used / (double) maxHeap;
        long gcTimeMs = profiler.gcTimeMsTotal();

        long baseBudget = cfg.cacheBudgetMb * 1024L * 1024L;
        if (heapUsage > 0.85 || gcTimeMs > 200) {
            STATE.cacheBudgetBytes = Math.max(8L * 1024 * 1024, (long) (baseBudget * 0.5));
        } else if (heapUsage > 0.70 || gcTimeMs > 100) {
            STATE.cacheBudgetBytes = (long) (baseBudget * 0.75);
        } else {
            STATE.cacheBudgetBytes = baseBudget;
        }
        CacheManager.setBudgetBytes(STATE.cacheBudgetBytes);

        // --- Frametime: если pacing плохой — уменьшаем аплоады чанков за кадр. ---
        double pacing = profiler.pacingScore();
        int baseBatch = cfg.chunkUploadBatchPerFrame;
        if (baseBatch > 0) {
            if (pacing < 0.90) {
                STATE.chunkUploadBatch = Math.max(4, baseBatch / 2);
            } else if (pacing < 0.97) {
                STATE.chunkUploadBatch = Math.max(8, (int) (baseBatch * 0.75));
            } else {
                STATE.chunkUploadBatch = baseBatch;
            }
        }

        // --- Частицы: если сущностей много — режем cap. ---
        int entities = client.world != null ? profiler.entityCount() : 0;
        int baseCap = cfg.particleMaxCount;
        if (entities > 600) {
            STATE.particleCap = Math.max(1500, baseCap / 2);
        } else if (entities > 300) {
            STATE.particleCap = Math.max(2500, (int) (baseCap * 0.75));
        } else {
            STATE.particleCap = baseCap;
        }

        // --- Планировщик: держим фоновую работу в пределах 2 мс на кадр при 60 FPS,
        //     и уменьшаем при высоком целевом FPS. ---
        int targetFps = client.options.getMaxFps().getValue();
        if (targetFps >= 120) {
            STATE.schedulerFrameBudgetMs = 1.0;
        } else if (targetFps >= 60) {
            STATE.schedulerFrameBudgetMs = 2.0;
        } else {
            STATE.schedulerFrameBudgetMs = 4.0;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
