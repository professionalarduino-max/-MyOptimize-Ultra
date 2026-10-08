package com.myoptimize.ultra;

import com.myoptimize.ultra.adaptive.SystemInfo;
import com.myoptimize.ultra.cache.CacheManager;
import com.myoptimize.ultra.cache.SmartCache;
import com.myoptimize.ultra.config.ModConfig;
import com.myoptimize.ultra.config.OptimizationProfile;
import com.myoptimize.ultra.entity.ThrottlePolicy;
import com.myoptimize.ultra.memory.GcMonitor;
import com.myoptimize.ultra.memory.MemoryAdvisor;
import com.myoptimize.ultra.memory.ObjectPool;
import com.myoptimize.ultra.profiler.AllocationTracker;
import com.myoptimize.ultra.profiler.FrameTimeTracker;
import com.myoptimize.ultra.profiler.KillerFinder;
import com.myoptimize.ultra.scheduler.UltraFrameScheduler;
import com.myoptimize.ultra.util.CompactLong2IntMap;
import com.myoptimize.ultra.util.CompactLongSet;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smoke-тест чисто-Java ядра мода (без зависимостей от Minecraft).
 *
 * Запуск без Gradle:
 *   javac -d out $(find src/main/java -name "*.java" | grep -E "util|memory|profiler|scheduler|cache|config|adaptive/SystemInfo|entity/ThrottlePolicy") src/test/java/com/myoptimize/ultra/SmokeTest.java
 *   java -cp out com.myoptimize.ultra.SmokeTest
 *
 * Покрывает: пулы, compact-структуры, кэши, трекеры, планировщик,
 * профили и эвристики. То, что зависит от Minecraft (миксины), здесь
 * не проверяется — это задача интеграционного теста в игре.
 */
public class SmokeTest {

    private static int failures = 0;

    private static void check(boolean condition, String name) {
        System.out.println((condition ? "PASS  " : "FAIL  ") + name);
        if (!condition) {
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== MyOptimize Ultra — smoke test (pure Java core) ===\n");

        // --- CompactLongSet ---
        CompactLongSet set = new CompactLongSet(16);
        check(set.add(123456789L), "set: add new key");
        check(!set.add(123456789L), "set: duplicate add rejected");
        check(set.contains(123456789L), "set: contains");
        check(!set.contains(42L), "set: missing key");
        for (int i = 0; i < 5000; i++) {
            set.add(i * 7919L);
        }
        check(set.size() == 5001, "set: size after bulk add");
        set.clear();
        check(set.isEmpty() && !set.contains(123456789L), "set: clear via generation stamp");
        CompactLongSet big = new CompactLongSet(4);
        for (int i = 0; i < 10000; i++) {
            big.add(i * 31L + 7);
        }
        boolean allPresent = true;
        for (int i = 0; i < 10000; i++) {
            if (!big.contains(i * 31L + 7)) {
                allPresent = false;
                break;
            }
        }
        check(allPresent && big.size() == 10000, "set: grow + integrity over 10k keys");

        // --- CompactLong2IntMap ---
        CompactLong2IntMap map = new CompactLong2IntMap(16, -1);
        check(map.get(5L) == -1, "map: default value");
        map.put(5L, 77);
        check(map.get(5L) == 77, "map: get after put");
        map.nextFrame();
        check(map.get(5L) == -1, "map: nextFrame invalidates");
        map.put(9L, 1);
        check(map.get(9L) == 1 && map.size() == 1, "map: size after frame bump");

        // --- ObjectPool ---
        ObjectPool<StringBuilder> pool = new ObjectPool<>(4, 16, StringBuilder::new, sb -> sb.setLength(0));
        StringBuilder a = pool.acquire();
        a.append("hello");
        pool.release(a);
        StringBuilder b = pool.acquire();
        check(b == a && b.length() == 0, "pool: object reused and reset");
        check(pool.createdCount() == 4, "pool: no extra allocations after warmup (created=" + pool.createdCount() + ")");
        pool.release(b);

        // --- FrameTimeTracker ---
        FrameTimeTracker tracker = new FrameTimeTracker(600);
        for (int i = 0; i < 300; i++) {
            tracker.recordFrame(10_000_000L); // 10 мс — нормальные кадры
        }
        for (int i = 0; i < 10; i++) {
            tracker.recordFrame(100_000_000L); // 10 кадров по 100 мс — микрофризы
        }
        long[] snap = tracker.snapshot();
        FrameTimeTracker.FrameStats stats = FrameTimeTracker.compute(snap, 16_666_666L);
        check(stats.samples == 310, "frametracker: sample count");
        check(stats.averageMs > 10 && stats.averageMs < 20, "frametracker: average sane (" + stats.averageMs + " ms)");
        check(stats.p1LowMs >= 90, "frametracker: 1% low catches spikes (" + stats.p1LowMs + " ms)");
        check(stats.p01LowMs >= 90, "frametracker: 0.1% low catches spikes (" + stats.p01LowMs + " ms)");
        check(stats.pacingScore < 1.0 && stats.pacingScore > 0.9, "frametracker: pacing score (" + stats.pacingScore + ")");
        long[] reuse = new long[120];
        check(tracker.snapshotInto(reuse) == 120, "frametracker: snapshotInto reuses buffer");

        // --- KillerFinder ---
        KillerFinder killerFinder = new KillerFinder();
        killerFinder.record(KillerFinder.Category.ENTITY_TICK, 1_000_000);
        killerFinder.record(KillerFinder.Category.ENTITY_RENDER, 3_000_000);
        killerFinder.beginFrame();
        KillerFinder.TopKiller top = killerFinder.topKiller();
        check(top.category == KillerFinder.Category.ENTITY_RENDER, "killer: top category");
        check(Math.abs(top.share - 0.75) < 0.001, "killer: share is 75%");
        check(killerFinder.categoryMillis(KillerFinder.Category.ENTITY_TICK) == 1.0, "killer: category millis");

        // --- ThrottlePolicy ---
        check(ThrottlePolicy.intervalFor(10 * 10, 64, 20) == 1, "throttle: near mobs tick every tick");
        check(ThrottlePolicy.intervalFor(80 * 80, 64, 20) == 5, "throttle: 80 blocks -> every 5 ticks");
        check(ThrottlePolicy.intervalFor(300 * 300, 64, 20) == 20, "throttle: very far -> capped at 20");
        boolean anyTick = false;
        for (int id = 0; id < 100; id++) {
            if (ThrottlePolicy.shouldTickThisTick(1000, id, 10)) {
                anyTick = true;
                break;
            }
        }
        check(anyTick, "throttle: ticks spread across entity ids");

        // --- GcMonitor ---
        GcMonitor gc = new GcMonitor();
        gc.start();
        for (int round = 0; round < 10 && gc.totalGcCount() == 0; round++) {
            for (int i = 0; i < 400; i++) {
                byte[] junk = new byte[256 * 1024];
                junk[0] = 1;
            }
            System.gc();
            Thread.sleep(100);
        }
        System.out.println("  (gc monitor observed: count=" + gc.totalGcCount()
            + ", totalMs=" + gc.totalGcTimeMs() + ", young=" + gc.youngGcCount() + ", old=" + gc.oldGcCount() + ")");
        gc.stop();
        check(true, "gc monitor: ran without errors" + (gc.totalGcCount() > 0 ? " and observed collections" : " (no GC triggered — JVM-dependent)"));

        // --- MemoryAdvisor ---
        long heap = MemoryAdvisor.recommendedMaxHeapBytes(8L * 1024 * 1024 * 1024);
        check(heap == 2L * 1024 * 1024 * 1024, "advisor: 8GB machine -> 2GB heap recommendation");
        check(MemoryAdvisor.cacheBudgetBytes(4L << 30, 1L << 30) >= 8L << 20, "advisor: cache budget has 8MB floor");
        check(!MemoryAdvisor.recommendedJvmFlags(16L << 30).isEmpty(), "advisor: jvm flags generated");

        // --- SmartCache + CacheManager ---
        SmartCache<String, Integer> cache = CacheManager.register(new SmartCache<>("smoke", 1024));
        cache.put("a", 1, 100);
        cache.put("b", 2, 100);
        check(cache.get("a") == 1, "cache: hit");
        check(cache.get("missing") == null, "cache: miss");
        check(cache.hitRate() > 0, "cache: hit rate tracked");
        for (int i = 0; i < 100; i++) {
            cache.put("k" + i, i, 100); // LRU-эвикция по весу
        }
        check(cache.currentWeight() <= 1024, "cache: evicts to budget (weight=" + cache.currentWeight() + ")");
        check(cache.evictions() > 0, "cache: evictions counted");
        check(CacheManager.totalWeight() >= 0, "cache manager: total weight");
        check(!CacheManager.trimToBudget(), "cache manager: trim is no-op within budget");

        // --- ModConfig + OptimizationProfile ---
        ModConfig cfg = new ModConfig();
        OptimizationProfile.MAX_FPS.applyTo(cfg);
        check(cfg.particleMaxCount == 2000 && "MAX FPS".equals(cfg.profile), "profile: MAX FPS applied");
        check(OptimizationProfile.byName("ultra performance") == OptimizationProfile.ULTRA_PERFORMANCE,
            "profile: fuzzy byName");
        check(OptimizationProfile.byName("garbage") == OptimizationProfile.BALANCED,
            "profile: byName fallback to Balanced");
        OptimizationProfile.POTATO.applyTo(cfg);
        check(cfg.chunkUploadBatchPerFrame == 8 && cfg.aggressiveEntityCulling, "profile: Potato applied");

        // --- SystemInfo ---
        SystemInfo info = SystemInfo.detect();
        check(info.cpuCores >= 1 && info.maxHeapBytes > 0, "system info: " + info.summary());
        check(info.machineClass() >= 0 && info.machineClass() <= 3, "system info: machine class in range");

        // --- AllocationTracker ---
        AllocationTracker alloc = new AllocationTracker(Thread.currentThread(), null);
        alloc.sample();
        Thread.sleep(50);
        alloc.sample();
        check(alloc.isAvailable(), "allocation tracker: available on this JVM");

        // --- UltraFrameScheduler ---
        UltraFrameScheduler scheduler = new UltraFrameScheduler(2);
        scheduler.setFrameBudgetNanos(1_000_000L); // 1 мс фоновой работы на кадр
        AtomicInteger ran = new AtomicInteger();
        for (int i = 0; i < 50; i++) {
            scheduler.schedule("smoke-task", UltraFrameScheduler.Priority.NORMAL, ran::incrementAndGet);
        }
        long deadline = System.currentTimeMillis() + 5000;
        while (ran.get() < 50 && System.currentTimeMillis() < deadline) {
            scheduler.onFrameStart();
            Thread.sleep(2);
        }
        scheduler.onFrameEnd();
        check(ran.get() == 50, "scheduler: all 50 tasks executed (ran=" + ran.get() + ")");
        check(scheduler.tasksExecuted() >= 50, "scheduler: execution stats");
        check(scheduler.averageTaskMs() >= 0, "scheduler: average task time");
        scheduler.shutdown();
        check(true, "scheduler: shutdown clean");

        System.out.println();
        if (failures == 0) {
            System.out.println("=== ALL SMOKE TESTS PASSED ===");
        } else {
            System.out.println("=== " + failures + " FAILURE(S) ===");
            System.exit(1);
        }
    }
}
