package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.render.CullingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Вариант для 1.21.2–1.21.11: renderSky(FrameGraphBuilder, Camera, float, Fog/GpuBufferSlice).
 * Fog сюда передаётся готовым объектом (вычисляется снаружи), поэтому отменять
 * отрисовку безопасно без каких-либо callback'ов — хук без аргументов.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRendererSky {

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipSkyUnderground(CallbackInfo ci) {
        if (!ConfigManager.config().undergroundSkySkip) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        Vec3d cam = client.gameRenderer.getCamera().getPos();
        if (CullingUtil.isCameraUnderground(world, cam)) {
            ci.cancel();
        }
    }
}
