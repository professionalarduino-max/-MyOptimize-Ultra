package com.myoptimize.ultra.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.myoptimize.ultra.render.TextureBindOptimizer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dedupe bind'ов текстур: повторная привязка той же текстуры на тот же
 * юнит пропускается. Все пути bind'а синхронизируют наш кэш:
 * setShaderTexture(int,int), setShaderTexture(int,Identifier),
 * bindTexture(int) + activeTexture(int).
 *
 * Отключается в shader safe mode (Iris/Oculus/VulkanMod) — см. TextureBindOptimizer.
 */
@Mixin(RenderSystem.class)
public abstract class MixinRenderSystem {

    @Inject(method = "setShaderTexture(II)V", at = @At("HEAD"), cancellable = true)
    private static void myoptimize$dedupeUnitBind(int slot, int glId, CallbackInfo ci) {
        if (TextureBindOptimizer.shouldSkipUnitBind(slot, glId)) {
            ci.cancel();
        }
    }

    @Inject(method = "setShaderTexture(ILnet/minecraft/util/Identifier;)V", at = @At("HEAD"), cancellable = true)
    private static void myoptimize$dedupeIdentifierBind(int slot, Identifier id, CallbackInfo ci) {
        if (TextureBindOptimizer.resolveAndCheck(slot, id) >= 0) {
            ci.cancel();
        }
    }

    @Inject(method = "bindTexture(I)V", at = @At("HEAD"), cancellable = true)
    private static void myoptimize$dedupeActiveBind(int glId, CallbackInfo ci) {
        if (TextureBindOptimizer.shouldSkipActiveBind(glId)) {
            ci.cancel();
        }
    }

    @Inject(method = "activeTexture(I)V", at = @At("HEAD"))
    private static void myoptimize$trackActiveUnit(int texture, CallbackInfo ci) {
        TextureBindOptimizer.onActiveTexture(texture);
    }
}
