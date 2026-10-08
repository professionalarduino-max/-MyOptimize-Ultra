package com.myoptimize.ultra.startup;

import com.myoptimize.ultra.MyOptimize;
import com.myoptimize.ultra.memory.MemoryAdvisor;
import com.myoptimize.ultra.scheduler.UltraFrameScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Оптимизация запуска.
 *
 * Честный подход: мы не можем "распараллелить загрузку ресурсов ваниллы"
 * (она и так асинхронная, и лезть туда опасно). Что мы реально делаем:
 *  1. Замеряем фазы нашей собственной инициализации и логируем — чтобы
 *     startup- регрессии были видны.
 *  2. Инициализируем независимые подсистемы параллельно (где это безопасно).
 *  3. "Прогреваем" наши структуры (пулы, кэши) фоновыми задачами через
 *     UltraFrameScheduler — чтобы первые кадры в мире не платили за прогрев.
 *  4. Логируем рекомендованные JVM-флаги под конкретную машину.
 */
public final class StartupOptimizer {

    private static final List<String> PHASE_LOG = new ArrayList<>();
    private static long phaseStart;
    private static String currentPhase = "";

    private StartupOptimizer() {
    }

    public static void beginPhase(String name) {
        endPhase();
        currentPhase = name;
        phaseStart = System.nanoTime();
    }

    public static void endPhase() {
        if (!currentPhase.isEmpty()) {
            long elapsedMs = (System.nanoTime() - phaseStart) / 1_000_000;
            String line = String.format(java.util.Locale.ROOT, "[startup] %-28s %6d ms", currentPhase, elapsedMs);
            PHASE_LOG.add(line);
            MyOptimize.LOGGER.info(line);
            currentPhase = "";
        }
    }

    /** Параллельное выполнение независимых задач инициализации. */
    public static void parallel(String name, List<Runnable> tasks) {
        beginPhase(name);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Runnable task : tasks) {
            futures.add(CompletableFuture.runAsync(task));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        endPhase();
    }

    /** Прогрев структур на фоновых воркерах (вызывать после создания планировщика). */
    public static void prewarm(UltraFrameScheduler scheduler) {
        scheduler.schedule("prewarm-pools", UltraFrameScheduler.Priority.BACKGROUND, () -> {
            // Прогреваем часто используемые структуры, чтобы первые кадры не аллоцировали.
            for (int i = 0; i < 256; i++) {
                com.myoptimize.ultra.util.CompactLongSet warmSet =
                    new com.myoptimize.ultra.util.CompactLongSet(64);
                for (int j = 0; j < 64; j++) {
                    warmSet.add(j * 2654435761L);
                }
                warmSet.clear();
            }
        });
        scheduler.schedule("prewarm-logs", UltraFrameScheduler.Priority.BACKGROUND, () -> {
            long totalRam = Runtime.getRuntime().maxMemory();
            MyOptimize.LOGGER.info("Рекомендуемые JVM-флаги для вашей машины: {}",
                String.join(" ", MemoryAdvisor.recommendedJvmFlags(totalRam)));
        });
    }

    public static List<String> phaseLog() {
        endPhase();
        return new ArrayList<>(PHASE_LOG);
    }

    public static void logSummary() {
        endPhase();
        MyOptimize.LOGGER.info("=== MyOptimize Ultra: итоги запуска ===");
        for (String line : PHASE_LOG) {
            MyOptimize.LOGGER.info(line);
        }
    }
}
