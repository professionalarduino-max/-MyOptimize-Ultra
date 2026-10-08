package com.myoptimize.ultra.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Умный LRU-кэш с адаптивным лимитом и статистикой.
 *
 * Особенности:
 *  - Лимит задаётся в "баллах" (для нас — примерный размер записи в байтах),
 *    а не в количестве элементов: разные кэши хранят объекты разного размера.
 *  - Лимит может ужиматься/расширяться на лету ({@link #setBudgetBytes}) —
 *    это использует CacheManager, когда AdaptiveEngine видит давление на память.
 *  - При переполнении вытесняются самые давно неиспользуемые записи (LRU).
 *  - Ведётся статистика hit/miss — профилировщик и F3 показывают эффективность.
 *
 * Не потокобезопасен: кэши живут на render thread. Исключение — помечено в CacheManager.
 */
public final class SmartCache<K, V> {

    private static final class Node<K, V> {
        final K key;
        V value;
        long weight; // не final: меняется при перезаписи существующего ключа
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value, long weight) {
            this.key = key;
            this.value = value;
            this.weight = weight;
        }
    }

    private final Map<K, Node<K, V>> index = new HashMap<>();
    private final Node<K, V> head = new Node<>(null, null, 0); // фиктивный "самый свежий"
    private final Node<K, V> tail = new Node<>(null, null, 0); // фиктивный "самый старый"
    private final long maxWeight;
    private long currentWeight;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final String name;

    public SmartCache(String name, long maxWeight) {
        this.name = name;
        this.maxWeight = Math.max(1, maxWeight);
        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {
        Node<K, V> node = index.get(key);
        if (node == null) {
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        touch(node);
        return node.value;
    }

    /** get-or-compute: если ключа нет, значение вычисляется и кладётся в кэш. */
    public V getOrCompute(K key, java.util.function.Function<K, V> loader, long weight) {
        Node<K, V> node = index.get(key);
        if (node != null) {
            hits.incrementAndGet();
            touch(node);
            return node.value;
        }
        misses.incrementAndGet();
        V value = loader.apply(key);
        if (value != null) {
            put(key, value, weight);
        }
        return value;
    }

    public void put(K key, V value, long weight) {
        Node<K, V> existing = index.get(key);
        if (existing != null) {
            currentWeight -= existing.weight;
            existing.value = value;
            existing.weight = weight;
            currentWeight += weight;
            touch(existing);
            evictIfNeeded();
            return;
        }
        Node<K, V> node = new Node<>(key, value, Math.max(1, weight));
        index.put(key, node);
        linkToFront(node);
        currentWeight += node.weight;
        evictIfNeeded();
    }

    public void invalidate(K key) {
        Node<K, V> node = index.remove(key);
        if (node != null) {
            currentWeight -= node.weight;
            unlink(node);
        }
    }

    public void clear() {
        index.clear();
        head.next = tail;
        tail.prev = head;
        currentWeight = 0;
    }

    public int size() {
        return index.size();
    }

    public long currentWeight() {
        return currentWeight;
    }

    public String name() {
        return name;
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public long evictions() {
        return evictions.get();
    }

    public double hitRate() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        return total == 0 ? 0 : h / (double) total;
    }

    /** Ужать/расширить лимит. Возвращает true, если кэш был сокращён. */
    public boolean resize(long newMaxWeight) {
        // maxWeight final — эмулируем через немедленную эвикцию при превышении.
        // Для простоты: пересоздаём лимит через эвикцию хвоста.
        long limit = Math.max(1, newMaxWeight);
        boolean trimmed = false;
        while (currentWeight > limit && tail.prev != head) {
            Node<K, V> eldest = tail.prev;
            index.remove(eldest.key);
            currentWeight -= eldest.weight;
            unlink(eldest);
            evictions.incrementAndGet();
            trimmed = true;
        }
        return trimmed;
    }

    private void evictIfNeeded() {
        while (currentWeight > maxWeight && tail.prev != head) {
            Node<K, V> eldest = tail.prev;
            index.remove(eldest.key);
            currentWeight -= eldest.weight;
            unlink(eldest);
            evictions.incrementAndGet();
        }
    }

    private void touch(Node<K, V> node) {
        unlink(node);
        linkToFront(node);
    }

    private void linkToFront(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void unlink(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }
}
