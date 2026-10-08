package com.myoptimize.ultra;

import com.myoptimize.ultra.adaptive.AdaptiveEngine;
import com.myoptimize.ultra.adaptive.GpuInfo;
import com.myoptimize.ultra.cache.CacheManager;
import com.myoptimize.ultra.chunk.ChunkUpdateTracker;
import com.myoptimize.ultra.chunk.ChunkUploadPacer;
import com.myoptimize.ultra.compat.CompatReport;
import com.myoptimize.ultra.compat.CompatibilityGuard;
import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.gui.ConfigScreen;
import com.myoptimize.ultra.gui.GuiOptimizer;
import com.myoptimize.ultra.network.NetworkOptimizer;
import com.myoptimize.ultra.profiler.AllocationTracker;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import com.myoptimize.ultra.render.CullingUtil;
import com.myoptimize.ultra.scheduler.UltraFrameScheduler;
import com.myoptimize.ultra.startup.StartupOptimizer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Точка входа мода (client) — вариант для Minecraft 26.x (mojmap, Java 25).
 *
 * Отличия от yarn-варианта: KeyMapping с Category, Identifier.fromNamespaceAndPath,
 * getConnection/getSingleplayerServer/getFps/getGpuUtilization, framerateLimit(),
 * getEntityCount(), expectedChunks(). NB: для 26.x нет texture-bind оптимизации
 * (RenderSystem без GL-биндов) и нет кэша lightmap (система света переписана).
 */
public class MyOptimizeUltra implements ClientModInitializer {

    private static UltraFrameScheduler scheduler;
    private static KeyMapping openSettingsKey;
    private static long frameCounter;

    @Override
    public void onInitializeClient() {
        StartupOptimizer.beginPhase("config");
        ConfigManager.load();

        StartupOptimizer.beginPhase("compat-guard");
        CompatibilityGuard.scan();

        StartupOptimizer.beginPhase("scheduler+profiler");
        scheduler = new UltraFrameScheduler(Math.max(1, Runtime.getRuntime().availableProcessors() - 2));
        StartupOptimizer.prewarm(scheduler);
        PerformanceProfiler.get().gcMonitor().start();

        StartupOptimizer.beginPhase("keybinds+tick");
        openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.myoptimize-ultra.settings",
            InputConstants.Type.KEYBOARD, // GLFW_KEY_K = 75 (в 26.x lwjgl-glfw убран из зависимостей Mojang)
            75, // GLFW_KEY_K
            KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.consumeClick()) {
                client.setScreenAndShow(new ConfigScreen(null)); // в 26.x текущий экран не выставлен наружу
            }
            NetworkOptimizer.drain(client);
            // Трекер аллокаций создаём один раз, когда понятны оба потока.
            if (client.level != null && PerformanceProfiler.get().allocationTracker() == null) {
                Thread serverThread = client.getSingleplayerServer() != null
                    ? client.getSingleplayerServer().getRunningThread() : null;
                PerformanceProfiler.get().setAllocationTracker(
                    new AllocationTracker(Thread.currentThread(), serverThread));
            }
            AllocationTracker tracker = PerformanceProfiler.get().allocationTracker();
            if (tracker != null) {
                tracker.sample();
            }
        });

        // Кэши, зависящие от ресурсов (шрифты/текстуры), сбрасываем при reload.
        // NB: в 26.x Fabric API ресурсный модуль переписан (resource.v1.ResourceLoader).
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            Identifier.fromNamespaceAndPath(MyOptimize.MOD_ID, "cache-invalidation"),
            new SimpleReloadListener<Void>() {
                @Override
                protected Void prepare(PreparableReloadListener.SharedState state) {
                    return null;
                }

                @Override
                protected void apply(Void prepared, PreparableReloadListener.SharedState state) {
                    GuiOptimizer.clearCaches();
                    CacheManager.clearAll();
                }
            });

        StartupOptimizer.beginPhase("adaptive-engine");
        AdaptiveEngine.start();

        StartupOptimizer.beginPhase("compat-report");
        CompatReport.write();

        StartupOptimizer.endPhase();
        StartupOptimizer.logSummary();
        MyOptimize.LOGGER.info("{} initialized. Открыть настройки: клавиша K.", MyOptimize.MOD_NAME);
    }

    public static UltraFrameScheduler scheduler() {
        return scheduler;
    }

    /** Вызывается из MixinGameRenderer (render thread) в начале кадра. */
    public static void onFrameStart() {
        frameCounter++;
        CullingUtil.frameCounter = frameCounter;
        ChunkUpdateTracker.beginFrame();
        ChunkUploadPacer.onFrameStart();

        PerformanceProfiler profiler = PerformanceProfiler.get();
        profiler.onFrameStart();

        if (!GpuInfo.isCollected()) {
            GpuInfo.collect(); // GL-вызовы допустимы только здесь, на render thread
        }

        if (scheduler != null) {
            scheduler.onFrameStart();
            if (frameCounter % 20 == 0) {
                // Тяжёлый расчёт статистики frametime — на фоновом воркере.
                profiler.requestStatsUpdate(scheduler);
            }
            if (frameCounter % 600 == 0) {
                scheduler.schedule("cache-trim", UltraFrameScheduler.Priority.LOW, CacheManager::trimToBudget);
            }
            double budgetMs = AdaptiveEngine.state().schedulerFrameBudgetMs;
            if (budgetMs > 0) {
                scheduler.setFrameBudgetNanos((long) (budgetMs * 1_000_000L));
            }
        }

        if (frameCounter % 10 == 0) {
            sampleWorldStats();
        }
    }

    /** Вызывается из MixinGameRenderer (render thread) в конце кадра. */
    public static void onFrameEnd() {
        if (scheduler != null) {
            scheduler.onFrameEnd();
        }
    }

    private static void sampleWorldStats() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            PerformanceProfiler.get().setEntityCount(client.level.getEntityCount());
            PerformanceProfiler.get().setLoadedChunkCount(client.levelRenderer.expectedChunks().size());
        }
    }
}
