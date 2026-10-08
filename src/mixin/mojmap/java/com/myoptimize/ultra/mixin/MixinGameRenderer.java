package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.MyOptimizeUltra;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Граница кадра для 26.x: GameRenderer.renderLevel() (без аргументов в 26.x).
 * renderLevel вызывается ровно один раз на кадр — идеальная точка входа.
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void myoptimize$onFrameStart(CallbackInfo ci) {
        MyOptimizeUltra.onFrameStart();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void myoptimize$onFrameEnd(CallbackInfo ci) {
        MyOptimizeUltra.onFrameEnd();
    }
}
