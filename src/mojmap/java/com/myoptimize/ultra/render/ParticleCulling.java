package com.myoptimize.ultra.render;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Отсечение спавна частиц — вариант для Minecraft 26.x (mojmap).
 * Вызывается из MixinParticleEngine.createParticle — ДО создания объекта.
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
        Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        double dx = x - cam.x;
        double dy = y - cam.y;
        double dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz > maxDist * maxDist;
    }
}
