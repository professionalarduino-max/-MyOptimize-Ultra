package com.myoptimize.ultra.gui;

import com.myoptimize.ultra.adaptive.AutoOptimizer;
import com.myoptimize.ultra.adaptive.GpuInfo;
import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.config.OptimizationProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Экран настроек MyOptimize Ultra.
 *
 * Структура:
 *  - заголовок + DONE (сохраняет и закрывает);
 *  - селектор профиля + кнопка AUTO OPTIMIZE (автоопределение системы);
 *  - 12 вкладок категорий (Rendering, Chunks, Entities, World, Memory, CPU,
 *    GPU, Network, Particles, Textures, Advanced, Diagnostics);
 *  - до 6 опций на категорию (bool — on/off, остальное — циклические значения).
 *
 * Любое ручное изменение переводит профиль в Custom. Профиль, в свою очередь,
 * применяется одной кнопкой и перезаписывает соответствующие поля.
 * DONE сохраняет; простое закрытие экрана (Esc) — тоже сохраняет (как в
 * ванильном меню настроек).
 */
public class ConfigScreen extends Screen {

    private static final Text TITLE = Text.literal("MyOptimize Ultra");

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
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
            ConfigManager.save();
            close();
        }).dimensions(this.width - 110, 6, 104, 18).build());

        // --- Профиль + AUTO OPTIMIZE ---
        int profileY = 28;
        addDrawableChild(CyclingButtonWidget.<OptimizationProfile>builder(profile -> Text.literal(profile.displayName()))
            .values(OptimizationProfile.values())
            .initially(OptimizationProfile.byName(cfg.profile))
            .build(centerX - 240, profileY, 170, 20,
                Text.translatable("myoptimize.screen.profile"),
                (button, value) -> {
                    value.applyTo(cfg);
                    rebuild();
                }));
        addDrawableChild(ButtonWidget.builder(Text.translatable("myoptimize.screen.autooptimize"), button -> {
            AutoOptimizer.run();
            rebuild();
        }).dimensions(centerX - 60, profileY, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("myoptimize.screen.resetdefaults"), button -> {
            OptimizationProfile.BALANCED.applyTo(cfg);
            rebuild();
        }).dimensions(centerX + 100, profileY, 140, 20).build());

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
            Text label = Text.translatable("myoptimize.category." + cat.name().toLowerCase(java.util.Locale.ROOT));
            if (active) {
                label = Text.literal("> ").append(label);
            }
            final Category clicked = cat;
            addDrawableChild(ButtonWidget.builder(label, button -> {
                category = clicked;
                rebuild();
            }).dimensions(tabX0 + col * (tabW + gap), 56 + row * (tabH + gap), tabW, tabH).build());
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
                    v -> cfg.blockEntityCullDistance = v, new Integer[]{48, 64, 80, 96, 128}, v -> Text.literal(String.valueOf(v)));
                y += 22;
                addBool(y, "myoptimize.opt.fontWidthCache", () -> cfg.fontWidthCache, v -> cfg.fontWidthCache = v);
                y += 22;
                addBool(y, "myoptimize.opt.emptyTextSkip", () -> cfg.emptyTextSkip, v -> cfg.emptyTextSkip = v);
            }
            case CHUNKS -> {
                addCycle(y, "myoptimize.opt.chunkUploadBatchPerFrame", () -> cfg.chunkUploadBatchPerFrame,
                    v -> cfg.chunkUploadBatchPerFrame = v, new Integer[]{0, 8, 16, 24, 32, 48, 64},
                    v -> v == 0 ? Text.translatable("myoptimize.value.unlimited") : Text.literal(String.valueOf(v)));
                y += 22;
                addBool(y, "myoptimize.opt.chunkUpdateTracking", () -> cfg.chunkUpdateTracking, v -> cfg.chunkUpdateTracking = v);
            }
            case ENTITIES -> {
                addBool(y, "myoptimize.opt.entityTickThrottling", () -> cfg.entityTickThrottling, v -> cfg.entityTickThrottling = v);
                y += 22;
                addCycle(y, "myoptimize.opt.entityThrottleDistance", () -> cfg.entityThrottleDistance,
                    v -> cfg.entityThrottleDistance = v, new Integer[]{32, 48, 64, 80, 96}, v -> Text.literal(String.valueOf(v)));
                y += 22;
                addCycle(y, "myoptimize.opt.entityThrottleMaxInterval", () -> cfg.entityThrottleMaxInterval,
                    v -> cfg.entityThrottleMaxInterval = v, new Integer[]{5, 10, 15, 20}, v -> Text.literal(String.valueOf(v)));
                y += 22;
                addBool(y, "myoptimize.opt.aggressiveEntityCulling", () -> cfg.aggressiveEntityCulling, v -> cfg.aggressiveEntityCulling = v);
                y += 22;
                addCycle(y, "myoptimize.opt.itemEntityCullDistance", () -> cfg.itemEntityCullDistance,
                    v -> cfg.itemEntityCullDistance = v, new Integer[]{24, 32, 48, 64, 96}, v -> Text.literal(String.valueOf(v)));
                y += 22;
                addCycle(y, "myoptimize.opt.entityCullDistance", () -> cfg.entityCullDistance,
                    v -> cfg.entityCullDistance = v, new Integer[]{48, 64, 80, 96, 128}, v -> Text.literal(String.valueOf(v)));
            }
            case WORLD -> {
                addBool(y, "myoptimize.opt.lightmapCache", () -> cfg.lightmapCache, v -> cfg.lightmapCache = v);
                y += 22;
                addInfo(y, "Light engine: vanilla async (1.21.x). Кэш — только чтение готовых значений.");
            }
            case MEMORY -> {
                addCycle(y, "myoptimize.opt.cacheBudgetMb", () -> cfg.cacheBudgetMb,
                    v -> cfg.cacheBudgetMb = v, new Integer[]{32, 48, 64, 96, 128, 160, 192, 256},
                    v -> Text.literal(v + " MB"));
                y += 22;
                addBool(y, "myoptimize.opt.gcMonitoring", () -> cfg.gcMonitoring, v -> cfg.gcMonitoring = v);
            }
            case CPU -> {
                addBool(y, "myoptimize.opt.adaptiveEngine", () -> cfg.adaptiveEngine, v -> cfg.adaptiveEngine = v);
                y += 22;
                addInfo(y, "Adaptive Engine: раз в секунду подстраивает кэши, аплоады и cap частиц под текущую нагрузку.");
                y += 22;
                addInfo(y, "Entity tick throttling — см. вкладку Entities.");
            }
            case GPU -> {
                addInfo(y, "GPU: " + (GpuInfo.isCollected() ? GpuInfo.renderer() : "собирается…"));
                y += 14;
                addInfo(y, "VRAM: " + (GpuInfo.vramKnown()
                    ? (GpuInfo.vramBytes() / (1024 * 1024)) + " MB" : "unknown (не NVIDIA)"));
                y += 22;
                addInfo(y, "Texture bind optimization — см. вкладку Textures.");
            }
            case NETWORK -> {
                addBool(y, "myoptimize.opt.particlePacketBatching", () -> cfg.particlePacketBatching, v -> cfg.particlePacketBatching = v);
                y += 22;
                addCycle(y, "myoptimize.opt.particlePacketsPerTick", () -> cfg.particlePacketsPerTick,
                    v -> cfg.particlePacketsPerTick = v, new Integer[]{1, 2, 4, 8, 16}, v -> Text.literal(String.valueOf(v)));
                y += 22;
                addCycle(y, "myoptimize.opt.particlePacketQueueLimit", () -> cfg.particlePacketQueueLimit,
                    v -> cfg.particlePacketQueueLimit = v, new Integer[]{128, 256, 512, 1024}, v -> Text.literal(String.valueOf(v)));
            }
            case PARTICLES -> {
                addBool(y, "myoptimize.opt.particleCulling", () -> cfg.particleCulling, v -> cfg.particleCulling = v);
                y += 22;
                addCycle(y, "myoptimize.opt.particleMaxCount", () -> cfg.particleMaxCount,
                    v -> cfg.particleMaxCount = v, new Integer[]{2000, 4000, 6000, 8000, 10000, 12000, 16384}, v -> Text.literal(String.valueOf(v)));
                y += 22;
                addCycle(y, "myoptimize.opt.particleSpawnDistance", () -> cfg.particleSpawnDistance,
                    v -> cfg.particleSpawnDistance = v, new Integer[]{24, 32, 48, 64, 96}, v -> Text.literal(String.valueOf(v)));
            }
            case TEXTURES -> {
                addBool(y, "myoptimize.opt.textureBindOptimization", () -> cfg.textureBindOptimization, v -> cfg.textureBindOptimization = v);
                y += 22;
                addInfo(y, "Автоматически выключается при шейдерах (Iris/Oculus) и VulkanMod.");
            }
            case ADVANCED -> {
                addBool(y, "myoptimize.opt.shaderSafeMode", () -> cfg.shaderSafeMode, v -> cfg.shaderSafeMode = v);
                y += 22;
                addInfo(y, "Форсирует безопасный режим (без texture-bind dedupe). Обычно не нужен:");
                y += 14;
                addInfo(y, "Compatibility Guard включает его сам при обнаружении шейдеров.");
                y += 22;
                addInfo(y, "Отчёт о совместимости: config/myoptimize-ultra/compat-report.txt");
            }
            case DIAGNOSTICS -> {
                addCycle(y, "myoptimize.opt.f3Style", () -> cfg.f3Style,
                    v -> cfg.f3Style = v, new String[]{"ultra", "vanilla"},
                    v -> "ultra".equals(v) ? Text.translatable("myoptimize.value.f3ultra") : Text.translatable("myoptimize.value.f3vanilla"));
                y += 22;
                addBool(y, "myoptimize.opt.showKillerFinder", () -> cfg.showKillerFinder, v -> cfg.showKillerFinder = v);
                y += 22;
                addBool(y, "myoptimize.opt.devMode", () -> cfg.devMode, v -> cfg.devMode = v);
            }
        }
    }

    private void addBool(int y, String labelKey, BooleanSupplier get, Consumer<Boolean> set) {
        labels.add(new RowLabel(labelKey, y, false));
        addDrawableChild(CyclingButtonWidget.onOffBuilder()
            .initially(get.getAsBoolean())
            .build(this.width / 2 + 10, y, 200, 20, Text.translatable(labelKey), (button, value) -> {
                set.accept(value);
                cfg.profile = "Custom";
            }));
    }

    private <T> void addCycle(int y, String labelKey, Supplier<T> get, Consumer<T> set, T[] values, Function<T, Text> namer) {
        labels.add(new RowLabel(labelKey, y, false));
        addDrawableChild(CyclingButtonWidget.<T>builder(namer)
            .values(values)
            .initially(get.get())
            .build(this.width / 2 + 10, y, 200, 20, Text.translatable(labelKey), (button, value) -> {
                set.accept(value);
                cfg.profile = "Custom";
            }));
    }

    private void addInfo(int y, String text) {
        labels.add(new RowLabel(text, y, true));
    }

    private void rebuild() {
        clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, TITLE, this.width / 2, 6, 0xFFFFFF);
        for (RowLabel label : labels) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(label.key),
                this.width / 2 - 240, label.y + 6, label.dim ? 0x8A9199 : 0xE0E0E0);
        }
    }

    @Override
    public void close() {
        ConfigManager.save();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
