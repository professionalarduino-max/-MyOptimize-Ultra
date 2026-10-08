package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.chunk.ChunkUploadPacer;
import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Вариант для 1.21.5–1.21.11: метод scheduleUpload исчез из ChunkBuilder
 * (перепись пайплайна), поэтому считаем только факт вызова upload() — pacing
 * по счётчику в этих версиях не работает (счётчик всегда 0), но замер времени
 * и статистика остаются. Для 1.20–1.21.4 — полная версия в src/mixin/yarn-cb.
 */
@Mixin(ChunkBuilder.class)
public abstract class MixinChunkBuilder {

    @Unique
    private long myoptimize$uploadStartNanos;

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void myoptimize$paceUploads(CallbackInfo ci) {
        int batch = PerformanceProfiler.get().effectiveChunkUploadBatch(
            ConfigManager.config().chunkUploadBatchPerFrame);
        if (ChunkUploadPacer.shouldDefer(batch)) {
            ci.cancel();
            return;
        }
        myoptimize$uploadStartNanos = System.nanoTime();
    }

    @Inject(method = "upload", at = @At("RETURN"))
    private void myoptimize$uploadEnd(CallbackInfo ci) {
        if (myoptimize$uploadStartNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.CHUNK_UPLOAD, System.nanoTime() - myoptimize$uploadStartNanos);
            myoptimize$uploadStartNanos = 0;
        }
    }
}
