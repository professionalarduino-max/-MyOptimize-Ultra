package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.gui.GuiOptimizer;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Кэш ширины текста — вариант для 26.x (Font.width(String)).
 * В 26.x отрисовка текста ушла в GuiGraphicsExtractor, но ширина считается
 * так же — кэш validité. Кэш сбрасывается при reload ресурсов.
 */
@Mixin(Font.class)
public abstract class MixinFont {

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void myoptimize$widthCacheGet(String text, CallbackInfoReturnable<Integer> cir) {
        Integer cached = GuiOptimizer.getCachedWidth(text);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("RETURN"))
    private void myoptimize$widthCachePut(String text, CallbackInfoReturnable<Integer> cir) {
        GuiOptimizer.cacheWidth(text, cir.getReturnValue());
    }
}
