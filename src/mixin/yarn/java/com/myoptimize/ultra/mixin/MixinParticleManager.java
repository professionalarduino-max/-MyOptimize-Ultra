package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import com.myoptimize.ultra.render.ParticleCulling;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Queue;

/**
 * Оптимизация частиц:
 *  1. createParticle — отсечение по cap'у и по дистанции ДО создания объекта
 *     (частица не аллоцируется вообще). Vanilla createParticle и так умеет
 *     возвращать null (нет фабрики), поэтому наш null безопасен.
 *  2. tick — замер времени (KillerFinder) и точный подсчёт частиц
 *     (раз в тик, O(число текстурных листов), не O(частиц)).
 */
@Mixin(ParticleManager.class)
public abstract class MixinParticleManager {

    @Unique
    private long myoptimize$tickStartNanos;

    @Inject(
        method = "createParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$cullSpawn(ParticleEffect parameters, double x, double y, double z,
                                      double velocityX, double velocityY, double velocityZ,
                                      CallbackInfoReturnable<Particle> cir) {
        if (ParticleCulling.shouldDrop(x, y, z)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void myoptimize$tickStart(CallbackInfo ci) {
        myoptimize$tickStartNanos = System.nanoTime();
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void myoptimize$tickEnd(CallbackInfo ci) {
        PerformanceProfiler.get().killerFinder().record(
            KillerFinder.Category.PARTICLE_TICK, System.nanoTime() - myoptimize$tickStartNanos);
        PerformanceProfiler.get().setParticleCount(countParticles());
    }

    @Unique
    private int countParticles() {
        Map<ParticleTextureSheet, Queue<Particle>> particles =
            ((ParticleManagerAccessor) this).myoptimize$getParticles();
        int total = 0;
        for (Queue<Particle> queue : particles.values()) {
            total += queue.size();
        }
        return total;
    }
}
