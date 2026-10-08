package com.myoptimize.ultra.gui;

import com.myoptimize.ultra.adaptive.AdaptiveEngine;
import com.myoptimize.ultra.adaptive.GpuInfo;
import com.myoptimize.ultra.cache.CacheManager;
import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.config.OptimizationProfile;
import com.myoptimize.ultra.memory.MemoryAdvisor;
import com.myoptimize.ultra.profiler.FrameTimeTracker;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import com.myoptimize.ultra.scheduler.UltraFrameScheduler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.client.font.TextRenderer;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Стилизованный F3-оверлей MyOptimize Ultra.
 *
 * Что рисуем:
 *  - полупрозрачные панели с акцентной рамкой за ванильным левым/правым текстом;
 *  - под левым текстом — наш дашборд: FPS, frametime (средний/p99/1% low),
 *    график frametime за 120 кадров с целевой линией, RAM-шкала,
 *    счётчики сущностей/частиц/чанков, "Performance Killer", адаптивные лимиты;
 *  - в dev mode — CPU/GPU/GC/аллокации/планировщик/кэши/топ-3 категорий.
 *
 * Весь оверлей — это F3 (debug-режим), поэтому несколько строковых конкатенаций
 * на кадр допустимы; тяжёлые вычисления вынесены в профилировщик.
 */
public final class F3OverlayRenderer {

    // Палитра (ARGB)
    private static final int PANEL_BG = 0xA0101418;
    private static final int PANEL_BORDER = 0x6035C2DE;
    private static final int HEADER_BG = 0xC0142230;
    private static final int ACCENT = 0xFF35C2DE;
    private static final int TEXT = 0xFFE8ECEF;
    private static final int DIM = 0xFF9AA3AD;
    private static final int GOOD = 0xFF4FC978;
    private static final int WARN = 0xFFE0B73C;
    private static final int BAD = 0xFFE0483C;
    private static final int BAR_GOOD = 0xFF3FBF6F;
    private static final int BAR_WARN = 0xFFE0B73C;
    private static final int BAR_BAD = 0xFFE0483C;
    private static final int TARGET_LINE = 0x70FFFFFF;

    private static final int DASH_WIDTH = 252;
    private static final int GRAPH_FRAMES = 120;
    private static final int GRAPH_HEIGHT = 22;
    private static final long[] GRAPH_BUFFER = new long[GRAPH_FRAMES]; // zero-alloc

    private F3OverlayRenderer() {
    }

    // ===== Панели за ванильным текстом =====

    public static void drawPanelBehindLeftText(DrawContext context, DebugHud hud) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        List<String> lines = ((com.myoptimize.ultra.mixin.DebugHudAccessor) hud).myoptimize$getLeftText();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getWidth(line));
        }
        drawPanel(context, 2, 1, width + 6, lines.size() * 10 + 3);
    }

    public static void drawPanelBehindRightText(DrawContext context, DebugHud hud) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        List<String> lines = ((com.myoptimize.ultra.mixin.DebugHudAccessor) hud).myoptimize$getRightText();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getWidth(line));
        }
        int x = client.getWindow().getScaledWidth() - width - 6;
        drawPanel(context, x, 1, width + 6, lines.size() * 10 + 3);
    }

    private static void drawPanel(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, PANEL_BG);
        context.drawBorder(x, y, w, h, PANEL_BORDER);
    }

    // ===== Дашборд под левым текстом =====

    public static void drawDashboard(DrawContext context, DebugHud hud) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        PerformanceProfiler profiler = PerformanceProfiler.get();
        ModConfig cfg = ConfigManager.config();

        List<String> leftText = ((com.myoptimize.ultra.mixin.DebugHudAccessor) hud).myoptimize$getLeftText();
        int topY = 4 + (leftText != null ? leftText.size() : 0) * 10 + 6;

        int x = 4;
        int w = DASH_WIDTH;
        int y = topY;

        // --- Заголовок ---
        String profileName = OptimizationProfile.byName(cfg.profile).displayName();
        context.fill(x, y, x + w, y + 16, HEADER_BG);
        context.drawBorder(x, y, w, 16, PANEL_BORDER);
        context.drawTextWithShadow(font, "MyOptimize Ultra", x + 5, y + 4, ACCENT);
        String profile = "[" + profileName + "]";
        context.drawTextWithShadow(font, profile, x + w - font.getWidth(profile) - 5, y + 4, TEXT);
        y += 20;

        // --- FPS / frametime ---
        FrameTimeTracker.FrameStats stats = profiler.stats();
        int fps = profiler.currentFps();
        int fpsColor = fps >= 60 ? GOOD : (fps >= 30 ? WARN : BAD);
        double targetMs = targetFrameMs(client);
        int ftColor = stats.averageMs <= targetMs ? GOOD : (stats.averageMs <= targetMs * 1.5 ? WARN : BAD);
        line(context, font, x, y, String.format(Locale.ROOT,
            "FPS %d  |  FT %.1f ms (p99 %.1f, 1%% low %.1f)",
            fps, stats.averageMs, stats.p99Ms, stats.p1LowMs),
            fpsColor, ftColor, "FT");
        y += 12;

        // --- График frametime ---
        drawFrametimeGraph(context, profiler, x, y, w, client);
        y += GRAPH_HEIGHT + 12;

        // --- RAM ---
        long used = profiler.heapUsedBytes();
        long max = profiler.heapMaxBytes();
        double ramFrac = max > 0 ? used / (double) max : 0;
        int ramColor = ramFrac < 0.7 ? GOOD : (ramFrac < 0.85 ? WARN : BAD);
        line(context, font, x, y, String.format(Locale.ROOT, "RAM %s / %s",
            MemoryAdvisor.describe(used), MemoryAdvisor.describe(max)), ramColor);
        drawBar(context, x, y + 10, w, 4, ramFrac, ramColor);
        y += 20;

        // --- Счётчики ---
        line(context, font, x, y, String.format(Locale.ROOT, "Entities %d  |  Particles %d  |  Chunks %d",
            profiler.entityCount(), profiler.particleCount(), profiler.loadedChunkCount()), TEXT);
        y += 12;
        line(context, font, x, y, String.format(Locale.ROOT, "Chunk uploads %d  |  Block changes %d (%d sections)",
            profiler.chunkUploadsThisFrame(), profiler.blockChangesThisFrame(), profiler.changedSectionsThisFrame()), DIM);
        y += 12;

        // --- Performance Killer ---
        if (cfg.showKillerFinder) {
            KillerFinder.TopKiller killer = profiler.killerFinder().topKiller();
            int killerColor = killer.share > 0.5 ? BAD : (killer.share > 0.25 ? WARN : GOOD);
            line(context, font, x, y, String.format(Locale.ROOT, "Killer: %s — %.0f%% (%.2f ms/frame)",
                killer.category.displayName(), killer.share * 100, killer.millis), killerColor);
            y += 12;
        }

        // --- Адаптивные лимиты ---
        AdaptiveEngine.AdaptiveState adaptive = AdaptiveEngine.state();
        if (adaptive.particleCap > 0 && adaptive.particleCap != cfg.particleMaxCount
            || adaptive.chunkUploadBatch > 0 && adaptive.chunkUploadBatch != cfg.chunkUploadBatchPerFrame) {
            line(context, font, x, y, String.format(Locale.ROOT, "Adaptive: particles<=%d, uploads<=%d",
                adaptive.particleCap < 0 ? cfg.particleMaxCount : adaptive.particleCap,
                adaptive.chunkUploadBatch < 0 ? cfg.chunkUploadBatchPerFrame : adaptive.chunkUploadBatch), ACCENT);
            y += 12;
        }

        // --- Dev mode ---
        if (cfg.devMode) {
            y = drawDevSection(context, font, x, y, w, profiler, client);
        }
    }

    private static int drawDevSection(DrawContext context, TextRenderer font, int x, int y, int w,
                                      PerformanceProfiler profiler, MinecraftClient client) {
        context.fill(x, y, x + w, y + 14, HEADER_BG);
        context.drawTextWithShadow(font, "DEV — diagnostics", x + 5, y + 3, ACCENT);
        y += 18;

        double cpu = profiler.cpuUsagePercent();
        line(context, font, x, y, String.format(Locale.ROOT, "CPU %.0f%%  |  GPU %.0f%%",
            Math.max(0, cpu), Math.max(0, profiler.gpuUtilizationPercent())), TEXT);
        y += 12;

        line(context, font, x, y, String.format(Locale.ROOT, "GC: %d collections, %d ms total, last %d ms",
            profiler.gcCountTotal(), profiler.gcTimeMsTotal(), profiler.gcTimeLastMs()), DIM);
        y += 12;

        var alloc = profiler.allocationTracker();
        if (alloc != null && alloc.isAvailable()) {
            line(context, font, x, y, String.format(Locale.ROOT, "Alloc: render %.1f MB/s, server %.1f MB/s",
                alloc.renderMbPerSecond(), alloc.serverMbPerSecond()), DIM);
            y += 12;
        }

        UltraFrameScheduler scheduler = com.myoptimize.ultra.MyOptimizeUltra.scheduler();
        if (scheduler != null) {
            line(context, font, x, y, String.format(Locale.ROOT,
                "Scheduler: %d tasks, avg %.3f ms, budget %.1f ms, deferred %d",
                scheduler.pendingTasks(), scheduler.averageTaskMs(),
                scheduler.frameBudgetNanos() / 1_000_000.0, scheduler.tasksDeferred()), DIM);
            y += 12;
        }

        line(context, font, x, y, String.format(Locale.ROOT, "Caches: %s / %s",
            MemoryAdvisor.describe(CacheManager.totalWeight()), MemoryAdvisor.describe(CacheManager.budgetBytes())), DIM);
        y += 12;

        line(context, font, x, y, String.format(Locale.ROOT, "Net: %d particle packets queued (%d processed)",
            profiler.particlePacketsQueued(), profiler.particlePacketsProcessed()), DIM);
        y += 12;

        List<Map.Entry<KillerFinder.Category, Double>> top = profiler.killerFinder().sortedCategories();
        int shown = 0;
        for (Map.Entry<KillerFinder.Category, Double> entry : top) {
            if (shown++ >= 3) {
                break;
            }
            line(context, font, x, y, String.format(Locale.ROOT, "  %s: %.2f ms",
                entry.getKey().displayName(), entry.getValue()), DIM);
            y += 11;
        }

        // GPU info
        if (GpuInfo.isCollected()) {
            String gpu = GpuInfo.renderer();
            if (gpu.length() > 34) {
                gpu = gpu.substring(0, 34) + "…";
            }
            line(context, font, x, y, "GPU: " + gpu, DIM);
            y += 11;
        }
        return y;
    }

    // ===== График frametime =====

    private static void drawFrametimeGraph(DrawContext context, PerformanceProfiler profiler,
                                            int x, int y, int w, MinecraftClient client) {
        double targetMs = targetFrameMs(client);
        int n = profiler.frameTimes().snapshotInto(GRAPH_BUFFER);

        // рамка графика
        context.fill(x, y, x + w, y + GRAPH_HEIGHT + 2, 0x80000000);
        context.drawBorder(x, y, w, GRAPH_HEIGHT + 2, PANEL_BORDER);

        if (n == 0) {
            return;
        }
        // шкала: верх графика = 2× целевое время кадра
        double scaleMax = Math.max(targetMs * 2.0, 1.0);
        int barAreaW = w - 4;
        int step = Math.max(1, barAreaW / n);
        int innerX = x + 2;
        int bottom = y + GRAPH_HEIGHT;

        for (int i = 0; i < n; i++) {
            double ms = GRAPH_BUFFER[i] / 1_000_000.0;
            int h = (int) Math.min(GRAPH_HEIGHT, Math.round(ms / scaleMax * GRAPH_HEIGHT));
            int color = ms <= targetMs * 0.9 ? BAR_GOOD : (ms <= targetMs * 1.5 ? BAR_WARN : BAR_BAD);
            int bx = innerX + i * step;
            context.fill(bx, bottom - h, bx + Math.max(1, step - 1), bottom, color);
        }

        // целевая линия
        int targetY = bottom - (int) Math.round(targetMs / scaleMax * GRAPH_HEIGHT);
        context.fill(innerX, targetY, innerX + barAreaW, targetY + 1, TARGET_LINE);
    }

    // ===== Вспомогательное =====

    private static void line(DrawContext context, TextRenderer font, int x, int y, String text, int color) {
        context.drawTextWithShadow(font, text, x, y, color);
    }

    /** Первая "метка" рисуется акцентным цветом, остальное — основным (как "FPS 120" | "FT …"). */
    private static void line(DrawContext context, TextRenderer font, int x, int y, String text, int color, int accentColor, String accentMark) {
        int idx = text.indexOf(accentMark);
        if (idx > 0) {
            String head = text.substring(0, idx);
            String tail = text.substring(idx);
            context.drawTextWithShadow(font, head, x, y, color);
            context.drawTextWithShadow(font, tail, x + font.getWidth(head), y, accentColor);
        } else {
            context.drawTextWithShadow(font, text, x, y, color);
        }
    }

    private static void drawBar(DrawContext context, int x, int y, int w, int h, double fraction, int color) {
        context.fill(x, y, x + w, y + h, 0x60000000);
        int fillW = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (fillW > 0) {
            context.fill(x, y, x + fillW, y + h, color);
        }
    }

    private static double targetFrameMs(MinecraftClient client) {
        int fps = client.options.getMaxFps().getValue();
        return fps <= 0 ? 16.667 : 1000.0 / fps;
    }
}
