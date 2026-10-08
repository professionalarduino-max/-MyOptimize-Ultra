package com.myoptimize.ultra.chunk;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import com.myoptimize.ultra.util.CompactLongSet;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Трекер изменений блоков по секциям чанков (клиент).
 *
 * Зачем: при изменении блока ванилла помечает секцию на ребилд, но нигде
 * не считает "сколько секций трогают за кадр". Эта статистика нужна:
 *  - профилировщику (сколько работы по ребилдам создаёт игра/сервер);
 *  - AdaptiveEngine (всплески изменений = кандидат в "performance killer").
 *
 * Как оптимизируем: ни одного объекта на изменение — только primitive-long
 * ключ секции в CompactLongSet (open addressing). Ребилд одной секции
 * учитывается один раз за кадр, сколько бы блоков в ней ни поменялось.
 *
 * Граничные блоки (x/z кратны 16, y на границе секции) трогают соседнюю
 * секцию — отмечаем и её, чтобы статистика была честной.
 *
 * Точка входа: MixinWorld.setBlockState (только когда world.isClient).
 * Сброс: MixinGameRenderer на границе кадра.
 */
public final class ChunkUpdateTracker {

    private static final CompactLongSet CHANGED_SECTIONS = new CompactLongSet(1024);
    private static int changeCount;

    private ChunkUpdateTracker() {
    }

    public static void onBlockChanged(World world, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!ConfigManager.config().chunkUpdateTracking) {
            return;
        }
        if (oldState.isAir() && newState.isAir()) {
            return; // фиктивное изменение — не трогаем статистику
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        CHANGED_SECTIONS.add(sectionKeyAtBlock(x, y, z));
        int sections = 1;

        if ((x & 15) == 0) {
            CHANGED_SECTIONS.add(sectionKeyAtBlock(x - 1, y, z));
            sections++;
        } else if ((x & 15) == 15) {
            CHANGED_SECTIONS.add(sectionKeyAtBlock(x + 1, y, z));
            sections++;
        }
        if ((y & 15) == 0) {
            CHANGED_SECTIONS.add(sectionKeyAtBlock(x, y - 1, z));
            sections++;
        } else if ((y & 15) == 15) {
            CHANGED_SECTIONS.add(sectionKeyAtBlock(x, y + 1, z));
            sections++;
        }
        if ((z & 15) == 0) {
            CHANGED_SECTIONS.add(sectionKeyAtBlock(x, y, z - 1));
            sections++;
        } else if ((z & 15) == 15) {
            CHANGED_SECTIONS.add(sectionKeyAtBlock(x, y, z + 1));
            sections++;
        }

        changeCount++;
        PerformanceProfiler.get().addBlockChange(sections);
    }

    /** Вызывается на границе кадра (render thread). */
    public static void beginFrame() {
        CHANGED_SECTIONS.clear();
        changeCount = 0;
    }

    public static int changedSections() {
        return CHANGED_SECTIONS.size();
    }

    public static int changeCount() {
        return changeCount;
    }

    private static long sectionKeyAtBlock(int bx, int by, int bz) {
        return sectionKey(bx >> 4, by >> 4, bz >> 4);
    }

    /**
     * Точная (без коллизий) упаковка координат секции в один long:
     * 26 бит на X (±33.5M), 12 бит на Y (с запасом), 26 бит на Z.
     */
    private static long sectionKey(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFFF) << 38)
            | ((long) (sy & 0xFFF) << 26)
            | (sz & 0x3FFFFFF);
    }
}
