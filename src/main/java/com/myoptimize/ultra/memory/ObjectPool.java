package com.myoptimize.ultra.memory;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/**
 * Пул переиспользуемых объектов.
 *
 * Зачем: в per-frame hot path (снапшоты профилировщика, задачи планировщика)
 * нельзя аллоцировать новый объект каждый кадр — это прямой вклад в GC pressure
 * и микростаттеры. Пул исключает аллокации в steady state: объект берётся из
 * очереди, сбрасывается через reset-callback и возвращается обратно.
 *
 * Потокобезопасность: не требуется — пул используется только с render/main thread.
 * Если понадобится кросс-поточность — оборачивайте в synchronized извне.
 */
public final class ObjectPool<T> {

    private final ArrayDeque<T> free;
    private final Consumer<T> resetter;
    private final int maxSize;
    private int created;
    private int reused;

    public ObjectPool(int initialCapacity, int maxSize, java.util.function.Supplier<T> factory, Consumer<T> resetter) {
        this.free = new ArrayDeque<>(initialCapacity);
        this.maxSize = maxSize;
        this.resetter = resetter;
        for (int i = 0; i < initialCapacity; i++) {
            free.add(factory.get());
            created++;
        }
    }

    public T acquire() {
        T obj = free.pollFirst();
        if (obj == null) {
            obj = newInstance();
        } else {
            reused++;
        }
        return obj;
    }

    public void release(T obj) {
        if (obj == null) {
            return;
        }
        resetter.accept(obj);
        if (free.size() < maxSize) {
            free.addFirst(obj);
        }
    }

    private T newInstance() {
        // Фабрика хранится неявно: мы не можем создать объект без supplier,
        // поэтому acquire() без свободных объектов делегируется сюда.
        throw new IllegalStateException(
            "ObjectPool: пул исчерпан и не задан fallback-supplier. " +
            "Используйте acquireOrCreate(Supplier) в кросс-поточном/нештатном пути.");
    }

    /**
     * Вариант для редких внештатных запросов: если пул пуст — создаём новый объект,
     * но не кладём его в пул (чтобы не разрастать пул единичными пиками).
     */
    public T acquireOrCreate(java.util.function.Supplier<T> fallback) {
        T obj = free.pollFirst();
        if (obj == null) {
            created++;
            return fallback.get();
        }
        reused++;
        return obj;
    }

    public int createdCount() {
        return created;
    }

    public int reusedCount() {
        return reused;
    }

    public int pooledCount() {
        return free.size();
    }
}
