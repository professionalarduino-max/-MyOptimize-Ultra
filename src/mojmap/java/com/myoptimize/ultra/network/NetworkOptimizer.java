package com.myoptimize.ultra.network;

import com.myoptimize.ultra.config.ConfigManager;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.profiler.PerformanceProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Оптимизация обработки сетевых пакетов на клиенте — вариант для 26.x (mojmap).
 *
 * Единственная «агрессивная» оптимизация: батчинг пакетов частиц. Всплеск частиц
 * (взрыв, ферма) — десятки пакетов в одном тике; мы размазываем их по тикам.
 * Протокол не меняется, порядок пакетов сохраняется, очередь ограничена.
 */
public final class NetworkOptimizer {

    private static final Queue<ClientboundLevelParticlesPacket> PARTICLE_QUEUE = new ArrayDeque<>();
    private static boolean bypass;

    private NetworkOptimizer() {
    }

    /**
     * @return true, если пакет нужно отложить (встать в очередь).
     */
    public static boolean onParticlePacket(ClientboundLevelParticlesPacket packet) {
        ModConfig cfg = ConfigManager.config();
        if (!cfg.particlePacketBatching) {
            return false;
        }
        synchronized (PARTICLE_QUEUE) {
            if (PARTICLE_QUEUE.size() >= cfg.particlePacketQueueLimit) {
                return true; // очередь полная — пакет просто пропускаем (частицы не критичны)
            }
            PARTICLE_QUEUE.add(packet);
            PerformanceProfiler.get().setParticlePacketsQueued(PARTICLE_QUEUE.size());
        }
        return true;
    }

    /** Вызывается каждый тик: обрабатывает не более N пакетов из очереди. */
    public static void drain(Minecraft client) {
        ModConfig cfg = ConfigManager.config();
        if (!cfg.particlePacketBatching) {
            return;
        }
        ClientPacketListener handler = client.getConnection();
        if (handler == null) {
            return;
        }
        int budget = Math.max(1, cfg.particlePacketsPerTick);
        for (int i = 0; i < budget; i++) {
            ClientboundLevelParticlesPacket packet;
            synchronized (PARTICLE_QUEUE) {
                packet = PARTICLE_QUEUE.poll();
                PerformanceProfiler.get().setParticlePacketsQueued(PARTICLE_QUEUE.size());
            }
            if (packet == null) {
                return;
            }
            bypass = true;
            try {
                handler.handleParticleEvent(packet);
            } finally {
                bypass = false;
            }
            PerformanceProfiler.get().addParticlePacketProcessed();
        }
    }

    /** Защита от рекурсии при ручной обработке пакетов из очереди. */
    public static boolean isBypassing() {
        return bypass;
    }
}
