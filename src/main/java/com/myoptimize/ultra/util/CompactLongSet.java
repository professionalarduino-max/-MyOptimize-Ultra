package com.myoptimize.ultra.util;

/**
 * Компактное множество примитивных long на открытой адресации.
 *
 * Зачем оно здесь: vanilla активно использует fastutil-коллекции в hot-path
 * (чанки, свет, частицы). Нашим подсистемам (трекинг изменённых секций чанков,
 * дедупликация light-check'ов) нужны частые add/contains/clear по ключу-long
 * без аллокаций. Open addressing без боксинга и без rehash-аллокаций на clear
 * (мы просто затираем used-флаги лениво через "generation").
 *
 * Память: 16 байт на слот (long key + int stamp), растёт в 2 раза при load factor 0.7.
 * Все операции — O(1) амортизированно, без единого new в steady state.
 */
public final class CompactLongSet {

    private static final long EMPTY = Long.MIN_VALUE;

    private long[] keys;
    private int[] stamps;
    private int mask;
    private int size;
    private int resizeThreshold;
    private int stamp; // увеличивается на каждом clear — O(1) очистка без обнуления массивов

    public CompactLongSet(int expectedCapacity) {
        int capacity = 8;
        while (capacity < expectedCapacity * 2) {
            capacity <<= 1;
        }
        this.keys = new long[capacity];
        this.stamps = new int[capacity];
        this.mask = capacity - 1;
        this.resizeThreshold = (int) (capacity * 0.7);
        java.util.Arrays.fill(keys, EMPTY);
    }

    /** O(1) амортизированно. false, если такого ключа не было. */
    public boolean add(long key) {
        if (key == EMPTY) {
            throw new IllegalArgumentException("EMPTY is a reserved key");
        }
        int mask = this.mask;
        int idx = mix(key) & mask;
        long[] keys = this.keys;
        int[] stamps = this.stamps;
        int stamp = this.stamp; // generation меняется только в clear(), не здесь
        while (true) {
            long k = keys[idx];
            if (k == EMPTY) {
                keys[idx] = key;
                stamps[idx] = stamp;
                if (++size > resizeThreshold) {
                    grow();
                }
                return true;
            }
            if (k == key && stamps[idx] == stamp) {
                return false;
            }
            idx = (idx + 1) & mask;
        }
    }

    public boolean contains(long key) {
        if (key == EMPTY) {
            return false;
        }
        int mask = this.mask;
        int idx = mix(key) & mask;
        long[] keys = this.keys;
        int[] stamps = this.stamps;
        int stamp = this.stamp;
        while (true) {
            long k = keys[idx];
            if (k == EMPTY) {
                return false;
            }
            if (k == key && stamps[idx] == stamp) {
                return true;
            }
            idx = (idx + 1) & mask;
        }
    }

    /** Обнуляет множество без аллокаций и без обнуления больших массивов. */
    public void clear() {
        if (size == 0) {
            return;
        }
        stamp++;
        if (stamp == 0) { // переполнение int — зануляем честно один раз
            java.util.Arrays.fill(stamps, 0);
            stamp = 1;
        }
        size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldStamps = stamps;
        int newCapacity = oldKeys.length << 1;
        keys = new long[newCapacity];
        stamps = new int[newCapacity];
        java.util.Arrays.fill(keys, EMPTY);
        mask = newCapacity - 1;
        resizeThreshold = (int) (newCapacity * 0.7);
        int stamp = this.stamp;
        int mask = this.mask;
        for (int i = 0; i < oldKeys.length; i++) {
            long k = oldKeys[i];
            if (k != EMPTY && oldStamps[i] == stamp) {
                int idx = mix(k) & mask;
                while (keys[idx] != EMPTY) {
                    idx = (idx + 1) & mask;
                }
                keys[idx] = k;
                stamps[idx] = stamp;
            }
        }
    }

    /** SplitMix64 финальный микс — убирает кластеризацию на последовательных ключах (chunk keys). */
    private static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) h;
    }
}
