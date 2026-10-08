package com.myoptimize.ultra.profiler;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * "Find Performance Killer": профилирует, какая подсистема сколько времени
 * съела за последний кадр, и отвечает на вопрос "что меня тормозит прямо сейчас?".
 *
 * Потокобезопасен: record() вызывается с разных потоков (render / server / network),
 * beginFrame() — на границе кадра с render thread.
 */
public final class KillerFinder {

    /** Подсистемы, по которым считаем время. displayName — для F3. */
    public enum Category {
        ENTITY_TICK("Entity tick"),
        ENTITY_RENDER("Entity render"),
        BLOCK_ENTITY_RENDER("Block entity render"),
        PARTICLE_TICK("Particles"),
        CHUNK_UPLOAD("Chunk uploads"),
        LIGHTING("Lighting"),
        TEXT_RENDER("Text render");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** Главный тормоз последнего кадра. */
    public static final class TopKiller {
        public final Category category;
        public final double share;   // 0..1 — доля от суммарного времени категорий
        public final double millis;  // среднее время категории за кадр

        TopKiller(Category category, double share, double millis) {
            this.category = category;
            this.share = share;
            this.millis = millis;
        }
    }

    private final ConcurrentHashMap<Category, AtomicLong> currentFrame = new ConcurrentHashMap<>();
    private volatile Map<Category, Long> lastFrame = Map.of();

    /** Учёт наносекунд в категории. Можно вызывать с любого потока. */
    public void record(Category category, long nanos) {
        if (nanos <= 0) {
            return;
        }
        currentFrame.computeIfAbsent(category, k -> new AtomicLong()).addAndGet(nanos);
    }

    /** Завершить кадр: текущие накопления становятся "последним кадром". */
    public void beginFrame() {
        Map<Category, Long> done = new EnumMap<>(Category.class);
        for (Map.Entry<Category, AtomicLong> entry : currentFrame.entrySet()) {
            done.put(entry.getKey(), entry.getValue().getAndSet(0));
        }
        lastFrame = done;
    }

    /** Самая "дорогая" категория последнего кадра. */
    public TopKiller topKiller() {
        Map<Category, Long> frame = lastFrame;
        long total = 0;
        Category top = null;
        long topNanos = 0;
        for (Map.Entry<Category, Long> entry : frame.entrySet()) {
            total += entry.getValue();
            if (entry.getValue() > topNanos) {
                topNanos = entry.getValue();
                top = entry.getKey();
            }
        }
        if (top == null || total <= 0) {
            return new TopKiller(Category.ENTITY_TICK, 0, 0);
        }
        return new TopKiller(top, (double) topNanos / total, topNanos / 1_000_000.0);
    }

    /** Время категории за последний кадр, мс. */
    public double categoryMillis(Category category) {
        Long nanos = lastFrame.get(category);
        return nanos == null ? 0 : nanos / 1_000_000.0;
    }

    /** Сумма по всем категориям за последний кадр, мс. */
    public double lastFrameTotalMillis() {
        long total = 0;
        for (long v : lastFrame.values()) {
            total += v;
        }
        return total / 1_000_000.0;
    }

    /** Все категории последнего кадра, отсортированные по убыванию времени. */
    public List<Map.Entry<Category, Double>> sortedCategories() {
        List<Map.Entry<Category, Double>> out = new ArrayList<>();
        for (Map.Entry<Category, Long> entry : lastFrame.entrySet()) {
            out.add(Map.entry(entry.getKey(), entry.getValue() / 1_000_000.0));
        }
        out.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return out;
    }
}
