package com.myoptimize.ultra.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * Советник по памяти: на основе доступной RAM даёт рекомендации по JVM-флагам
 * и рассчитывает безопасный бюджет кэшей мода.
 *
 * Зачем: Minecraft по умолчанию не подстраивается под слабые машины.
 * Мы не можем менять флаги запущенной JVM, но можем (а) подсказать игроку
 * оптимальные флаги при следующем запуске, (б) автоматически ограничить
 * собственное потребление кэшей, чтобы не провоцировать GC.
 */
public final class MemoryAdvisor {

    private MemoryAdvisor() {
    }

    /** Рекомендуемый максимальный heap для Minecraft на машине с totalRam. */
    public static long recommendedMaxHeapBytes(long totalRamBytes) {
        // Эмпирика: MC комфортно живёт с 2–4 ГБ; выше — длиннее GC-паузы.
        long quarter = totalRamBytes / 4;
        long capped = Math.min(quarter, 6L * 1024 * 1024 * 1024); // потолок 6 ГБ
        return Math.max(1L * 1024 * 1024 * 1024, capped);       // пол 1 ГБ
    }

    /**
     * Бюджет кэшей мода (в байтах) как доля от свободного heap.
     * Консервативно: не более 12.5% от max heap, и не более половины того,
     * что сейчас свободно.
     */
    public static long cacheBudgetBytes(long maxHeapBytes, long currentlyFreeBytes) {
        long byHeap = maxHeapBytes / 8;
        long byFree = currentlyFreeBytes / 2;
        return Math.max(8L * 1024 * 1024, Math.min(byHeap, byFree)); // минимум 8 МБ
    }

    /** Набор рекомендуемых JVM-флагов для слабых/средних/сильных машин. */
    public static List<String> recommendedJvmFlags(long totalRamBytes) {
        List<String> flags = new ArrayList<>();
        long heap = recommendedMaxHeapBytes(totalRamBytes);
        flags.add("-Xms" + heap / (1024 * 1024) + "M");
        flags.add("-Xmx" + heap / (1024 * 1024) + "M");
        // G1 — стандарт для MC; целевая пауза 50 мс держит микрофризы короткими.
        flags.add("-XX:+UseG1GC");
        flags.add("-XX:MaxGCPauseMillis=50");
        flags.add("-XX:+UnlockExperimentalVMOptions");
        flags.add("-XX:G1NewSizePercent=20");
        flags.add("-XX:G1MixedGCCountTarget=8");
        return flags;
    }

    public static String describe(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
        if (bytes >= 1024 * 1024) {
            return String.format(java.util.Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024));
        }
        return (bytes / 1024) + " KB";
    }
}
