package com.myoptimize.ultra.entity;

/**
 * Чистая математика throttling'а тиков сущностей — без зависимостей от Minecraft,
 * поэтому покрыта smoke-тестами.
 *
 * Идея: чем дальше сущность от игрока, тем реже она тикает. Интервал растёт
 * ступенями по 5 тиков за каждое превышение пороговой дистанции, но не выше maxInterval.
 * Чтобы тики разных сущностей не бились в одну волну, фаза тика привязана к id сущности.
 */
public final class ThrottlePolicy {

    private ThrottlePolicy() {
    }

    /**
     * Интервал тиков (в тиках) для сущности на квадрате дистанции distSq.
     *
     * @param distSq     квадрат расстояния до ближайшего игрока
     * @param startDist  дистанция, с которой начинается throttling
     * @param maxInterval максимальный интервал (потолок)
     */
    public static int intervalFor(double distSq, int startDist, int maxInterval) {
        if (distSq <= (double) startDist * startDist) {
            return 1; // вплотную — каждый тик
        }
        int multiples = (int) Math.sqrt(distSq) / startDist; // целых порогов пройдено
        int interval = Math.max(5, multiples * 5);
        return Math.min(maxInterval, interval);
    }

    /**
     * Должна ли сущность тикать в этом тике? Фаза = (worldTime + entityId) % interval,
     * поэтому сущности с разными id распределяются равномерно, а при interval=1 — всегда.
     */
    public static boolean shouldTickThisTick(long worldTime, int entityId, int interval) {
        if (interval <= 1) {
            return true;
        }
        return Math.floorMod(worldTime + entityId, interval) == 0;
    }
}
