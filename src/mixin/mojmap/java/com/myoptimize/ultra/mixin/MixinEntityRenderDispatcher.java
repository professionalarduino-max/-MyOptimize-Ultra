package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.entity.EntityCuller;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Culling сущностей по дистанции — вариант для 26.x.
 *
 * В 26.x отрисовка сущностей идёт через render-state пайплайн: сперва
 * shouldRender(E, Frustum, x, y, z, tickDelta) (frustum-проверка), потом
 * extractEntity + submit. shouldRender — идеальная точка для distance-culling:
 * отсечённая сущность не попадает в extract/submit вовсе.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Unique
    private long myoptimize$startNanos;

    @Inject(
        method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$cullAndStart(Entity entity, Frustum frustum, double x, double y, double z,
                                         float tickDelta, CallbackInfoReturnable<Boolean> cir) {
        if (EntityCuller.shouldCull(entity)) {
            cir.setReturnValue(false);
            return;
        }
        myoptimize$startNanos = System.nanoTime();
    }

    @Inject(
        method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
        at = @At("RETURN"))
    private void myoptimize$end(Entity entity, Frustum frustum, double x, double y, double z,
                                float tickDelta, CallbackInfoReturnable<Boolean> cir) {
        if (myoptimize$startNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.ENTITY_RENDER, System.nanoTime() - myoptimize$startNanos);
            myoptimize$startNanos = 0;
        }
    }
}
