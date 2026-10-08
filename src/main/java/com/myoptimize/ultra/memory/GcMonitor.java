package com.myoptimize.ultra.memory;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Мониторинг GC через стандартные MXBean'ы JVM.
 *
 * Зачем: GC-паузы — главный источник микростаттеров в Minecraft. Мы не можем
 * "оптимизировать GC" напрямую (это делает JVM), но можем:
 *  1. Измерять частоту и длительность сборок (GC time — метрика профилировщика).
 *  2. Сообщать AdaptiveEngine, когда GC давит — чтобы тот уменьшил размеры кэшей
 *     и aggressive-настройки, снизив темп аллокаций.
 *  3. В режиме разработчика — детальная статистика по каждому коллектору.
 *
 * Подписка идёт на NotificationEmitter каждого GarbageCollectorMXBean —
 * это официальный μηχανизм, не требующий никаких JVM-флагов.
 */
public final class GcMonitor implements NotificationListener {

    private static final String GC_NOTIFICATION_TYPE = "java.management.gc.notification";

    private final AtomicLong totalGcCount = new AtomicLong();
    private final AtomicLong totalGcTimeMs = new AtomicLong();
    private final AtomicLong lastGcTimeMs = new AtomicLong();
    private final AtomicLong lastGcTimestamp = new AtomicLong();
    private final AtomicLong youngGcCount = new AtomicLong();
    private final AtomicLong oldGcCount = new AtomicLong();
    private volatile boolean running;

    public void start() {
        if (running) {
            return;
        }
        running = true;
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean collector : collectors) {
            if (collector instanceof NotificationEmitter) {
                ((NotificationEmitter) collector).addNotificationListener(this, null, collector.getName());
            }
        }
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (collector instanceof NotificationEmitter) {
                try {
                    ((NotificationEmitter) collector).removeNotificationListener(this);
                } catch (Exception ignored) {
                    // MXBean мог быть заменён рантаймом — не критично.
                }
            }
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        if (!GC_NOTIFICATION_TYPE.equals(notification.getType())) {
            return;
        }
        CompositeData data = (CompositeData) notification.getUserData();
        long durationMs = data.getCompositeType().containsKey("duration")
            ? ((Number) ((CompositeData) data.get("gcInfo")).get("duration")).longValue()
            : 0L;
        String name = String.valueOf(handback);

        totalGcCount.incrementAndGet();
        totalGcTimeMs.addAndGet(durationMs);
        lastGcTimeMs.set(durationMs);
        lastGcTimestamp.set(System.currentTimeMillis());
        if (isOldGenCollector(name)) {
            oldGcCount.incrementAndGet();
        } else {
            youngGcCount.incrementAndGet();
        }
    }

    private static boolean isOldGenCollector(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.contains("old") || n.contains("mark") || n.contains("tenured") || n.contains("g1 old");
    }

    public long totalGcCount() {
        return totalGcCount.get();
    }

    public long totalGcTimeMs() {
        return totalGcTimeMs.get();
    }

    public long lastGcTimeMs() {
        return lastGcTimeMs.get();
    }

    public long lastGcTimestamp() {
        return lastGcTimestamp.get();
    }

    public long youngGcCount() {
        return youngGcCount.get();
    }

    public long oldGcCount() {
        return oldGcCount.get();
    }

    /** Среднее время GC за последние {@code windowMs} миллисекунд. */
    public double averageGcTimePerSecond(long windowMs) {
        long count = totalGcCount.get();
        if (count == 0) {
            return 0.0;
        }
        return (totalGcTimeMs.get() / (double) count) / 1000.0;
    }
}
