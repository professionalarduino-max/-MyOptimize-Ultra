package com.myoptimize.ultra.render;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Отсечение спавна частиц:
 *  1. По глобальному cap'у (с адаптивным потолком): лишние частицы не
 *     создаются вообще — экономия на аллокации, тике и отрисовке. Приоритет
 *     отдаётся существующим частицам (новые отбрасываются), поэтому "хвост"
 *     взрыва всегда дорисован, а spam — обрезан.
 *  2. По дистанции: далёкие частицы (фермы, чужие взрывы) не спавним.
 *
 * Вызывается из MixinParticleManager.createParticle — ДО создания объекта.
 */
public final class ParticleCulling {

    private ParticleCulling() {
    }

    public static boolean shouldDrop(double x, double y, double z) {
        ModConfig cfg = ConfigManager.config();
        if (!cfg.particleCulling) {
            return false;
        }
        PerformanceProfiler profiler = PerformanceProfiler.get();
        int cap = profiler.effectiveParticleCap(cfg.particleMaxCount);
        if (profiler.particleCount() >= cap) {
            return true;
        }
        double maxDist = cfg.particleSpawnDistance;
        if (maxDist <= 0) {
            return false;
        }
        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        double dx = x - cam.x;
        double dy = y - cam.y;
        double dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz > maxDist * maxDist;
    }
}
