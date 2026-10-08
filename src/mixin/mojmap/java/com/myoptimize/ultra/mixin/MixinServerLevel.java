package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.entity.EntityTickThrottler;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Throttling тиков далёких мобов — вариант для 26.x.
 * В 26.x тик сущности (не-пассажира) — ServerLevel.tickNonPassenger.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {

    @Unique
    private long myoptimize$tickStartNanos;

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void myoptimize$throttleEntityTick(Entity entity, CallbackInfo ci) {
        if (EntityTickThrottler.shouldSkip((ServerLevel) (Object) this, entity)) {
            ci.cancel();
            return;
        }
        myoptimize$tickStartNanos = System.nanoTime();
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void myoptimize$tickEnd(Entity entity, CallbackInfo ci) {
        if (myoptimize$tickStartNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.ENTITY_TICK, System.nanoTime() - myoptimize$tickStartNanos);
            myoptimize$tickStartNanos = 0;
        }
    }
}
