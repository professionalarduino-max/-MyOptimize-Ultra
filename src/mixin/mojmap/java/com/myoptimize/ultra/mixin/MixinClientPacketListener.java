package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.network.NetworkOptimizer;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Клиентская сеть — вариант для 26.x (ClientPacketListener, mojmap).
 *  1. Батчинг пакетов частиц (handleParticleEvent).
 *  2. Замер времени обработки light-пакетов (handleLightUpdatePacket).
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Unique
    private long myoptimize$lightStartNanos;

    @Inject(
        method = "handleParticleEvent(Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket;)V",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$batchParticlePacket(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (NetworkOptimizer.isBypassing()) {
            return; // ручная обработка из нашей очереди — не перехватываем
        }
        if (NetworkOptimizer.onParticlePacket(packet)) {
            ci.cancel();
        }
    }

    @Inject(
        method = "handleLightUpdatePacket(Lnet/minecraft/network/protocol/game/ClientboundLightUpdatePacket;)V",
        at = @At("HEAD"))
    private void myoptimize$lightStart(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        myoptimize$lightStartNanos = System.nanoTime();
    }

    @Inject(
        method = "handleLightUpdatePacket(Lnet/minecraft/network/protocol/game/ClientboundLightUpdatePacket;)V",
        at = @At("RETURN"))
    private void myoptimize$lightEnd(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        if (myoptimize$lightStartNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.LIGHTING, System.nanoTime() - myoptimize$lightStartNanos);
            myoptimize$lightStartNanos = 0;
        }
    }
}
