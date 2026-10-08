package com.myoptimize.ultra.render;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.client.render.Frustum;

/**
 * Утилиты отсечения (culling).
 *
 * Два вида:
 *  1. Геометрический — классический frustum culling (vanilla делает его для
 *     чанков и сущностей; мы используем тот же Frustum для block entities).
 *  2. Контекстный — "камера под землёй". Когда игрок в пещере, небесные
 *     объекты (sky, clouds, weather) заведомо не видны: туман полностью
 *     перекрывает их. Пропуск отрисовки экономит реальные GPU-работы.
 *     Проверка мемоизируется на кадр (три вызова за кадр → одно чтение heightmap).
 */
public final class CullingUtil {

    /** Инкрементируется из MixinGameRenderer на границе кадров. */
    public static volatile long frameCounter;

    private static long memoFrame = -1;
    private static int memoX;
    private static int memoZ;
    private static boolean memoResult;

    private CullingUtil() {
    }

    /**
     * Камера под землёй/под крышей: самый высокий MOTION_BLOCKING блок в колонке
     * камеры выше самой камеры. В пещере sky/clouds/weather не видны.
     */
    public static boolean isCameraUnderground(ClientWorld world, Vec3d pos) {
        return pos != null && isCameraUnderground(world, pos.x, pos.y, pos.z);
    }

    /** Версия без аллокации Vec3d — для вызовов из миксинов рендера. */
    public static boolean isCameraUnderground(ClientWorld world, double px, double py, double pz) {
        if (world == null) {
            return false;
        }
        long frame = frameCounter;
        int x = (int) Math.floor(px);
        int z = (int) Math.floor(pz);
        if (frame == memoFrame && x == memoX && z == memoZ) {
            return memoResult;
        }
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        boolean result = topY > py + 2.0;
        memoFrame = frame;
        memoX = x;
        memoZ = z;
        memoResult = result;
        return result;
    }

    /** Box виден во frustum. null-frustum считаем "всё видать" (безопасный фолбэк). */
    public static boolean isVisible(Frustum frustum, Box box) {
        return frustum == null || box == null || frustum.isVisible(box);
    }
}
