package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.gui.F3OverlayRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Красивый F3: стилизованные панели за текстом + наша панель мониторинга
 * под левым текстом (FPS, frametime-график, 1% low, RAM, сущности, killer).
 *
 * При f3Style=vanilla — ничего не меняется, F3 выглядит как в ванилле.
 */
@Mixin(DebugHud.class)
public abstract class MixinDebugHud {

    @Inject(method = "drawLeftText", at = @At("HEAD"))
    private void myoptimize$panelBehindLeftText(DrawContext context, CallbackInfo ci) {
        if (ConfigManager.config().isUltraF3()) {
            F3OverlayRenderer.drawPanelBehindLeftText(context, (DebugHud) (Object) this);
        }
    }

    @Inject(method = "drawLeftText", at = @At("RETURN"))
    private void myoptimize$drawDashboard(DrawContext context, CallbackInfo ci) {
        if (ConfigManager.config().isUltraF3()) {
            F3OverlayRenderer.drawDashboard(context, (DebugHud) (Object) this);
        }
    }

    @Inject(method = "drawRightText", at = @At("HEAD"))
    private void myoptimize$panelBehindRightText(DrawContext context, CallbackInfo ci) {
        if (ConfigManager.config().isUltraF3()) {
            F3OverlayRenderer.drawPanelBehindRightText(context, (DebugHud) (Object) this);
        }
    }
}
