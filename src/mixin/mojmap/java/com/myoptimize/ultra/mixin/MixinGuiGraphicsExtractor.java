package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.gui.GuiOptimizer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пропуск отрисовки пустых строк — вариант для 26.x.
 * В 26.x текст рисуется через GuiGraphicsExtractor.text(Font, String, x, y, color, shadow).
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinGuiGraphicsExtractor {

    @Inject(
        method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipEmptyText(Font font, String text, int x, int y, int color, boolean shadow,
                                          CallbackInfo ci) {
        if (GuiOptimizer.shouldSkipEmpty(text)) {
            ci.cancel();
        }
    }
}
