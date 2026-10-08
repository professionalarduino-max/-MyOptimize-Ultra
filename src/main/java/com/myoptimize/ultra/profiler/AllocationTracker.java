package com.myoptimize.ultra.profiler;

import java.lang.management.ManagementFactory;

/**
 * Трекинг аллокаций по потокам через HotSpot ThreadMXBean
 * (com.sun.management.ThreadMXBean#getThreadAllocatedBytes).
 *
 * Показывает, сколько МБ/с аллоцирует render thread и server thread —
 * главный инструмент поиска источника GC-давления.
 */
public final class AllocationTracker {

    private final long renderThreadId;
    private final long serverThreadId;
    private final com.sun.management.ThreadMXBean bean;

    private long lastSampleNanos;
    private long lastRenderAllocated;
    private long lastServerAllocated;
    private double renderMbPerSecond;
    private double serverMbPerSecond;

    public AllocationTracker(Thread renderThread, Thread serverThread) {
        this.renderThreadId = renderThread != null ? renderThread.getId() : -1;
        this.serverThreadId = serverThread != null ? serverThread.getId() : -1;
        com.sun.management.ThreadMXBean bean = null;
        try {
            java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
            if (base instanceof com.sun.management.ThreadMXBean) {
                com.sun.management.ThreadMXBean sunBean = (com.sun.management.ThreadMXBean) base;
                if (sunBean.isThreadAllocatedMemorySupported()) {
                    sunBean.setThreadAllocatedMemoryEnabled(true);
                    bean = sunBean;
                }
            }
        } catch (Throwable ignored) {
            bean = null;
        }
        this.bean = bean;
    }

    public boolean isAvailable() {
        return bean != null;
    }

    /** Снять показания. Вызывать регулярно (например, каждый тик). */
    public void sample() {
        if (bean == null) {
            return;
        }
        long now = System.nanoTime();
        long renderAllocated = renderThreadId >= 0 ? bean.getThreadAllocatedBytes(renderThreadId) : 0;
        long serverAllocated = serverThreadId >= 0 ? bean.getThreadAllocatedBytes(serverThreadId) : 0;
        if (lastSampleNanos > 0) {
            double dtSeconds = (now - lastSampleNanos) / 1_000_000_000.0;
            if (dtSeconds > 0) {
                renderMbPerSecond = (renderAllocated - lastRenderAllocated) / dtSeconds / (1024.0 * 1024.0);
                serverMbPerSecond = (serverAllocated - lastServerAllocated) / dtSeconds / (1024.0 * 1024.0);
            }
        }
        lastSampleNanos = now;
        lastRenderAllocated = renderAllocated;
        lastServerAllocated = serverAllocated;
    }

    public double renderMbPerSecond() {
        return Math.max(0, renderMbPerSecond);
    }

    public double serverMbPerSecond() {
        return Math.max(0, serverMbPerSecond);
    }

    public long renderAllocatedBytes() {
        return lastRenderAllocated;
    }

    public long serverAllocatedBytes() {
        return lastServerAllocated;
    }
}
