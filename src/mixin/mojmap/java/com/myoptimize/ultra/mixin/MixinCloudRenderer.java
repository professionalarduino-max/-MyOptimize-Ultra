package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.render.CullingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пропуск отрисовки облаков под землёй — вариант для 26.x (CloudRenderer.render).
 */
@Mixin(CloudRenderer.class)
public abstract class MixinCloudRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipCloudsUnderground(CallbackInfo ci) {
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
