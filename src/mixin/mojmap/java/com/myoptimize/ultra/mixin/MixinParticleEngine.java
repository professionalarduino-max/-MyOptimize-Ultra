package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import com.myoptimize.ultra.render.ParticleCulling;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Оптимизация частиц — вариант для 26.x (ParticleEngine, mojmap).
 *  1. createParticle — отсечение по cap'у и дистанции ДО создания объекта.
 *  2. tick — замер времени и точный подсчёт частиц (через accessor, раз в тик).
 */
@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {

    @Unique
    private long myoptimize$tickStartNanos;

    @Inject(
        method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"), cancellable = true)
    private void myoptimize$cullSpawn(ParticleOptions parameters, double x, double y, double z,
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
        PerformanceProfiler.get().setParticleCount(
            ((ParticleEngineAccessor) this).myoptimize$countParticles());
    }
}
