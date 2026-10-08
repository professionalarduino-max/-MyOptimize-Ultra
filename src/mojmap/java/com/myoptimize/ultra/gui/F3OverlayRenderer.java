package com.myoptimize.ultra.gui;

import com.myoptimize.ultra.MyOptimizeUltra;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Стилизованный F3-оверлей — вариант для Minecraft 26.x (mojmap).
 *
 * В 26.x F3 — это модульная система DebugScreenEntries, а отрисовка идёт через
 * GuiGraphicsExtractor (extractRenderState у DebugScreenOverlay). Поэтому наша
 * панель рисуется внизу слева (положение ванильного текста неизвестно без
 * парсинга списка entries), а «панели за текстом» нет.
 *
 * API GuiGraphicsExtractor: fill / text / outline / guiWidth / guiHeight.
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

    /** Вызывается из MixinDebugScreenOverlay после отрисовки ванильного F3. */
    public static void drawDashboard(GuiGraphicsExtractor gfx) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        PerformanceProfiler profiler = PerformanceProfiler.get();
        ModConfig cfg = ConfigManager.config();

        int w = DASH_WIDTH;
        int x = 4;
        // высота дашборда: считаем по содержимому
        int lines = 6 + (cfg.showKillerFinder ? 1 : 0) + (cfg.devMode ? 10 : 0);
        int h = 16 + 12 + GRAPH_HEIGHT + 12 + 20 + 12 + 12 + 12 + lines * 0; // уточняется ниже
        h = 20 + 12 + (GRAPH_HEIGHT + 12) + 20 + 12 + 12;
        if (cfg.showKillerFinder) {
            h += 12;
        }
        if (cfg.devMode) {
            h += 18 + 12 * 6 + 11 * 3 + 11;
        }
        int y = gfx.guiHeight() - h - 6;

        // Панель
        gfx.fill(x, y, x + w, y + h, PANEL_BG);
        gfx.outline(x, y, w, h, PANEL_BORDER);
        int cy = y + 4;

        // --- Заголовок ---
        String profileName = OptimizationProfile.byName(cfg.profile).displayName();
        gfx.fill(x, cy, x + w, cy + 16, HEADER_BG);
        gfx.outline(x, cy, w, 16, PANEL_BORDER);
        gfx.text(font, "MyOptimize Ultra", x + 5, cy + 4, ACCENT, false);
        String profile = "[" + profileName + "]";
        gfx.text(font, profile, x + w - font.width(profile) - 5, cy + 4, TEXT, false);
        cy += 20;

        // --- FPS / frametime ---
        FrameTimeTracker.FrameStats stats = profiler.stats();
        int fps = profiler.currentFps();
        int fpsColor = fps >= 60 ? GOOD : (fps >= 30 ? WARN : BAD);
        double targetMs = targetFrameMs(client);
        int ftColor = stats.averageMs <= targetMs ? GOOD : (stats.averageMs <= targetMs * 1.5 ? WARN : BAD);
        String fpsLine = String.format(Locale.ROOT, "FPS %d", fps);
        String ftLine = String.format(Locale.ROOT, "FT %.1f ms (p99 %.1f, 1%% low %.1f)",
            stats.averageMs, stats.p99Ms, stats.p1LowMs);
        gfx.text(font, fpsLine + "  |  ", x, cy, fpsColor, false);
        gfx.text(font, ftLine, x + font.width(fpsLine + "  |  "), cy, ftColor, false);
        cy += 12;

        // --- График frametime ---
        drawFrametimeGraph(gfx, profiler, font, x, cy, w, client);
        cy += GRAPH_HEIGHT + 12;

        // --- RAM ---
        long used = profiler.heapUsedBytes();
        long max = profiler.heapMaxBytes();
        double ramFrac = max > 0 ? used / (double) max : 0;
        int ramColor = ramFrac < 0.7 ? GOOD : (ramFrac < 0.85 ? WARN : BAD);
        gfx.text(font, String.format(Locale.ROOT, "RAM %s / %s",
            MemoryAdvisor.describe(used), MemoryAdvisor.describe(max)), x, cy, ramColor, false);
        drawBar(gfx, x, cy + 10, w, 4, ramFrac, ramColor);
        cy += 20;

        // --- Счётчики ---
        gfx.text(font, String.format(Locale.ROOT, "Entities %d  |  Particles %d  |  Chunks %d",
            profiler.entityCount(), profiler.particleCount(), profiler.loadedChunkCount()), x, cy, TEXT, false);
        cy += 12;
        gfx.text(font, String.format(Locale.ROOT, "Chunk uploads %d  |  Block changes %d (%d sections)",
            profiler.chunkUploadsThisFrame(), profiler.blockChangesThisFrame(), profiler.changedSectionsThisFrame()),
            x, cy, DIM, false);
        cy += 12;

        // --- Performance Killer ---
        if (cfg.showKillerFinder) {
            KillerFinder.TopKiller killer = profiler.killerFinder().topKiller();
            int killerColor = killer.share > 0.5 ? BAD : (killer.share > 0.25 ? WARN : GOOD);
            gfx.text(font, String.format(Locale.ROOT, "Killer: %s — %.0f%% (%.2f ms/frame)",
                killer.category.displayName(), killer.share * 100, killer.millis), x, cy, killerColor, false);
            cy += 12;
        }

        // --- Dev mode ---
        if (cfg.devMode) {
            cy = drawDevSection(gfx, font, x, cy, w, profiler, client);
        }
    }

    private static int drawDevSection(GuiGraphicsExtractor gfx, Font font, int x, int y, int w,
                                      PerformanceProfiler profiler, Minecraft client) {
        gfx.fill(x, y, x + w, y + 14, HEADER_BG);
        gfx.text(font, "DEV — diagnostics", x + 5, y + 3, ACCENT, false);
        y += 18;

        double cpu = profiler.cpuUsagePercent();
        gfx.text(font, String.format(Locale.ROOT, "CPU %.0f%%  |  GPU %.0f%%",
            Math.max(0, cpu), Math.max(0, profiler.gpuUtilizationPercent())), x, y, TEXT, false);
        y += 12;

        gfx.text(font, String.format(Locale.ROOT, "GC: %d collections, %d ms total, last %d ms",
            profiler.gcCountTotal(), profiler.gcTimeMsTotal(), profiler.gcTimeLastMs()), x, y, DIM, false);
        y += 12;

        var alloc = profiler.allocationTracker();
        if (alloc != null && alloc.isAvailable()) {
            gfx.text(font, String.format(Locale.ROOT, "Alloc: render %.1f MB/s, server %.1f MB/s",
                alloc.renderMbPerSecond(), alloc.serverMbPerSecond()), x, y, DIM, false);
            y += 12;
        }

        UltraFrameScheduler scheduler = MyOptimizeUltra.scheduler();
        if (scheduler != null) {
            gfx.text(font, String.format(Locale.ROOT,
                "Scheduler: %d tasks, avg %.3f ms, budget %.1f ms, deferred %d",
                scheduler.pendingTasks(), scheduler.averageTaskMs(),
                scheduler.frameBudgetNanos() / 1_000_000.0, scheduler.tasksDeferred()), x, y, DIM, false);
            y += 12;
        }

        gfx.text(font, String.format(Locale.ROOT, "Caches: %s / %s",
            MemoryAdvisor.describe(CacheManager.totalWeight()), MemoryAdvisor.describe(CacheManager.budgetBytes())),
            x, y, DIM, false);
        y += 12;

        gfx.text(font, String.format(Locale.ROOT, "Net: %d particle packets queued (%d processed)",
            profiler.particlePacketsQueued(), profiler.particlePacketsProcessed()), x, y, DIM, false);
        y += 12;

        List<Map.Entry<KillerFinder.Category, Double>> top = profiler.killerFinder().sortedCategories();
        int shown = 0;
        for (Map.Entry<KillerFinder.Category, Double> entry : top) {
            if (shown++ >= 3) {
                break;
            }
            gfx.text(font, String.format(Locale.ROOT, "  %s: %.2f ms",
                entry.getKey().displayName(), entry.getValue()), x, y, DIM, false);
            y += 11;
        }

        if (GpuInfo.isCollected()) {
            String gpu = GpuInfo.renderer();
            if (gpu.length() > 34) {
                gpu = gpu.substring(0, 34) + "…";
            }
            gfx.text(font, "GPU: " + gpu, x, y, DIM, false);
            y += 11;
        }
        return y;
    }

    private static void drawFrametimeGraph(GuiGraphicsExtractor gfx, PerformanceProfiler profiler,
                                            Font font, int x, int y, int w, Minecraft client) {
        double targetMs = targetFrameMs(client);
        int n = profiler.frameTimes().snapshotInto(GRAPH_BUFFER);

        gfx.fill(x, y, x + w, y + GRAPH_HEIGHT + 2, 0x80000000);
        gfx.outline(x, y, w, GRAPH_HEIGHT + 2, PANEL_BORDER);

        if (n == 0) {
            return;
        }
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
            gfx.fill(bx, bottom - h, bx + Math.max(1, step - 1), bottom, color);
        }

        int targetY = bottom - (int) Math.round(targetMs / scaleMax * GRAPH_HEIGHT);
        gfx.fill(innerX, targetY, innerX + barAreaW, targetY + 1, TARGET_LINE);
    }

    private static void drawBar(GuiGraphicsExtractor gfx, int x, int y, int w, int h, double fraction, int color) {
        gfx.fill(x, y, x + w, y + h, 0x60000000);
        int fillW = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (fillW > 0) {
            gfx.fill(x, y, x + fillW, y + h, color);
        }
    }

    private static double targetFrameMs(Minecraft client) {
        int fps = client.options.framerateLimit().get();
        return fps <= 0 ? 16.667 : 1000.0 / fps;
    }
}
