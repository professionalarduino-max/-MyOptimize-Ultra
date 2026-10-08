package com.myoptimize.ultra.network;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Клиентская оптимизация сетевого взаимодействия.
 *
 * Что реально тормозит: не "протокол", а ВСПЛЕСКИ. Взрыв/ферма/PvP одновременно
 * присылают десятки particle-пакетов, каждый из которых может развернуться в
 * тысячи частиц. Все они обрабатываются в одном тике → spike на main thread.
 *
 * Наше решение (безопасное для протокола — мы не меняем и не задерживаем
 * ничего, кроме cosmetic-пакетов, и не на сервере):
 *  - ParticleS2CPacket ставится в очередь вместо немедленной обработки;
 *  - в конце тика клиента обрабатывается не более N пакетов (N из конфига);
 *  - очередь ограничена: при переполнении лишние particle-пакеты отбрасываются
 *    (частицы — визуал; потеря части из них незаметна, а freeze — заметен).
 *
 * Порядок пакетов внутри категории сохраняется (FIFO). Другие пакеты не трогаем.
 */
public final class NetworkOptimizer {

    private static final Queue<ParticleS2CPacket> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger DROPPED = new AtomicInteger();
    private static volatile boolean bypass = false;

    private NetworkOptimizer() {
    }

    /**
     * Вызывается из MixinClientPlayNetworkHandler.onParticle (HEAD).
     *
     * @return true, если пакет обработан нашим механизмом (оригинал надо отменить).
     */
    public static boolean onParticlePacket(ParticleS2CPacket packet) {
        ModConfig cfg = ConfigManager.config();
        if (!cfg.particlePacketBatching || bypass) {
            return false;
        }
        if (PENDING.size() >= cfg.particlePacketQueueLimit) {
            DROPPED.incrementAndGet();
            return true; // отбрасываем, но считаем
        }
        PENDING.offer(packet);
        PerformanceProfiler.get().setParticlePacketsQueued(PENDING.size());
        return true;
    }

    /** Вызывается из ClientTickEvents.END_CLIENT_TICK (главный поток клиента). */
    public static void drain(MinecraftClient client) {
        ModConfig cfg = ConfigManager.config();
        if (!cfg.particlePacketBatching) {
            flushAll(client);
            return;
        }
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            PENDING.clear();
            return;
        }
        bypass = true;
        try {
            int budget = Math.max(1, cfg.particlePacketsPerTick);
            for (int i = 0; i < budget; i++) {
                ParticleS2CPacket packet = PENDING.poll();
                if (packet == null) {
                    break;
                }
                handler.onParticle(packet);
                PerformanceProfiler.get().addParticlePacketProcessed();
            }
            PerformanceProfiler.get().setParticlePacketsQueued(PENDING.size());
        } finally {
            bypass = false;
        }
    }

    private static void flushAll(MinecraftClient client) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            PENDING.clear();
            return;
        }
        bypass = true;
        try {
            ParticleS2CPacket packet;
            while ((packet = PENDING.poll()) != null) {
                handler.onParticle(packet);
            }
        } finally {
            bypass = false;
        }
    }

    public static int pendingCount() {
        return PENDING.size();
    }

    public static int droppedCount() {
        return DROPPED.get();
    }

    /** Вызывается при отключении от мира — очередь должна очиститься. */
    public static void reset() {
        PENDING.clear();
        DROPPED.set(0);
    }

    /** Защита от рекурсии: true, пока мы сами обрабатываем пакет из очереди. */
    public static boolean isBypassing() {
        return bypass;
    }
}
