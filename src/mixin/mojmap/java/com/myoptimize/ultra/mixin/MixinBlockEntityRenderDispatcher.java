package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Culling block entities по дистанции — вариант для 26.x.
 * В 26.x block entities идут через tryExtractRenderState (nullable результат) —
 * отсечённый block entity не попадает в submit.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcher {

    @Unique
    private long myoptimize$startNanos;

    @Inject(
        method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$cullAndStart(BlockEntity blockEntity, float tickDelta,
                                         ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, boolean bl,
                                         CallbackInfoReturnable<?> cir) {
        ModConfig cfg = ConfigManager.config();
        if (cfg.blockEntityCulling && blockEntity != null) {
            double maxDist = cfg.blockEntityCullDistance;
            Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
            var pos = blockEntity.getBlockPos();
            double dx = pos.getX() + 0.5 - cam.x;
            double dy = pos.getY() + 0.5 - cam.y;
            double dz = pos.getZ() + 0.5 - cam.z;
            if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) {
                cir.setReturnValue(null);
                return;
            }
        }
        myoptimize$startNanos = System.nanoTime();
    }

    @Inject(
        method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
        at = @At("RETURN"))
    private void myoptimize$end(BlockEntity blockEntity, float tickDelta,
                                ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, boolean bl,
                                CallbackInfoReturnable<?> cir) {
        if (myoptimize$startNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.BLOCK_ENTITY_RENDER, System.nanoTime() - myoptimize$startNanos);
            myoptimize$startNanos = 0;
        }
    }
}
