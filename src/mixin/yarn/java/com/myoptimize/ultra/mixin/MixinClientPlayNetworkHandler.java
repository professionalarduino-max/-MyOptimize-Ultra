package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.network.NetworkOptimizer;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Клиентская сеть:
 *  1. Батчинг particle-пакетов (всплески частиц размазываются по тикам).
 *  2. Замер времени обработки light-пакетов для "Performance Killer".
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler {

    @Unique
    private long myoptimize$lightStartNanos;

    @Inject(method = "onParticle(Lnet/minecraft/network/packet/s2c/play/ParticleS2CPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$batchParticlePacket(ParticleS2CPacket packet, CallbackInfo ci) {
        if (NetworkOptimizer.isBypassing()) {
            return; // ручная обработка из нашей очереди — не перехватываем
        }
        if (NetworkOptimizer.onParticlePacket(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "onLightUpdate(Lnet/minecraft/network/packet/s2c/play/LightUpdateS2CPacket;)V", at = @At("HEAD"))
    private void myoptimize$lightStart(LightUpdateS2CPacket packet, CallbackInfo ci) {
        myoptimize$lightStartNanos = System.nanoTime();
    }

    @Inject(method = "onLightUpdate(Lnet/minecraft/network/packet/s2c/play/LightUpdateS2CPacket;)V", at = @At("RETURN"))
    private void myoptimize$lightEnd(LightUpdateS2CPacket packet, CallbackInfo ci) {
        if (myoptimize$lightStartNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.LIGHTING, System.nanoTime() - myoptimize$lightStartNanos);
            myoptimize$lightStartNanos = 0;
        }
    }
}
