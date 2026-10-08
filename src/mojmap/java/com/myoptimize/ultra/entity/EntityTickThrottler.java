package com.myoptimize.ultra.entity;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Throttling тиков далёких мобов — вариант для Minecraft 26.x (mojmap).
 *
 * Работает ТОЛЬКО на integrated server (синглплеер). В 26.x тики сущностей
 * идут через ServerLevel.tickNonPassenger (yarn: ServerWorld.tickEntity).
 */
public final class EntityTickThrottler {

    private EntityTickThrottler() {
    }

    /** @return true, если тик сущности нужно пропустить в этом тике. */
    public static boolean shouldSkip(ServerLevel level, Entity entity) {
        ModConfig cfg = ConfigManager.config();
        if (!cfg.entityTickThrottling || level == null || entity == null) {
            return false;
        }
        // Не трогаем то, что заметно игроку или влияет на механики.
        if (entity instanceof Player || entity instanceof ServerPlayer) {
            return false;
        }
        if (entity.hasCustomName() || entity.isPassenger() || entity.isVehicle()
            || entity.isSpectator() || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof Mob mob && mob.isLeashed()) {
            return false;
        }
        // Расстояние до ближайшего игрока.
        double nearestSq = Double.MAX_VALUE;
        for (Player player : level.players()) {
            double d = player.distanceToSqr(entity);
            if (d < nearestSq) {
                nearestSq = d;
            }
        }
        if (nearestSq == Double.MAX_VALUE) {
            return false; // рядом никого нет — тикаем как обычно
        }
        int interval = ThrottlePolicy.intervalFor((int) nearestSq, cfg.entityThrottleDistance, cfg.entityThrottleMaxInterval);
        return !ThrottlePolicy.shouldTickThisTick(level.getLevelData().getGameTime(), entity.getId(), interval);
    }
}
