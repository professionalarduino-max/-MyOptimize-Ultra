package com.myoptimize.ultra.util;

/**
 * Компактная карта long → int на открытой адресации без боксинга.
 *
 * Применения в моде:
 *  - кэш упакованных lightmap-координат (ключ = BlockPos.asLong(), значение = packed light)
 *    — siehe world/LightmapCache;
 *  - счётчики "изменений на секцию чанка" для профилировщика.
 *
 * Инвариант "значение по умолчанию" — {@link #defaultValue}, возвращается для отсутствующих
 * ключей, поэтому чистить карту между кадрами не нужно: достаточно bump'нуть generation,
 * как в {@link CompactLongSet}.
 */
public final class CompactLong2IntMap {

    private static final long EMPTY = Long.MIN_VALUE;

    private long[] keys;
    private int[] values;
    private int[] stamps;
    private int mask;
    private int size;
    private int resizeThreshold;
    private int stamp;
    private final int defaultValue;

    public CompactLong2IntMap(int expectedCapacity, int defaultValue) {
        int capacity = 8;
        while (capacity < expectedCapacity * 2) {
            capacity <<= 1;
        }
        this.keys = new long[capacity];
        this.values = new int[capacity];
        this.stamps = new int[capacity];
        this.mask = capacity - 1;
        this.resizeThreshold = (int) (capacity * 0.7);
        this.defaultValue = defaultValue;
        java.util.Arrays.fill(keys, EMPTY);
    }

    /** get-or-default: если ключа нет — возвращает defaultValue и НЕ вставляет ключ. */
    public int get(long key) {
        if (key == EMPTY) {
            return defaultValue;
        }
        int idx = mix(key) & mask;
        int stamp = this.stamp;
        long[] keys = this.keys;
        int[] stamps = this.stamps;
        while (true) {
            long k = keys[idx];
            if (k == EMPTY) {
                return defaultValue;
            }
            if (k == key && stamps[idx] == stamp) {
                return values[idx];
            }
            idx = (idx + 1) & mask;
        }
    }

    public void put(long key, int value) {
        if (key == EMPTY) {
            throw new IllegalArgumentException("EMPTY is a reserved key");
        }
        int idx = mix(key) & mask;
        int stamp = this.stamp; // generation меняется только в nextFrame(), не здесь
        long[] keys = this.keys;
        int[] stamps = this.stamps;
        while (true) {
            long k = keys[idx];
            if (k == EMPTY) {
                keys[idx] = key;
                values[idx] = value;
                stamps[idx] = stamp;
                if (++size > resizeThreshold) {
                    grow();
                }
                return;
            }
            if (k == key && stamps[idx] == stamp) {
                values[idx] = value;
                return;
            }
            idx = (idx + 1) & mask;
        }
    }

    /** Текущая генерация (для внешних кэшей, привязанных к кадру). */
    public int frame() {
        return stamp;
    }

    /** Следующий кадр: O(1), без обнуления массивов. */
    public void nextFrame() {
        if (size == 0) {
            return;
        }
        stamp++;
        if (stamp == 0) {
            java.util.Arrays.fill(stamps, 0);
            stamp = 1;
        }
        size = 0;
    }

    public int size() {
        return size;
    }

    public int defaultValue() {
        return defaultValue;
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldValues = values;
        int[] oldStamps = stamps;
        int newCapacity = oldKeys.length << 1;
        keys = new long[newCapacity];
        values = new int[newCapacity];
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
                values[idx] = oldValues[i];
                stamps[idx] = stamp;
            }
        }
    }

    private static int mix(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) h;
    }
}
