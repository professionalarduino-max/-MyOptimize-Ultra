package com.myoptimize.ultra.config;

import java.util.Locale;

/**
 * Профили производительности.
 *
 * Каждый профиль — это полный набор настроек под класс машины/задачу.
 * AUTO OPTIMIZE (в меню и при первом запуске) выбирает профиль по SystemInfo
 * и применяет его. Профиль Custom — пользовательский, руками.
 *
 * Принцип баланса: даже MAX FPS не трогает игровые механики — только
 * рендер/частицы/память/сеть. То, что ломает механику (редстоун, физика воды,
 * despawn-таймеры) — не меняется ни в одном профиле.
 */
public enum OptimizationProfile {

    POTATO(
        "Potato",
        "Для самых слабых ПК: минимум частиц, агрессивный cull, маленькие кэши.",
        cfg -> {
            cfg.particleMaxCount = 2000;
            cfg.particleSpawnDistance = 24;
            cfg.itemEntityCullDistance = 24;
            cfg.aggressiveEntityCulling = true;
            cfg.entityCullDistance = 48;
            cfg.blockEntityCullDistance = 48;
            cfg.entityThrottleDistance = 32;
            cfg.entityThrottleMaxInterval = 20;
            cfg.chunkUploadBatchPerFrame = 8;
            cfg.cacheBudgetMb = 32;
            cfg.particlePacketsPerTick = 2;
        }),

    LOW(
        "Low",
        "Для слабых ПК: заметно меньше нагрузки, минимум визуальных компромиссов.",
        cfg -> {
            cfg.particleMaxCount = 4000;
            cfg.particleSpawnDistance = 32;
            cfg.itemEntityCullDistance = 32;
            cfg.aggressiveEntityCulling = true;
            cfg.entityCullDistance = 64;
            cfg.blockEntityCullDistance = 64;
            cfg.entityThrottleDistance = 40;
            cfg.entityThrottleMaxInterval = 15;
            cfg.chunkUploadBatchPerFrame = 16;
            cfg.cacheBudgetMb = 48;
            cfg.particlePacketsPerTick = 3;
        }),

    BALANCED(
        "Balanced",
        "Оптимально для большинства ПК: прирост FPS без заметной потери картинки.",
        cfg -> {
            cfg.particleMaxCount = 10000;
            cfg.particleSpawnDistance = 64;
            cfg.itemEntityCullDistance = 64;
            cfg.aggressiveEntityCulling = false;
            cfg.entityCullDistance = 96;
            cfg.blockEntityCullDistance = 96;
            cfg.entityThrottleDistance = 64;
            cfg.entityThrottleMaxInterval = 20;
            cfg.chunkUploadBatchPerFrame = 32;
            cfg.cacheBudgetMb = 96;
            cfg.particlePacketsPerTick = 4;
        }),

    PERFORMANCE(
        "Performance",
        "Для средних ПК: приоритет FPS и стабильности frametime.",
        cfg -> {
            cfg.particleMaxCount = 8000;
            cfg.particleSpawnDistance = 48;
            cfg.itemEntityCullDistance = 48;
            cfg.aggressiveEntityCulling = true;
            cfg.entityCullDistance = 80;
            cfg.blockEntityCullDistance = 80;
            cfg.entityThrottleDistance = 56;
            cfg.entityThrottleMaxInterval = 15;
            cfg.chunkUploadBatchPerFrame = 24;
            cfg.cacheBudgetMb = 128;
            cfg.particlePacketsPerTick = 4;
        }),

    ULTRA_PERFORMANCE(
        "Ultra Performance",
        "Для сильных ПК, где важен каждый кадр: минимум лишней работы.",
        cfg -> {
            cfg.particleMaxCount = 6000;
            cfg.particleSpawnDistance = 40;
            cfg.itemEntityCullDistance = 40;
            cfg.aggressiveEntityCulling = true;
            cfg.entityCullDistance = 64;
            cfg.blockEntityCullDistance = 64;
            cfg.entityThrottleDistance = 48;
            cfg.entityThrottleMaxInterval = 10;
            cfg.chunkUploadBatchPerFrame = 16;
            cfg.cacheBudgetMb = 160;
            cfg.particlePacketsPerTick = 6;
        }),

    EXTREME(
        "Extreme",
        "Максимум производительности ценой визуальных деталей вдали.",
        cfg -> {
            cfg.particleMaxCount = 4000;
            cfg.particleSpawnDistance = 32;
            cfg.itemEntityCullDistance = 32;
            cfg.aggressiveEntityCulling = true;
            cfg.entityCullDistance = 56;
            cfg.blockEntityCullDistance = 56;
            cfg.entityThrottleDistance = 40;
            cfg.entityThrottleMaxInterval = 10;
            cfg.chunkUploadBatchPerFrame = 12;
            cfg.cacheBudgetMb = 192;
            cfg.particlePacketsPerTick = 8;
        }),

    MAX_FPS(
        "MAX FPS",
        "Всё ради FPS: агрессивнее некуда, для слабых машин и 60+ Гц мониторов.",
        cfg -> {
            cfg.particleMaxCount = 2000;
            cfg.particleSpawnDistance = 24;
            cfg.itemEntityCullDistance = 24;
            cfg.aggressiveEntityCulling = true;
            cfg.entityCullDistance = 48;
            cfg.blockEntityCullDistance = 48;
            cfg.entityThrottleDistance = 32;
            cfg.entityThrottleMaxInterval = 10;
            cfg.chunkUploadBatchPerFrame = 8;
            cfg.cacheBudgetMb = 256;
            cfg.particlePacketsPerTick = 8;
        }),

    CUSTOM(
        "Custom",
        "Ручная настройка: применяется последним, не затирает пользовательские значения.",
        cfg -> {
        });

    @FunctionalInterface
    public interface Applier {
        void apply(ModConfig cfg);
    }

    private final String displayName;
    private final String description;
    private final Applier applier;

    OptimizationProfile(String displayName, String description, Applier applier) {
        this.displayName = displayName;
        this.description = description;
        this.applier = applier;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public void applyTo(ModConfig cfg) {
        applier.apply(cfg);
        cfg.profile = displayName;
    }

    public static OptimizationProfile byName(String name) {
        if (name == null) {
            return BALANCED;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (OptimizationProfile profile : values()) {
            if (profile.displayName.equalsIgnoreCase(name.trim())
                || profile.name().equalsIgnoreCase(normalized.replace(' ', '_'))) {
                return profile;
            }
        }
        return BALANCED;
    }
}
