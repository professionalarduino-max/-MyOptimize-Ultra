package com.myoptimize.ultra.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleLimit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Доступ к счётчикам частиц ParticleEngine (26.x): trackedParticleCounts —
 * суммарное число живых частиц по лимитам. O(число лимитов), не O(частиц).
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {

    @Accessor("trackedParticleCounts")
    Object2IntOpenHashMap<ParticleLimit> myoptimize$trackedCounts();

    @Unique
    default int myoptimize$countParticles() {
        int total = 0;
        for (int v : myoptimize$trackedCounts().values()) {
            total += v;
        }
        return total;
    }
}
