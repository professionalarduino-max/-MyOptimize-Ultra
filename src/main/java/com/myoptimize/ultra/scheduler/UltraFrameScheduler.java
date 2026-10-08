package com.myoptimize.ultra.scheduler;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ultra Frame Scheduler — распределитель фоновой работы по кадрам.
 *
 * Проблема, которую решаем: подсистемы мода (trim кэшей, prewarm, подсчёт
 * статистики профилировщика) генерируют второстепенную работу. Если выполнять её
 * "как попало", она попадает в случайные кадры и даёт скачки frametime
 * (микростаттеры) — главного врага плавности, ради которого мод и существует.
 *
 * Как решаем:
 *  1. Вся фоновая работа ставится в приоритетную очередь и выполняется пулом
 *     воркеров ВНЕ render thread.
 *  2. Воркеры не работают "бесконтрольно": у них есть бюджет наносекунд на кадр
 *     ({@link #frameBudgetNanos}). Как только бюджет исчерпан — воркеры засыпают
 *     до начала следующего кадра. Таким образом наш вклад в frametime жёстко
 *     ограничен и равномерно размазан, а не сконцентрирован в одном кадре.
 *  3. Задачи, которые обязаны выполняться на render thread (инвалидация кэшей
 *     на границе кадров), идут через отдельную очередь и выполняются в
 *     {@link #onFrameStart()} с собственным микробюджетом.
 *  4. Приоритеты: CRITICAL задачи выполняются всегда (они крошечные и должны
 *     выполняться строго вовремя), остальные — в порядке приоритета.
 *
 * Точки вызова: MixinGameRenderer (render thread) → onFrameStart/onFrameEnd.
 * Submit: любой модуль → schedule(...).
 */
public final class UltraFrameScheduler {

    public enum Priority {
        CRITICAL(0), HIGH(1), NORMAL(2), LOW(3), BACKGROUND(4);

        final int order;

        Priority(int order) {
            this.order = order;
        }
    }

    private static final class Entry implements Comparable<Entry> {
        final Priority priority;
        final long seq;
        final String name;
        final Runnable task;

        Entry(Priority priority, long seq, String name, Runnable task) {
            this.priority = priority;
            this.seq = seq;
            this.name = name;
            this.task = task;
        }

        @Override
        public int compareTo(Entry other) {
            int byPriority = Integer.compare(this.priority.order, other.priority.order);
            return byPriority != 0 ? byPriority : Long.compare(this.seq, other.seq);
        }
    }

    private final PriorityBlockingQueue<Entry> backgroundQueue = new PriorityBlockingQueue<>();
    private final PriorityBlockingQueue<Entry> renderQueue = new PriorityBlockingQueue<>();
    private final Thread[] workers;
    private final Lock pauseLock = new ReentrantLock();
    private final Condition frameSignal = pauseLock.newCondition();

    /** Сколько наносекунд фоновых задач разрешено выполнить within одного кадра. */
    private volatile long frameBudgetNanos = 2_000_000L; // 2 мс
    /** Микробюджет render-thread очереди на кадр. */
    private volatile long renderBudgetNanos = 500_000L; // 0.5 мс

    private final AtomicLong frameSpentNanos = new AtomicLong();
    private final AtomicLong seq = new AtomicLong();
    private volatile boolean running = true;

    // Статистика для профилировщика / F3
    private final AtomicLong tasksExecuted = new AtomicLong();
    private final AtomicLong tasksDeferred = new AtomicLong();
    private final AtomicLong totalTaskNanos = new AtomicLong();
    private final AtomicLong budgetExhaustedCount = new AtomicLong();
    private volatile double lastTaskMs;
    private volatile int queueSizeSnapshot;

    public UltraFrameScheduler(int workerCount) {
        int count = Math.max(1, Math.min(workerCount, 4));
        this.workers = new Thread[count];
        for (int i = 0; i < count; i++) {
            Thread t = new Thread(this::workerLoop, "MyOptimize-Worker-" + i);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1); // чуть ниже дефолта, чтобы не душить игру
            t.start();
            workers[i] = t;
        }
    }

    public void schedule(String name, Priority priority, Runnable task) {
        if (task == null || !running) {
            return;
        }
        backgroundQueue.offer(new Entry(priority, seq.getAndIncrement(), name, task));
        wakeWorkers();
    }

    /** Задача, обязанная выполняться на render thread (например, инвалидация кэша на границе кадра). */
    public void scheduleOnRenderThread(String name, Priority priority, Runnable task) {
        if (task == null || !running) {
            return;
        }
        renderQueue.offer(new Entry(priority, seq.getAndIncrement(), name, task));
    }

    /** Вызывается на render thread в начале кадра (MixinGameRenderer). */
    public void onFrameStart() {
        frameSpentNanos.set(0);
        queueSizeSnapshot = backgroundQueue.size() + renderQueue.size();
        wakeWorkers();
        pumpRenderQueue();
    }

    /** Вызывается на render thread в конце кадра. */
    public void onFrameEnd() {
        // Статистика; следующее onFrameStart сбросит счётчики снова.
    }

    public void setFrameBudgetNanos(long nanos) {
        this.frameBudgetNanos = Math.max(0, nanos);
    }

    public void setRenderBudgetNanos(long nanos) {
        this.renderBudgetNanos = Math.max(0, nanos);
    }

    public long frameBudgetNanos() {
        return frameBudgetNanos;
    }

    public int pendingTasks() {
        return backgroundQueue.size() + renderQueue.size();
    }

    public long tasksExecuted() {
        return tasksExecuted.get();
    }

    public long tasksDeferred() {
        return tasksDeferred.get();
    }

    public long budgetExhaustedCount() {
        return budgetExhaustedCount.get();
    }

    public double lastTaskMs() {
        return lastTaskMs;
    }

    public double averageTaskMs() {
        long executed = tasksExecuted.get();
        return executed == 0 ? 0 : (totalTaskNanos.get() / (double) executed) / 1_000_000.0;
    }

    public void shutdown() {
        running = false;
        wakeWorkers();
        for (Thread worker : workers) {
            try {
                worker.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void pumpRenderQueue() {
        long deadline = System.nanoTime() + renderBudgetNanos;
        Entry entry;
        while ((entry = renderQueue.poll()) != null) {
            if (entry.priority != Priority.CRITICAL && System.nanoTime() > deadline) {
                // Бюджет render-очереди исчерпан — остальное на следующем кадре.
                renderQueue.offer(entry);
                tasksDeferred.incrementAndGet();
                break;
            }
            runTask(entry);
        }
    }

    private void workerLoop() {
        while (running) {
            Entry entry;
            try {
                entry = backgroundQueue.poll();
            } catch (Exception e) {
                continue;
            }
            if (entry == null) {
                awaitFrameSignal();
                continue;
            }
            if (entry.priority != Priority.CRITICAL && frameSpentNanos.get() >= frameBudgetNanos) {
                // Бюджет на этот кадр исчерпан — спим до следующего кадра.
                backgroundQueue.offer(entry);
                tasksDeferred.incrementAndGet();
                budgetExhaustedCount.incrementAndGet();
                awaitFrameSignal();
                continue;
            }
            long start = System.nanoTime();
            try {
                entry.task.run();
            } catch (Throwable t) {
                // Фоновая задача не должна ронять воркера.
                t.printStackTrace();
            }
            long elapsed = System.nanoTime() - start;
            frameSpentNanos.addAndGet(elapsed);
            totalTaskNanos.addAndGet(elapsed);
            lastTaskMs = elapsed / 1_000_000.0;
            tasksExecuted.incrementAndGet();
        }
    }

    private void runTask(Entry entry) {
        long start = System.nanoTime();
        try {
            entry.task.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        long elapsed = System.nanoTime() - start;
        totalTaskNanos.addAndGet(elapsed);
        lastTaskMs = elapsed / 1_000_000.0;
        tasksExecuted.incrementAndGet();
    }

    private void awaitFrameSignal() {
        pauseLock.lock();
        try {
            // Спим небольшое время; onFrameStart нас разбудит.
            frameSignal.awaitNanos(500_000L); // 0.5 мс — страховка от пропущенного сигнала
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pauseLock.unlock();
        }
    }

    private void wakeWorkers() {
        pauseLock.lock();
        try {
            frameSignal.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }
}
