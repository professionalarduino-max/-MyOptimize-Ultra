package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.render.CullingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Вариант для 1.20.5–1.21.1: renderSky(Matrix4f, Matrix4f, float, Camera, boolean, Runnable).
 * fogCallback обязан выполниться перед отменой (он выставляет параметры тумана).
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRendererSky {

    @Inject(
        method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipSkyUnderground(Matrix4f matrix4f, Matrix4f matrix4f2, float tickDelta,
                                               Camera camera, boolean thickFog, Runnable fogCallback,
                                               CallbackInfo ci) {
        if (!ConfigManager.config().undergroundSkySkip) {
            return;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (CullingUtil.isCameraUnderground(world, camera.getPos())) {
            fogCallback.run(); // fog обязан быть настроен даже без отрисовки неба
            ci.cancel();
        }
    }
}
