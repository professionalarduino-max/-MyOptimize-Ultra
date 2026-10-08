package com.myoptimize.ultra.config;

/**
 * Конфигурация MyOptimize Ultra.
 *
 * Чистый POJO (без зависимостей от Minecraft) — сериализуется через Gson,
 * профилями модифицируется через {@link OptimizationProfile}, читается всеми
 * подсистемами через {@link ConfigManager#config()}.
 *
 * Принцип: КАЖДАЯ опция при выключении возвращает поведение к ванильному.
 * Никаких "фейковых" флагов — за каждым стоит реальная ветка в коде.
 */
public class ModConfig {

    public int configVersion = 1;

    // ===== Профиль / авто =====
    /** Potato | Low | Balanced | Performance | Ultra Performance | Extreme | MAX FPS | Custom */
    public String profile = "Balanced";
    public boolean adaptiveEngine = true;

    // ===== Rendering =====
    /** Dedupe bind'ов текстур (пропуск повторных setShaderTexture/bindTexture). */
    public boolean textureBindOptimization = true;
    /** Кэш ширины строк TextRenderer (горячий путь GUI). */
    public boolean fontWidthCache = true;
    /** Пропуск отрисовки пустых строк. */
    public boolean emptyTextSkip = true;
    /** Пропуск sky/clouds/weather, когда камера под землёй (тuman всё равно скрывает). */
    public boolean undergroundSkySkip = true;
    /** Сколько чанк-буферов разрешено аплоадить на GPU за один кадр (0 = без лимита). */
    public int chunkUploadBatchPerFrame = 32;
    /** Кэширование lightmap-координат на время кадра. */
    public boolean lightmapCache = true;

    // ===== Entities =====
    public boolean entityCulling = true;
    /** Дистанция, за которой не рендерятся item/XP сущности. */
    public int itemEntityCullDistance = 64;
    /** Агрессивный cull обычных сущностей (визуально заметен на границе!). */
    public boolean aggressiveEntityCulling = false;
    public int entityCullDistance = 96;
    public boolean blockEntityCulling = true;
    public int blockEntityCullDistance = 96;
    /** Throttling tick'ов мобов вдали (только integrated server / синглплеер). */
    public boolean entityTickThrottling = true;
    public int entityThrottleDistance = 64;
    /** Максимальный интервал между tick'ами далёкого моба (в тиках). */
    public int entityThrottleMaxInterval = 20;

    // ===== Particles =====
    public boolean particleCulling = true;
    /** Глобальный cap на количество частиц (ванильный лимит 16384). */
    public int particleMaxCount = 10000;
    /** Дистанция, за которой клиентские частицы не спавнятся вообще. */
    public int particleSpawnDistance = 64;

    // ===== Chunks =====
    /** Трекинг изменений блоков по секциям (stat + адаптивная эвристика, zero-alloc). */
    public boolean chunkUpdateTracking = true;

    // ===== Memory =====
    /** Бюджет всех кэшей мода, МБ. Адаптируется под RAM. */
    public int cacheBudgetMb = 96;
    public boolean gcMonitoring = true;

    // ===== Network =====
    /** Батчинг particle-пакетов: не более N пакетов за тик клиента. */
    public boolean particlePacketBatching = true;
    public int particlePacketsPerTick = 4;
    /** Лимит очереди; при переполнении новые частицы отбрасываются (визуально безопасно). */
    public int particlePacketQueueLimit = 512;

    // ===== Diagnostics / F3 =====
    /** ultra = наш стилизованный оверлей поверх F3; vanilla = как в ванилле. */
    public String f3Style = "ultra";
    /** Режим разработчика: расширенная статистика в F3. */
    public boolean devMode = false;
    /** Показывать блок "Performance Killer" в F3. */
    public boolean showKillerFinder = true;

    // ===== Compatibility =====
    /** Автоустанавливается CompatibilityGuard при наличии shader-модов. */
    public boolean shaderSafeMode = false;

    public boolean isUltraF3() {
        return "ultra".equalsIgnoreCase(f3Style);
    }
}
