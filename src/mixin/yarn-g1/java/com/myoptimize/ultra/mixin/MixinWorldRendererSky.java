package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.render.CullingUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Вариант для 1.20–1.20.4: renderSky(MatrixStack, Matrix4f, float, Camera, boolean, Runnable).
 * В этих версиях есть ещё и приватный renderSky(BufferBuilder, float) — поэтому
 * хук с полным дескриптором. fogCallback обязан выполниться перед отменой
 * (он выставляет параметры тумана), иначе небо станет чёрным.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRendererSky {

    @Inject(
        method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipSkyUnderground(MatrixStack matrices, Matrix4f matrix4f, float tickDelta,
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
