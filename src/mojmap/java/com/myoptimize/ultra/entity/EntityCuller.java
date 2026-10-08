package com.myoptimize.ultra.entity;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Culling рендера сущностей по дистанции — вариант для Minecraft 26.x (mojmap).
 *
 * В 26.x отрисовка сущностей идёт через shouldRender (frustum-проверка) —
 * мы добавляем туда отсечение по расстоянию. Безопасно: сущность продолжает
 * тикать, подбираться и despawn'иться — просто не рисуется.
 */
public final class EntityCuller {

    private EntityCuller() {
    }

    /** @return true, если сущность не надо рисовать в этом кадре. */
    public static boolean shouldCull(Entity entity) {
        if (entity == null) {
            return false;
        }
        ModConfig cfg = ConfigManager.config();
        if (!cfg.entityCulling) {
            return false;
        }
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        double distSq = dispatcher.distanceToSqr(entity);

        // Предметы и сферы опыта: отсекаем раньше — они самые дешёвые визуально.
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            double maxDist = cfg.itemEntityCullDistance;
            return distSq > maxDist * maxDist;
        }
        // Игроки никогда не отсекаются.
        if (entity instanceof Player) {
            return false;
        }
        // Агрессивный режим: обычные сущности тоже отсекаем по дали.
        if (cfg.aggressiveEntityCulling) {
            double maxDist = cfg.entityCullDistance;
            return distSq > maxDist * maxDist;
        }
        return false;
    }
}
