package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.render.CullingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пропуск отрисовки неба под землёй — вариант для 26.x.
 * В 26.x небо рисует отдельный SkyRenderer; render(GpuBufferSlice, SkyRenderState)
 * получает готовое состояние — отменять безопасно, fog формируется снаружи.
 */
@Mixin(SkyRenderer.class)
public abstract class MixinSkyRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipSkyUnderground(CallbackInfo ci) {
        if (!ConfigManager.config().undergroundSkySkip) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Vec3 cam = client.gameRenderer.mainCamera().position();
        if (CullingUtil.isCameraUnderground(level, cam)) {
            ci.cancel();
        }
    }
}
