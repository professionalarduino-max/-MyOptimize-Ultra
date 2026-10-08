package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.gui.GuiOptimizer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Оптимизация рендера текста:
 *  1. Пустые строки не рисуются (пропуск бесполезной работы и аллокаций).
 *  2. getWidth(String) кэшируется — GUI вызывает его многократно на одних
 *     и тех же строках каждый кадр. Кэш сбрасывается при reload ресурсов
 *     (смена языка/ресурс-пака/шрифта) — см. регистрацию в MyOptimizeUltra.
 */
@Mixin(TextRenderer.class)
public abstract class MixinTextRenderer {

    @Inject(
        method = "draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipEmptyText(String text, float x, float y, int color, boolean shadow,
                                          Matrix4f matrix, VertexConsumerProvider vertexConsumers,
                                          TextRenderer.TextLayerType layerType, int backgroundColor, int light,
                                          CallbackInfoReturnable<Integer> cir) {
        if (GuiOptimizer.shouldSkipEmpty(text)) {
            cir.setReturnValue((int) x);
        }
    }

    @Inject(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void myoptimize$widthCacheGet(String text, CallbackInfoReturnable<Integer> cir) {
        Integer cached = GuiOptimizer.getCachedWidth(text);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getWidth(Ljava/lang/String;)I", at = @At("RETURN"))
    private void myoptimize$widthCachePut(String text, CallbackInfoReturnable<Integer> cir) {
        GuiOptimizer.cacheWidth(text, cir.getReturnValue());
    }
}
