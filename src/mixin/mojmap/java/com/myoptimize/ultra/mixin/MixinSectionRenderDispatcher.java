package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.chunk.ChunkUploadPacer;
import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pacing GPU-аплоадов чанков — вариант для 26.x.
 *
 * В 26.x пайплайн переписан: SectionRenderDispatcher.uploadTerrainBuffersToGpu()
 * грузит все готовые буферы секций в GPU, а getRenderSectionSlice(...) вызывается
 * на каждый аплоад (аналог yarn scheduleUpload). Считаем аплоады через
 * getRenderSectionSlice и откладываем uploadTerrainBuffersToGpu, когда лимит
 * за кадр исчерпан.
 */
@Mixin(SectionRenderDispatcher.class)
public abstract class MixinSectionRenderDispatcher {

    @Unique
    private long myoptimize$uploadStartNanos;

    @Inject(method = "getRenderSectionSlice", at = @At("HEAD"))
    private void myoptimize$countUpload(CallbackInfoReturnable<?> cir) {
        ChunkUploadPacer.onUpload();
    }

    @Inject(method = "uploadTerrainBuffersToGpu", at = @At("HEAD"), cancellable = true)
    private void myoptimize$paceUploads(CallbackInfo ci) {
        int batch = PerformanceProfiler.get().effectiveChunkUploadBatch(
            ConfigManager.config().chunkUploadBatchPerFrame);
        if (ChunkUploadPacer.shouldDefer(batch)) {
            ci.cancel();
            return;
        }
        myoptimize$uploadStartNanos = System.nanoTime();
    }

    @Inject(method = "uploadTerrainBuffersToGpu", at = @At("RETURN"))
    private void myoptimize$uploadEnd(CallbackInfo ci) {
        if (myoptimize$uploadStartNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.CHUNK_UPLOAD, System.nanoTime() - myoptimize$uploadStartNanos);
            myoptimize$uploadStartNanos = 0;
        }
    }
}
