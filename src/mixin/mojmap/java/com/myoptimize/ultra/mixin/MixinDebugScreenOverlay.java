package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.gui.F3OverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Красивый F3 — вариант для 26.x.
 * В 26.x F3 — модульная система DebugScreenEntries, а отрисовка —
 * DebugScreenOverlay.extractRenderState(GuiGraphicsExtractor). Наша панель
 * мониторинга рисуется после ванильного оверлея (внизу слева).
 *
 * При f3Style=vanilla — ничего не меняется, F3 выглядит как в ванилле.
 */
@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void myoptimize$drawDashboard(GuiGraphicsExtractor extractor, CallbackInfo ci) {
        if (ConfigManager.config().isUltraF3()) {
            F3OverlayRenderer.drawDashboard(extractor);
        }
    }
}
