package com.myoptimize.ultra.chunk;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import com.myoptimize.ultra.util.CompactLongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Трекинг изменений блоков на клиенте (по секциям чанков) — вариант для 26.x (mojmap).
 *
 * Секция кодируется в один long: Y (4 бита, -30..15 → высота 384) + Z (26 бит) + X (26 бит).
 * Граничные блоки (x/z на краю секции) помечают соседнюю секцию — чтобы ребилд
 * знал про изменения на границе. Всё zero-allocation: счётчики — int, ключи — long.
 */
public final class ChunkUpdateTracker {

    private static final int SECTION_BITS = 4;
    private static final int SECTION_MASK = 0xF;

    private static final CompactLongSet CHANGED_SECTIONS = new CompactLongSet(256);
    private static int blockChanges;

    private ChunkUpdateTracker() {
    }

    public static void beginFrame() {
        if (ConfigManager.config().chunkUpdateTracking) {
            CHANGED_SECTIONS.clear();
        }
        blockChanges = 0;
    }

    /** Вызывается из MixinClientLevel.setBlock (клиент, HEAD). */
    public static void onBlockChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!ConfigManager.config().chunkUpdateTracking) {
            return;
        }
        if (oldState == newState || (oldState.isAir() && newState.isAir())) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int sectionY = (y >> 4) & SECTION_MASK;
        long key = pack(sectionY, x, z);
        CHANGED_SECTIONS.add(key);
        // Граничные блоки: помечаем соседнюю секцию по x/z.
        if ((x & 15) == 0) {
            CHANGED_SECTIONS.add(pack(sectionY, x - 1, z));
        } else if ((x & 15) == 15) {
            CHANGED_SECTIONS.add(pack(sectionY, x + 1, z));
        }
        if ((z & 15) == 0) {
            CHANGED_SECTIONS.add(pack(sectionY, x, z - 1));
        } else if ((z & 15) == 15) {
            CHANGED_SECTIONS.add(pack(sectionY, x, z + 1));
        }
        blockChanges++;
        PerformanceProfiler.get().addBlockChange(1);
    }

    public static int blockChangesThisFrame() {
        return blockChanges;
    }

    public static int changedSectionsThisFrame() {
        return CHANGED_SECTIONS.size();
    }

    public static boolean isSectionChanged(int sectionY, int x, int z) {
        return CHANGED_SECTIONS.contains(pack(sectionY, x, z));
    }

    private static long pack(int sectionY, int x, int z) {
        // Y: 4 бита, Z: 26 бит (sign-safe), X: 26 бит — суммарно 56 бит, помещается в long.
        long lx = x & 0x3FFFFFFL;
        long lz = z & 0x3FFFFFFL;
        return ((long) (sectionY & SECTION_MASK) << 52) | (lz << 26) | lx;
    }
}
