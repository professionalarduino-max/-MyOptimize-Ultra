package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Culling рендера block entities по дистанции (далёкие сундуки/вывески/
 * рамки не рисуются) + замер времени для "Performance Killer".
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcher {

    @Unique
    private long myoptimize$renderStartNanos;

    @Inject(
        method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$cullAndStart(BlockEntity blockEntity, float tickDelta,
                                         MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         CallbackInfo ci) {
        ModConfig cfg = ConfigManager.config();
        if (cfg.blockEntityCulling && blockEntity != null) {
            double maxDist = cfg.blockEntityCullDistance;
            Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
            BlockPos pos = blockEntity.getPos();
            double dx = pos.getX() + 0.5 - cam.x;
            double dy = pos.getY() + 0.5 - cam.y;
            double dz = pos.getZ() + 0.5 - cam.z;
            if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) {
                ci.cancel();
                return;
            }
        }
        myoptimize$renderStartNanos = System.nanoTime();
    }

    @Inject(
        method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
        at = @At("RETURN"))
    private void myoptimize$renderEnd(BlockEntity blockEntity, float tickDelta,
                                      MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                      CallbackInfo ci) {
        if (myoptimize$renderStartNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.BLOCK_ENTITY_RENDER, System.nanoTime() - myoptimize$renderStartNanos);
            myoptimize$renderStartNanos = 0;
        }
    }
}
