package com.myoptimize.ultra.gui;

import com.myoptimize.ultra.adaptive.AutoOptimizer;
import com.myoptimize.ultra.adaptive.GpuInfo;
import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.config.OptimizationProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Экран настроек MyOptimize Ultra — вариант для Minecraft 26.x (mojmap).
 *
 * Отличия от yarn-варианта: вместо render(DrawContext,...) — extractRenderState
 * (GuiGraphicsExtractor, int, int, float); CycleButton собирается через
 * new CycleButton.Builder<>(namer, initial).withValues(...).create(...);
 * CommonComponents.GUI_DONE вместо ScreenTexts.DONE.
 */
public class ConfigScreen extends Screen {

    private static final Component TITLE = Component.literal("MyOptimize Ultra");

    private enum Category {
        RENDERING, CHUNKS, ENTITIES, WORLD, MEMORY, CPU, GPU, NETWORK, PARTICLES, TEXTURES, ADVANCED, DIAGNOSTICS
    }

    private record RowLabel(String key, int y, boolean dim) {
    }

    private final Screen parent;
    private final ModConfig cfg;
    private Category category = Category.RENDERING;
    private final List<RowLabel> labels = new ArrayList<>();

    public ConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
        this.cfg = ConfigManager.config();
    }

    @Override
    protected void init() {
        labels.clear();
        int centerX = this.width / 2;

        // DONE (сохранить) — справа вверху, чтобы никогда не пересекаться с опциями.
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            ConfigManager.save();
            onClose();
        }).pos(this.width - 110, 6).size(104, 18).build());

        // --- Профиль + AUTO OPTIMIZE ---
        int profileY = 28;
        addRenderableWidget(new CycleButton.Builder<OptimizationProfile>(
                profile -> Component.literal(profile.displayName()),
                () -> OptimizationProfile.byName(cfg.profile))
            .withValues(List.of(OptimizationProfile.values()))
            .create(centerX - 240, profileY, 170, 20, Component.translatable("myoptimize.screen.profile"),
                (button, value) -> {
                    value.applyTo(cfg);
                    rebuild();
                }));
        addRenderableWidget(Button.builder(Component.translatable("myoptimize.screen.autooptimize"), button -> {
            AutoOptimizer.run();
            rebuild();
        }).pos(centerX - 60, profileY).size(150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("myoptimize.screen.resetdefaults"), button -> {
            OptimizationProfile.BALANCED.applyTo(cfg);
            rebuild();
        }).pos(centerX + 100, profileY).size(140, 20).build());

        // --- Вкладки категорий (2 ряда по 6) ---
        Category[] categories = Category.values();
        int tabW = 78;
        int tabH = 18;
        int gap = 2;
        int rowWidth = categories.length / 2 * (tabW + gap) - gap;
        int tabX0 = centerX - rowWidth / 2;
        for (int i = 0; i < categories.length; i++) {
            Category cat = categories[i];
            int row = i / 6;
            int col = i % 6;
            boolean active = cat == category;
            Component label = Component.translatable(
                "myoptimize.category." + cat.name().toLowerCase(java.util.Locale.ROOT));
            if (active) {
                label = Component.literal("> ").append(label);
            }
            final Category clicked = cat;
            addRenderableWidget(Button.builder(label, button -> {
                category = clicked;
                rebuild();
            }).pos(tabX0 + col * (tabW + gap), 56 + row * (tabH + gap)).size(tabW, tabH).build());
        }

        // --- Опции категории ---
        int y = 104;
        switch (category) {
            case RENDERING -> {
                addBool(y, "myoptimize.opt.undergroundSkySkip", () -> cfg.undergroundSkySkip, v -> cfg.undergroundSkySkip = v);
                y += 22;
                addBool(y, "myoptimize.opt.entityCulling", () -> cfg.entityCulling, v -> cfg.entityCulling = v);
                y += 22;
                addBool(y, "myoptimize.opt.blockEntityCulling", () -> cfg.blockEntityCulling, v -> cfg.blockEntityCulling = v);
                y += 22;
                addCycle(y, "myoptimize.opt.blockEntityCullDistance", () -> cfg.blockEntityCullDistance,
                    v -> cfg.blockEntityCullDistance = v, new Integer[]{48, 64, 80, 96, 128}, v -> Component.literal(String.valueOf(v)));
                y += 22;
                addBool(y, "myoptimize.opt.fontWidthCache", () -> cfg.fontWidthCache, v -> cfg.fontWidthCache = v);
                y += 22;
                addBool(y, "myoptimize.opt.emptyTextSkip", () -> cfg.emptyTextSkip, v -> cfg.emptyTextSkip = v);
            }
            case CHUNKS -> {
                addCycle(y, "myoptimize.opt.chunkUploadBatchPerFrame", () -> cfg.chunkUploadBatchPerFrame,
                    v -> cfg.chunkUploadBatchPerFrame = v, new Integer[]{0, 8, 16, 24, 32, 48, 64},
                    v -> v == 0 ? Component.translatable("myoptimize.value.unlimited") : Component.literal(String.valueOf(v)));
                y += 22;
                addBool(y, "myoptimize.opt.chunkUpdateTracking", () -> cfg.chunkUpdateTracking, v -> cfg.chunkUpdateTracking = v);
            }
            case ENTITIES -> {
                addBool(y, "myoptimize.opt.entityTickThrottling", () -> cfg.entityTickThrottling, v -> cfg.entityTickThrottling = v);
                y += 22;
                addCycle(y, "myoptimize.opt.entityThrottleDistance", () -> cfg.entityThrottleDistance,
                    v -> cfg.entityThrottleDistance = v, new Integer[]{32, 48, 64, 80, 96}, v -> Component.literal(String.valueOf(v)));
                y += 22;
                addCycle(y, "myoptimize.opt.entityThrottleMaxInterval", () -> cfg.entityThrottleMaxInterval,
                    v -> cfg.entityThrottleMaxInterval = v, new Integer[]{5, 10, 15, 20}, v -> Component.literal(String.valueOf(v)));
                y += 22;
                addBool(y, "myoptimize.opt.aggressiveEntityCulling", () -> cfg.aggressiveEntityCulling, v -> cfg.aggressiveEntityCulling = v);
                y += 22;
                addCycle(y, "myoptimize.opt.itemEntityCullDistance", () -> cfg.itemEntityCullDistance,
                    v -> cfg.itemEntityCullDistance = v, new Integer[]{24, 32, 48, 64, 96}, v -> Component.literal(String.valueOf(v)));
                y += 22;
                addCycle(y, "myoptimize.opt.entityCullDistance", () -> cfg.entityCullDistance,
                    v -> cfg.entityCullDistance = v, new Integer[]{48, 64, 80, 96, 128}, v -> Component.literal(String.valueOf(v)));
            }
            case WORLD -> {
                addInfo(y, "Light engine 26.x переписан Mojang; кэш lightmap отключён для этой версии.");
            }
            case MEMORY -> {
                addCycle(y, "myoptimize.opt.cacheBudgetMb", () -> cfg.cacheBudgetMb,
                    v -> cfg.cacheBudgetMb = v, new Integer[]{32, 48, 64, 96, 128, 160, 192, 256},
                    v -> Component.literal(v + " MB"));
                y += 22;
                addBool(y, "myoptimize.opt.gcMonitoring", () -> cfg.gcMonitoring, v -> cfg.gcMonitoring = v);
            }
            case CPU -> {
                addBool(y, "myoptimize.opt.adaptiveEngine", () -> cfg.adaptiveEngine, v -> cfg.adaptiveEngine = v);
                y += 22;
                addInfo(y, "Adaptive Engine: раз в секунду подстраивает кэши, аплоады и cap частиц под нагрузку.");
                y += 22;
                addInfo(y, "Entity tick throttling — см. вкладку Entities.");
            }
            case GPU -> {
                addInfo(y, "GPU: " + (GpuInfo.isCollected() ? GpuInfo.renderer() : "собирается…"));
                y += 14;
                addInfo(y, "VRAM: " + (GpuInfo.vramKnown()
                    ? (GpuInfo.vramBytes() / (1024 * 1024)) + " MB" : "unknown (не NVIDIA)"));
                y += 22;
                addInfo(y, "Texture bind optimization отключён для 26.x (нет GL-биндов в RenderSystem).");
            }
            case NETWORK -> {
                addBool(y, "myoptimize.opt.particlePacketBatching", () -> cfg.particlePacketBatching, v -> cfg.particlePacketBatching = v);
                y += 22;
                addCycle(y, "myoptimize.opt.particlePacketsPerTick", () -> cfg.particlePacketsPerTick,
                    v -> cfg.particlePacketsPerTick = v, new Integer[]{1, 2, 4, 8, 16}, v -> Component.literal(String.valueOf(v)));
                y += 22;
                addCycle(y, "myoptimize.opt.particlePacketQueueLimit", () -> cfg.particlePacketQueueLimit,
                    v -> cfg.particlePacketQueueLimit = v, new Integer[]{128, 256, 512, 1024}, v -> Component.literal(String.valueOf(v)));
            }
            case PARTICLES -> {
                addBool(y, "myoptimize.opt.particleCulling", () -> cfg.particleCulling, v -> cfg.particleCulling = v);
                y += 22;
                addCycle(y, "myoptimize.opt.particleMaxCount", () -> cfg.particleMaxCount,
                    v -> cfg.particleMaxCount = v, new Integer[]{2000, 4000, 6000, 8000, 10000, 12000, 16384}, v -> Component.literal(String.valueOf(v)));
                y += 22;
                addCycle(y, "myoptimize.opt.particleSpawnDistance", () -> cfg.particleSpawnDistance,
                    v -> cfg.particleSpawnDistance = v, new Integer[]{24, 32, 48, 64, 96}, v -> Component.literal(String.valueOf(v)));
            }
            case TEXTURES -> {
                addInfo(y, "Для 26.x нет GL-биндов текстур (RenderPearl) — функция отключена.");
            }
            case ADVANCED -> {
                addBool(y, "myoptimize.opt.shaderSafeMode", () -> cfg.shaderSafeMode, v -> cfg.shaderSafeMode = v);
                y += 22;
                addInfo(y, "Compatibility Guard включает его сам при обнаружении шейдеров.");
                y += 22;
                addInfo(y, "Отчёт о совместимости: config/myoptimize-ultra/compat-report.txt");
            }
            case DIAGNOSTICS -> {
                addCycle(y, "myoptimize.opt.f3Style", () -> cfg.f3Style,
                    v -> cfg.f3Style = v, new String[]{"ultra", "vanilla"},
                    v -> "ultra".equals(v) ? Component.translatable("myoptimize.value.f3ultra") : Component.translatable("myoptimize.value.f3vanilla"));
                y += 22;
                addBool(y, "myoptimize.opt.showKillerFinder", () -> cfg.showKillerFinder, v -> cfg.showKillerFinder = v);
                y += 22;
                addBool(y, "myoptimize.opt.devMode", () -> cfg.devMode, v -> cfg.devMode = v);
            }
        }
    }

    private void addBool(int y, String labelKey, BooleanSupplier get, Consumer<Boolean> set) {
        labels.add(new RowLabel(labelKey, y, false));
        addRenderableWidget(CycleButton.onOffBuilder(get.getAsBoolean())
            .create(this.width / 2 + 10, y, 200, 20, Component.translatable(labelKey), (button, value) -> {
                set.accept(value);
                cfg.profile = "Custom";
            }));
    }

    private <T> void addCycle(int y, String labelKey, Supplier<T> get, Consumer<T> set, T[] values, Function<T, Component> namer) {
        labels.add(new RowLabel(labelKey, y, false));
        addRenderableWidget(new CycleButton.Builder<T>(namer, get)
            .withValues(values)
            .create(this.width / 2 + 10, y, 200, 20, Component.translatable(labelKey), (button, value) -> {
                set.accept(value);
                cfg.profile = "Custom";
            }));
    }

    private void addInfo(int y, String text) {
        labels.add(new RowLabel(text, y, true));
    }

    private void rebuild() {
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gfx, mouseX, mouseY, delta);
        gfx.centeredText(this.font, TITLE, this.width / 2, 6, 0xFFFFFF);
        for (RowLabel label : labels) {
            gfx.text(this.font, label.key, this.width / 2 - 240, label.y + 6,
                label.dim ? 0x8A9199 : 0xE0E0E0, false);
        }
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        if (this.minecraft != null) {
            // parent может быть null (в 26.x текущий экран наружу не выставлен) — тогда просто выходим в игру
            this.minecraft.setScreenAndShow(parent);
        }
    }
}
