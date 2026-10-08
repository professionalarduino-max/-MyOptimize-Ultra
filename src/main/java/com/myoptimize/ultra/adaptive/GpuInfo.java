package com.myoptimize.ultra.adaptive;

import com.myoptimize.ultra.MyOptimize;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.NVXGPUMemoryInfo;

/**
 * Сбор информации о GPU.
 *
 * ВАЖНО: все GL-вызовы выполняются ТОЛЬКО на render thread (например, при первом кадре).
 * Класс собирает строки один раз и кэширует результат.
 *
 * VRAM: прямой энумерации в OpenGL нет. Пробуем расширение NVIDIA
 * (GL_NVX_gpu_memory_info) — на остальных видеокартах честно возвращаем
 * "неизвестно" (-1), чем врать цифрой.
 */
public final class GpuInfo {

    private static volatile boolean collected;
    private static volatile String renderer = "unknown";
    private static volatile String vendor = "unknown";
    private static volatile long vramBytes = -1;

    private GpuInfo() {
    }

    /** Собрать информацию. Вызывать с render thread, один раз. */
    public static void collect() {
        if (collected) {
            return;
        }
        try {
            String r = GL11.glGetString(GL11.GL_RENDERER);
            String v = GL11.glGetString(GL11.GL_VENDOR);
            if (r != null) {
                renderer = r.trim();
            }
            if (v != null) {
                vendor = v.trim();
            }
            if (GL.getCapabilities().GL_NVX_gpu_memory_info) {
                try {
                    vramBytes = GL11.glGetInteger(
                        NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX) & 0xFFFFFFFFL;
                    vramBytes *= 1024L; // KiB -> bytes
                } catch (Throwable t) {
                    vramBytes = -1;
                }
            }
            collected = true;
            MyOptimize.LOGGER.info("GPU: {} ({}), VRAM: {}",
                renderer, vendor, vramKnown() ? (vramBytes / (1024 * 1024)) + " MB" : "unknown");
        } catch (Throwable t) {
            MyOptimize.LOGGER.warn("GPU info collection failed", t);
            collected = true; // не пытаться снова на каждом кадре
        }
    }

    public static boolean isCollected() {
        return collected;
    }

    public static String renderer() {
        return renderer;
    }

    public static String vendor() {
        return vendor;
    }

    public static boolean vramKnown() {
        return vramBytes > 0;
    }

    public static long vramBytes() {
        return vramBytes;
    }
}
