package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.render.CullingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пропуск отрисовки погоды под землёй — вариант для 26.x (WeatherEffectRenderer.render).
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class MixinWeatherEffectRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipWeatherUnderground(CallbackInfo ci) {
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
