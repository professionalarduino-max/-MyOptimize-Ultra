package com.myoptimize.ultra.entity;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Отсечение рендера сущностей.
 *
 * Vanilla уже отсекает сущности вне frustum (EntityRenderDispatcher.shouldRender).
 * Наш вклад — отсечение по ДИСТАНЦИИ, которого у ваниллы нет:
 *  - ItemEntity и ExperienceOrbEntity исчезают за пределом itemEntityCullDistance
 *    (мелкие, далёкие, визуально незаметные — классика оптимизационных модов);
 *  - агрессивный режим: то же самое для обычных сущностей (включается в профилях
 *    Performance+; на границе заметен поп-in, поэтому по умолчанию выключен).
 *
 * Это влияет только на отрисовку, не на логику: сущности продолжают тикать,
 * подбираться и despawn'иться как обычно.
 */
public final class EntityCuller {

    private EntityCuller() {
    }

    /**
     * @return true, если сущность не надо рендерить в этом кадре.
     */
    public static boolean shouldCull(EntityRenderDispatcher dispatcher, Entity entity) {
        ModConfig cfg = ConfigManager.config();
        if (!cfg.entityCulling) {
            return false;
        }
        if (entity instanceof PlayerEntity || entity.isSpectator() || !entity.isAlive()) {
            return false;
        }
        double distSq;
        try {
            distSq = dispatcher.getSquaredDistanceToCamera(entity);
        } catch (Throwable t) {
            return false;
        }
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity) {
            double d = cfg.itemEntityCullDistance;
            return distSq > d * d;
        }
        if (cfg.aggressiveEntityCulling) {
            double d = cfg.entityCullDistance;
            return distSq > d * d;
        }
        return false;
    }
}
