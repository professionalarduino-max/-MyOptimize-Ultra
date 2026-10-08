package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.MyOptimizeUltra;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Точка входа на границу кадра: Ultra Frame Scheduler, профилировщик,
 * инвалидация per-frame кэшей, сброс счётчиков.
 *
 * renderWorld вызывается ровно один раз на кадр на render thread — идеальное
 * место, чтобы "начать новый кадр". Хук без аргументов: сигнатура renderWorld
 * менялась между версиями (1.20.1: (float, long, MatrixStack), 1.20.5: (float, long),
 * 1.21.2+: (RenderTickCounter)), но имя стабильно и метод один — так что миксин
 * общий для всех версий 1.20–1.21.11.
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void myoptimize$onFrameStart(CallbackInfo ci) {
        MyOptimizeUltra.onFrameStart();
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void myoptimize$onFrameEnd(CallbackInfo ci) {
        MyOptimizeUltra.onFrameEnd();
    }
}
