package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.world.LightmapCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Общая (для всех версий 1.20–1.21.11) часть оптимизаций рендера мира:
 *  - renderWeather не рисуется, когда камера под землёй (туман всё скрывает).
 *    Хук без аргументов: позиция камеры берётся из gameRenderer, а имя метода
 *    стабильно во всех версиях.
 *  - кэш lightmap-координат на кадр (hot path для block entities/сущностей).
 *
 * renderClouds/renderSky вынесены в era-варианты (их сигнатуры менялись
 * между версиями): см. src/mixin/yarn-g1, yarn-g2, yarn-g34.
 */
@Mixin(net.minecraft.client.render.WorldRenderer.class)
public abstract class MixinWorldRenderer {

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void myoptimize$skipWeatherUnderground(CallbackInfo ci) {
        com.myoptimize.ultra.config.ModConfig cfg = com.myoptimize.ultra.config.ConfigManager.config();
        if (!cfg.undergroundSkySkip) {
            return;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (com.myoptimize.ultra.render.CullingUtil.isCameraUnderground(client.world,
            client.gameRenderer.getCamera().getPos())) {
            ci.cancel();
        }
    }

    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
        at = @At("HEAD"), cancellable = true)
    private static void myoptimize$lightmapCacheGet(BlockRenderView world, BlockPos pos,
                                                    CallbackInfoReturnable<Integer> cir) {
        int cached = LightmapCache.get(pos);
        if (cached >= 0) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I",
        at = @At("RETURN"))
    private static void myoptimize$lightmapCachePut(BlockRenderView world, BlockPos pos,
                                                    CallbackInfoReturnable<Integer> cir) {
        LightmapCache.put(pos, cir.getReturnValue());
    }
}
