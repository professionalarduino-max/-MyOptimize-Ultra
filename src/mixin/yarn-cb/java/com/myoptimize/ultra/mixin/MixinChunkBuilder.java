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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pacing GPU-аплоадов чанков: vanilla грузит ВСЕ готовые буферы чанков
 * в GPU в одном кадре. После телепорта/входа в мир это сотни аплоадов
 * за один кадр → скачок frametime и stall render thread.
 *
 * Мы считаем аплоады за кадр (scheduleUpload) и позволяем не более N за кадр
 * (N из конфига/адаптации, 0 = как в ванилле). Лишние ждут следующего кадра —
 * очередь аплоадов у ChunkBuilder не теряется, чанки появляются чуть
 * растянуто во времени, но без фризов.
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

    @Inject(method = "scheduleUpload", at = @At("HEAD"))
    private void myoptimize$countUpload(CallbackInfoReturnable<?> cir) {
        ChunkUploadPacer.onUpload();
    }
}
