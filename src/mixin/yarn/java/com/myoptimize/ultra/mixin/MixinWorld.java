package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.chunk.ChunkUpdateTracker;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ModifiableWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Трекинг изменений блоков на клиенте (по секциям чанков).
 *
 * ВАЖНО (верифицировано по yarn для 1.20–1.21.11): 4-аргументный
 * setBlockState(BlockPos, BlockState, int, int) → boolean объявлен в интерфейсе
 * ModifiableWorld (default-метод), а не в World. Поэтому цель миксина — интерфейс.
 *
 * Старое состояние берём в HEAD через getBlockState(pos) — return value тут
 * boolean (успех), а не старое состояние. Регистрация — сразу в HEAD: трекер
 * хранит только ключи секций, поэтому попадание "лишней" записи при неудачном
 * setBlockState безвредно.
 */
@Mixin(ModifiableWorld.class)
public abstract class MixinWorld {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"))
    private void myoptimize$trackBlockChange(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                             CallbackInfoReturnable<Boolean> cir) {
        World self = (World) (Object) this;
        if (self.isClient) {
            ChunkUpdateTracker.onBlockChanged(self, pos, self.getBlockState(pos), state);
        }
    }
}
