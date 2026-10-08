package com.myoptimize.ultra.adaptive;

import com.myoptimize.ultra.memory.MemoryAdvisor;

/**
 * Автоопределение машины: CPU, RAM, архитектура — всё, что нужно для выбора профиля.
 * Чистый Java, без зависимостей от Minecraft.
 */
public final class SystemInfo {

    public final int cpuCores;
    public final long maxHeapBytes;
    public final String osArch;

    private SystemInfo(int cpuCores, long maxHeapBytes, String osArch) {
        this.cpuCores = cpuCores;
        this.maxHeapBytes = maxHeapBytes;
        this.osArch = osArch;
    }

    public static SystemInfo detect() {
        return new SystemInfo(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory(),
            System.getProperty("os.arch", "unknown"));
    }

    /**
     * Класс машины 0..3: 0 — potato (≤2 ядра или ≤2 ГБ), 1 — low (≤4 ядра или ≤4 ГБ),
     * 2 — средняя, 3 — мощная.
     */
    public int machineClass() {
        if (cpuCores <= 2 || maxHeapBytes <= 2L << 30) {
            return 0;
        }
        if (cpuCores <= 4 || maxHeapBytes <= 4L << 30) {
            return 1;
        }
        if (cpuCores <= 8 || maxHeapBytes <= 8L << 30) {
            return 2;
        }
        return 3;
    }

    public String summary() {
        return String.format("CPU=%d cores (%s), heap=%s",
            cpuCores, osArch, MemoryAdvisor.describe(maxHeapBytes));
    }
}
