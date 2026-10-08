package com.myoptimize.ultra.chunk;

import com.myoptimize.ultra.profiler.PerformanceProfiler;

/**
 * Pacing GPU-аплоадов чанков: считает аплоады за кадр и решает,
 * стоит ли отложить вызов ChunkBuilder.upload() на следующий кадр.
 *
 * Счётчик сбрасывается на границе кадра (MixinGameRenderer → onFrameStart).
 */
public final class ChunkUploadPacer {

    private static int uploadsThisFrame;

    private ChunkUploadPacer() {
    }

    public static void onFrameStart() {
        uploadsThisFrame = 0;
    }

    /** @param batch лимит аплоадов за кадр (0 = без лимита, ванильное поведение) */
    public static boolean shouldDefer(int batch) {
        return batch > 0 && uploadsThisFrame >= batch;
    }

    public static void onUpload() {
        uploadsThisFrame++;
        PerformanceProfiler.get().addChunkUpload();
    }

    public static int uploadsThisFrame() {
        return uploadsThisFrame;
    }
}
