package com.myoptimize.ultra.profiler;

import java.util.Arrays;

/**
 * Трекинг frametime: кольцевой буфер на примитивах (без аллокаций в steady state)
 * + расчёт статистики вынесен на фон (compute вызывается из UltraFrameScheduler).
 *
 * Метрики: среднее, максимум, p99, 1% low, 0.1% low и pacing score
 * (доля кадров, уложившихся в целевой бюджет — 1.0 = идеально ровно).
 */
public final class FrameTimeTracker {

    /** Сводка по окну кадров. Все поля — миллисекунды, кроме pacingScore (0..1) и samples. */
    public static final class FrameStats {
        public double averageMs;
        public double maxMs;
        public double p99Ms;
        public double p1LowMs;   // 1% low — худший кадр среди 99% лучших
        public double p01LowMs;  // 0.1% low — ловит единичные микрофризы
        public double pacingScore; // доля кадров в бюджете, 1.0 = идеально
        public int samples;

        public double averageFps() {
            return averageMs <= 0 ? 0 : 1000.0 / averageMs;
        }
    }

    private final long[] samplesNanos;
    private int index;
    private int count;

    public FrameTimeTracker(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive");
        }
        this.samplesNanos = new long[windowSize];
    }

    /** Записать длительность кадра. Вызывается с render thread, O(1), без аллокаций. */
    public void recordFrame(long frameNanos) {
        samplesNanos[index] = frameNanos;
        index = (index + 1) % samplesNanos.length;
        if (count < samplesNanos.length) {
            count++;
        }
    }

    public int sampleCount() {
        return count;
    }

    /**
     * Снимок окна (от старых к новым), пригодный для расчёта на другом потоке.
     * Копия — единственная аллокация, и она O(n) memcpy, без сортировки.
     */
    public long[] snapshot() {
        long[] out = new long[count];
        snapshotInto(out);
        return out;
    }

    /**
     * То же, но в переданный буфер — zero-alloc вариант для F3-графика
     * (буфер переиспользуется между кадрами).
     *
     * @return сколько сэмплов записано.
     */
    public int snapshotInto(long[] reuse) {
        int n = Math.min(count, reuse.length);
        for (int i = 0; i < n; i++) {
            int src = (index - n + i + samplesNanos.length) % samplesNanos.length;
            reuse[i] = samplesNanos[src];
        }
        return n;
    }

    /**
     * Расчёт статистики по снимку. Выполняется на фоновом потоке (сортировка O(n log n)).
     *
     * @param samples         снимок наносекунд (любой порядок)
     * @param pacingBudgetNanos целевой бюджет кадра (например, 1e9 / fpsLimit)
     */
    public static FrameStats compute(long[] samples, long pacingBudgetNanos) {
        FrameStats stats = new FrameStats();
        int n = samples.length;
        stats.samples = n;
        if (n == 0) {
            return stats;
        }
        long[] sorted = samples.clone();
        Arrays.sort(sorted);

        double sum = 0;
        for (long v : sorted) {
            sum += v;
        }
        stats.averageMs = (sum / n) / 1_000_000.0;
        stats.maxMs = sorted[n - 1] / 1_000_000.0;
        stats.p99Ms = sorted[(int) Math.ceil(0.99 * n) - 1] / 1_000_000.0;
        // 1% low: худший кадр среди 99% лучших (индекс с конца окна)
        int p1Rank = Math.max(1, (int) Math.ceil(0.01 * n));
        stats.p1LowMs = sorted[n - p1Rank] / 1_000_000.0;
        // 0.1% low: единичные микрофризы
        int p01Rank = Math.max(1, (int) Math.ceil(0.001 * n));
        stats.p01LowMs = sorted[n - p01Rank] / 1_000_000.0;

        int withinBudget = 0;
        for (long v : sorted) {
            if (v <= pacingBudgetNanos) {
                withinBudget++;
            }
        }
        stats.pacingScore = (double) withinBudget / n;
        return stats;
    }
}
