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
 * Вариант для 1.20.5–1.21.1: renderClouds(MatrixStack, Matrix4f, Matrix4f, float, double, double, double).
 * В 1.20.5–1.20.6 у WorldRenderer два метода renderClouds (старый + новый),
 * поэтому хук с полным дескриптором — попадает в новый (рабочий).
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRendererClouds {

    @Inject(
        method = "renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipCloudsUnderground(CallbackInfo ci) {
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
