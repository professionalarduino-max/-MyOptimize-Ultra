package com.myoptimize.ultra.cache;

import java.util.ArrayList;
import java.util.List;

/**
 * Центральный реестр всех кэшей мода.
 *
 * Зачем: кэш без контроля разрастается и сам становится причиной GC-пауз.
 * CacheManager:
 *  - знает все кэши и их суммарный вес;
 *  - периодически (через UltraFrameScheduler, вне render thread) подрезает
 *    LRU-хвосты, если суммарный вес превышает бюджет;
 *  - умеет резать budgets при давлении на память (сигнал от AdaptiveEngine/GcMonitor).
 *
 * Бюджет по умолчанию берётся из профиля, затем адаптируется под реальную RAM.
 */
public final class CacheManager {

    private static final List<SmartCache<?, ?>> ALL = new ArrayList<>();
    private static volatile long budgetBytes = 96L * 1024 * 1024;

    private CacheManager() {
    }

    public static synchronized <K, V> SmartCache<K, V> register(SmartCache<K, V> cache) {
        ALL.add(cache);
        return cache;
    }

    public static synchronized List<SmartCache<?, ?>> all() {
        return new ArrayList<>(ALL);
    }

    public static void setBudgetBytes(long bytes) {
        budgetBytes = Math.max(8L * 1024 * 1024, bytes);
    }

    public static long budgetBytes() {
        return budgetBytes;
    }

    /** Суммарный вес всех кэшей. */
    public static synchronized long totalWeight() {
        long total = 0;
        for (SmartCache<?, ?> cache : ALL) {
            total += cache.currentWeight();
        }
        return total;
    }

    /**
     * Периодическая подрезка. Выполняется на фоновом воркере планировщика.
     * Возвращает true, если хоть один кэш был подрезан.
     */
    public static synchronized boolean trimToBudget() {
        long total = totalWeight();
        if (total <= budgetBytes) {
            return false;
        }
        // Режем пропорционально: каждый кэш теряет долю своего превышения.
        // Простая и честная стратегия: режем самый большой кэш до среднего веса.
        boolean trimmed = false;
        while (totalWeight() > budgetBytes && !ALL.isEmpty()) {
            SmartCache<?, ?> biggest = null;
            long biggestWeight = 0;
            for (SmartCache<?, ?> cache : ALL) {
                if (cache.currentWeight() > biggestWeight) {
                    biggestWeight = cache.currentWeight();
                    biggest = cache;
                }
            }
            if (biggest == null || biggestWeight == 0) {
                break;
            }
            long target = Math.max(1024, biggestWeight / 2);
            trimmed |= biggest.resize(target);
            if (!trimmed && biggest.currentWeight() <= target) {
                break; // нечего резать
            }
        }
        return trimmed;
    }

    /** Полная очистка всех кэшей (например, при смене мира/ресурс-пака). */
    public static synchronized void clearAll() {
        for (SmartCache<?, ?> cache : ALL) {
            cache.clear();
        }
    }
}
