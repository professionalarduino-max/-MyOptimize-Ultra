package com.myoptimize.ultra.adaptive;

import com.myoptimize.ultra.MyOptimize;
import com.myoptimize.ultra.compat.CompatibilityGuard;
import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.config.OptimizationProfile;

/**
 * Кнопка "AUTO OPTIMIZE": определяет машину и ситуацию (шейдеры, VRAM, ядра, RAM)
 * и применяет подходящий профиль. Ничего не включает без нужды: если шейдеры —
 * упор на совместимость, а не на производительность.
 */
public final class AutoOptimizer {

    private AutoOptimizer() {
    }

    public static void run() {
        SystemInfo info = SystemInfo.detect();
        ModConfig cfg = ConfigManager.config();

        OptimizationProfile profile;
        if (CompatibilityGuard.isShadersModLoaded()) {
            // Шейдеры: приоритет совместимости, средняя агрессия.
            profile = OptimizationProfile.BALANCED;
            cfg.shaderSafeMode = true;
            MyOptimize.LOGGER.info("AUTO OPTIMIZE: обнаружены шейдеры → {} + shader safe mode", profile.displayName());
        } else {
            profile = switch (info.machineClass()) {
                case 0 -> OptimizationProfile.POTATO;
                case 1 -> OptimizationProfile.LOW;
                case 2 -> OptimizationProfile.PERFORMANCE;
                default -> OptimizationProfile.ULTRA_PERFORMANCE;
            };
            MyOptimize.LOGGER.info("AUTO OPTIMIZE: {} → профиль {}", info.summary(), profile.displayName());
        }
        profile.applyTo(cfg);
        ConfigManager.save();
    }
}
