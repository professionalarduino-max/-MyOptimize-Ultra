package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.entity.EntityCuller;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Culling рендера сущностей по дистанции (item/XP сущности всегда,
 * остальные — в агрессивном режиме) + замер времени для "Performance Killer".
 *
 * приятный побочный эффект: dispatcher.render вызывается и в shadow pass,
 * поэтому culling уменьшает и отрисовку теней.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Unique
    private long myoptimize$renderStartNanos;

    @Inject(
        method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$cullAndStart(Entity entity, double x, double y, double z, float yaw, float tickDelta,
                                         MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                         CallbackInfo ci) {
        if (EntityCuller.shouldCull((EntityRenderDispatcher) (Object) this, entity)) {
            ci.cancel();
            return;
        }
        myoptimize$renderStartNanos = System.nanoTime();
    }

    @Inject(
        method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("RETURN"))
    private void myoptimize$renderEnd(Entity entity, double x, double y, double z, float yaw, float tickDelta,
                                      MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                      CallbackInfo ci) {
        if (myoptimize$renderStartNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.ENTITY_RENDER, System.nanoTime() - myoptimize$renderStartNanos);
            myoptimize$renderStartNanos = 0;
        }
    }
}
