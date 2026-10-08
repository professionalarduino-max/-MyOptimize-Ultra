package com.myoptimize.ultra.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Утилиты отсечения — вариант для Minecraft 26.x (mojmap).
 * Подземная проверка мемоизируется на кадр (high-water из Heightmap.Types.MOTION_BLOCKING).
 */
public final class CullingUtil {

    /** Счётчик кадров — обновляется из MixinGameRenderer. */
    public static volatile long frameCounter;

    // мемоизация проверки "камера под землёй" на кадр
    private static volatile long memoFrame = -1;
    private static volatile int memoX;
    private static volatile int memoZ;
    private static volatile boolean memoResult;

    private CullingUtil() {
    }

    /**
     * Камера под землёй/под крышей: самый высокий MOTION_BLOCKING блок в колонке
     * камеры выше самой камеры. В пещере sky/clouds/weather не видны.
     */
    public static boolean isCameraUnderground(ClientLevel level, Vec3 pos) {
        return pos != null && isCameraUnderground(level, pos.x, pos.y, pos.z);
    }

    /** Версия без аллокации Vec3 — для вызовов из миксинов рендера. */
    public static boolean isCameraUnderground(ClientLevel level, double px, double py, double pz) {
        if (level == null) {
            return false;
        }
        long frame = frameCounter;
        int x = (int) Math.floor(px);
        int z = (int) Math.floor(pz);
        if (frame == memoFrame && x == memoX && z == memoZ) {
            return memoResult;
        }
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        boolean result = topY > py + 2.0;
        memoFrame = frame;
        memoX = x;
        memoZ = z;
        memoResult = result;
        return result;
    }

    /** Виден ли бокс камере (frustum culling). Обёртка над Frustum.isVisible. */
    public static boolean isVisible(Frustum frustum, AABB box) {
        return frustum != null && box != null && frustum.isVisible(box);
    }

    /** Быстрая проверка: сущность в пределах квадрата дистанции от камеры. */
    public static boolean withinDistanceSq(Vec3 cam, double x, double y, double z, double maxDistSq) {
        double dx = x - cam.x;
        double dy = y - cam.y;
        double dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz <= maxDistSq;
    }
}
