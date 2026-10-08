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
 * Вариант для 1.20–1.20.4: в этих версиях у WorldRenderer ровно один renderClouds
 * (старый сигнатурный вариант с BufferBuilder), поэтому хук по имени без дескриптора.
 * Позиция камеры берётся из gameRenderer — аргументы метода не нужны.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRendererClouds {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
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
