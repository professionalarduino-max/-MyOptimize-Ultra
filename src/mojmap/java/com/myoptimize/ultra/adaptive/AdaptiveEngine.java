package com.myoptimize.ultra.adaptive;

import com.myoptimize.ultra.MyOptimize;
import com.myoptimize.ultra.cache.CacheManager;
import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.Minecraft;

/**
 * MyOptimize Adaptive Engine — вариант для Minecraft 26.x (mojmap).
 *
 * Раз в секунду снимает показания (FPS, pacing score, 1% low, сущности, heap, GC)
 * и подстраивает ТОЛЬКО наши ручки: бюджет кэшей, лимит аплоадов чанков за кадр,
 * cap частиц. Ничего игрового не трогает.
 */
public final class AdaptiveEngine {

    /** Текущие адаптивные лимиты (-1 = использовать конфиг). */
    public static final class AdaptiveState {
        public volatile int particleCap = -1;
        public volatile int chunkUploadBatch = -1;
        public volatile long cacheBudgetBytes = -1;
        public volatile double schedulerFrameBudgetMs = -1; // -1 = дефолт планировщика
    }

    private static final AdaptiveState STATE = new AdaptiveState();
    private static Thread thread;
    private static volatile boolean running;

    private AdaptiveEngine() {
    }

    public static AdaptiveState state() {
        return STATE;
    }

    public static void start() {
        if (running) {
            return;
        }
        if (!ConfigManager.config().adaptiveEngine) {
            return;
        }
        running = true;
        thread = new Thread(AdaptiveEngine::loop, "MyOptimize-Adaptive");
        thread.setDaemon(true);
        thread.start();
        MyOptimize.LOGGER.info("Adaptive Engine запущен (26.x, 1 Гц)");
    }

    public static void stop() {
        running = false;
    }

    private static void loop() {
        while (running) {
            try {
                tick();
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                MyOptimize.LOGGER.warn("AdaptiveEngine tick failed", t);
            }
        }
    }

    private static void tick() {
        PerformanceProfiler profiler = PerformanceProfiler.get();
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }
        double pacing = profiler.pacingScore();
        long usedHeap = profiler.heapUsedBytes();
        long maxHeap = profiler.heapMaxBytes();
        double heapFrac = maxHeap > 0 ? usedHeap / (double) maxHeap : 0;
        long gcMs = profiler.gcTimeMsTotal();
        int entities = profiler.entityCount();
        int targetFps = client.options.framerateLimit().get();

        // 1. Pacing просел — режем аплоады чанков.
        if (pacing > 0 && pacing < 0.90 && targetFps >= 60) {
            STATE.chunkUploadBatch = Math.max(8, STATE.chunkUploadBatch > 0
                ? STATE.chunkUploadBatch / 2 : ConfigManager.config().chunkUploadBatchPerFrame / 2);
        } else if (STATE.chunkUploadBatch > 0 && pacing > 0.97) {
            STATE.chunkUploadBatch = -1; // вернуться к конфигу
        }

        // 2. Heap/GC давят — уменьшаем бюджет кэшей.
        if (heapFrac > 0.85 || gcMs > 200) {
            long budget = CacheManager.budgetBytes();
            STATE.cacheBudgetBytes = Math.max(8L << 20, budget / 2);
            CacheManager.setBudgetBytes(STATE.cacheBudgetBytes);
        } else if (STATE.cacheBudgetBytes > 0 && heapFrac < 0.6 && gcMs < 50) {
            CacheManager.setBudgetBytes(ConfigManager.config().cacheBudgetMb << 20);
            STATE.cacheBudgetBytes = -1;
        }

        // 3. Сущностей много — режем cap частиц.
        if (entities > 600) {
            STATE.particleCap = Math.max(2000, ConfigManager.config().particleMaxCount * 2 / 3);
        } else if (STATE.particleCap > 0 && entities < 300) {
            STATE.particleCap = -1;
        }

        // 4. Бюджет планировщика под целевой FPS.
        if (targetFps >= 120) {
            STATE.schedulerFrameBudgetMs = 1.0;
        } else if (targetFps >= 60) {
            STATE.schedulerFrameBudgetMs = 2.0;
        } else {
            STATE.schedulerFrameBudgetMs = 4.0;
        }
    }
}
