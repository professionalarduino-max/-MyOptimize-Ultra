package com.myoptimize.ultra.mixin;

import com.myoptimize.ultra.chunk.ChunkUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Трекинг изменений блоков на клиенте — вариант для 26.x.
 * ClientLevel.setBlock(BlockPos, BlockState, int, int) → boolean объявлен
 * в самом ClientLevel (переопределяет Level.setBlock) — цель миксина корректна.
 * Старое состояние берём в HEAD через getBlockState (return — boolean успеха).
 */
@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"))
    private void myoptimize$trackBlockChange(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                             CallbackInfoReturnable<Boolean> cir) {
        ClientLevel self = (ClientLevel) (Object) this;
        ChunkUpdateTracker.onBlockChanged(self, pos, self.getBlockState(pos), state);
    }
}
