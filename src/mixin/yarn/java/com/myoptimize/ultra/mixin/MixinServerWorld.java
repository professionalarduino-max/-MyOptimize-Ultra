package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.entity.EntityTickThrottler;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Throttling tick'ов далёких мобов (только integrated server — в синглплеере).
 * Замер времени тика сущностей для "Performance Killer".
 */
@Mixin(ServerWorld.class)
public abstract class MixinServerWorld {

    @Unique
    private long myoptimize$tickStartNanos;

    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void myoptimize$throttleEntityTick(Entity entity, CallbackInfo ci) {
        if (EntityTickThrottler.shouldSkip((ServerWorld) (Object) this, entity)) {
            ci.cancel();
            return;
        }
        myoptimize$tickStartNanos = System.nanoTime();
    }

    @Inject(method = "tickEntity", at = @At("RETURN"))
    private void myoptimize$tickEnd(Entity entity, CallbackInfo ci) {
        if (myoptimize$tickStartNanos != 0) {
            PerformanceProfiler.get().killerFinder().record(
                KillerFinder.Category.ENTITY_TICK, System.nanoTime() - myoptimize$tickStartNanos);
            myoptimize$tickStartNanos = 0;
        }
    }
}
