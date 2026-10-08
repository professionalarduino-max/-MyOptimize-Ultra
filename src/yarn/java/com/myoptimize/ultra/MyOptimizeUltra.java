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
import com.myoptimize.ultra.world.LightmapCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Точка входа мода (client).
 *
 * Порядок инициализации подобран так, чтобы тяжёлые/опасные вещи включались
 * последними, а диагностика была доступна сразу:
 *  1. конфиг → 2. CompatibilityGuard (решает, что можно включать) →
 *  3. планировщик + профилировщик + GC-монитор → 4. keybind/хуки тика →
 *  5. AdaptiveEngine → 6. prewarm и отчёты.
 *
 * Границы кадра (onFrameStart/onFrameEnd) вызываются из MixinGameRenderer.
 */
public class MyOptimizeUltra implements ClientModInitializer {

    private static UltraFrameScheduler scheduler;
    private static KeyBinding openSettingsKey;
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
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.myoptimize-ultra.settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.misc"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.wasPressed()) {
                client.setScreen(new ConfigScreen(client.currentScreen));
            }
            NetworkOptimizer.drain(client);
            // Трекер аллокаций создаём один раз, когда понятны оба потока.
            if (client.world != null && PerformanceProfiler.get().allocationTracker() == null) {
                Thread serverThread = client.getServer() != null ? client.getServer().getThread() : null;
                PerformanceProfiler.get().setAllocationTracker(
                    new AllocationTracker(Thread.currentThread(), serverThread));
            }
            AllocationTracker tracker = PerformanceProfiler.get().allocationTracker();
            if (tracker != null) {
                tracker.sample();
            }
        });

        // Кэши, зависящие от ресурсов (шрифты/текстуры), сбрасываем при reload.
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    return Identifier.of(MyOptimize.MOD_ID, "cache-invalidation");
                }

                @Override
                public void reload(ResourceManager manager) {
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
        MyOptimize.LOGGER.info("{} initialized. Открыть настройки: клавиша K. F3 — стилизованный оверлей.",
            MyOptimize.MOD_NAME);
    }

    public static UltraFrameScheduler scheduler() {
        return scheduler;
    }

    /** Вызывается из MixinGameRenderer (render thread) в начале кадра. */
    public static void onFrameStart() {
        frameCounter++;
        CullingUtil.frameCounter = frameCounter;
        ChunkUpdateTracker.beginFrame();
        LightmapCache.nextFrame();
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            PerformanceProfiler.get().setEntityCount(client.world.getRegularEntityCount());
            PerformanceProfiler.get().setLoadedChunkCount(client.worldRenderer.getCompletedChunkCount());
        }
    }
}
