package com.myoptimize.ultra.entity;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Throttling tick'ов сущностей на integrated server (синглплеер).
 *
 * Идея (та же, что у Paper "entity activation range"): моб, который далеко от
 * всех игроков, не делает ничего интересного — его AI, pathfinding и физика
 * можно обновлять реже. Мы не меняем сам tick, а пропускаем часть тиков
 * для далёких мобов (интервал растёт с дистанцией — см. ThrottlePolicy).
 *
 * Что защищает механику (важно, "не ломать ванильную механику"):
 *  - не трогаем игроков, чудовищ с именем, на поводке, в транспорте, с пассажирами,
 *    спектаторов — их тикаем всегда;
 *  - тикаем моба, когда (worldTime + entityId) % interval == 0 — тики далёких
 *    мобов размазаны по тикам, без "волн" нагрузки;
 *  - работаем только на integrated server (синглплеер). На выделенный сервер
 *    мод стоит на клиенте и в tick'и не лезет — честно, без читов.
 *
 * Побочный эффект (документирован): у далёких мобов медленнее despawn и реже
 * проверяется "ближайший игрок" — на геймплей это не влияет заметно.
 */
public final class EntityTickThrottler {

    private EntityTickThrottler() {
    }

    /**
     * @return true, если tick сущности в этом тике нужно пропустить.
     */
    public static boolean shouldSkip(ServerWorld world, Entity entity) {
        ModConfig cfg = ConfigManager.config();
        if (!cfg.entityTickThrottling) {
            return false;
        }
        if (!(entity instanceof MobEntity mob)) {
            return false;
        }
        if (!entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        // "Особые" сущности тикаем всегда — их поведение заметно игроку.
        if (entity.hasCustomName() || entity.hasVehicle() || entity.hasPassengers()
            || entity.isSpectator() || mob.isLeashed()) {
            return false;
        }
        double minDistSq = Double.MAX_VALUE;
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            double d = player.squaredDistanceTo(entity);
            if (d < minDistSq) {
                minDistSq = d;
            }
        }
        if (minDistSq == Double.MAX_VALUE) {
            return false; // рядом нет игроков — не душим (спавн-логика не страдает)
        }
        int interval = ThrottlePolicy.intervalFor(
            minDistSq, cfg.entityThrottleDistance, cfg.entityThrottleMaxInterval);
        if (interval <= 1) {
            return false;
        }
        return !ThrottlePolicy.shouldTickThisTick(world.getTime(), entity.getId(), interval);
    }
}
