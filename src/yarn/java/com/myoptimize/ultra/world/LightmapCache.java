package com.myoptimize.ultra.world;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.util.CompactLong2IntMap;
import net.minecraft.util.math.BlockPos;

/**
 * Кэш координат освещения (lightmap) на время кадра.
 *
 * Инвариант: на клиенте свет не меняется внутри одного кадра (light-пакеты
 * применяются между кадрами), поэтому результат getLightmapCoordinates валиден
 * до конца кадра. Кэш — CompactLong2IntMap с generation-stamp: инвалидация
 * между кадрами — O(1) инкремент, без аллокаций и без обнуления памяти.
 *
 * Включается флагом lightmapCache; при выключенном — O(1) проверка флага.
 */
public final class LightmapCache {

    private static final CompactLong2IntMap CACHE = new CompactLong2IntMap(1024, -1);
    private static long frame = -1;

    private LightmapCache() {
    }

    public static void nextFrame() {
        frame++;
        CACHE.nextFrame();
    }

    /** @return закэшированное значение или -1, если кэш выключен или промах. */
    public static int get(BlockPos pos) {
        if (!ConfigManager.config().lightmapCache || pos == null) {
            return -1;
        }
        if (CACHE.frame() != frame) {
            // страховка: если nextFrame не вызвали, не отдаём протухшие данные
            nextFrame();
        }
        return CACHE.get(pos.asLong());
    }

    public static void put(BlockPos pos, int value) {
        if (!ConfigManager.config().lightmapCache || pos == null) {
            return;
        }
        if (CACHE.frame() != frame) {
            nextFrame();
        }
        CACHE.put(pos.asLong(), value);
    }
}
