# Техническое обоснование каждой оптимизации

Документ отвечает на требование: **«каждая оптимизация должна иметь техническое обоснование»** и **«не делать бессмысленных оптимизаций»**. Для каждой фичи: механизм, почему это реально уменьшает нагрузку, цена/риски, где код.

## 1. Pacing GPU-аплоадов чанков (ChunkUploadPacer + MixinChunkBuilder)

**Проблема.** `ChunkBuilder.upload()` вызывается каждый кадр и загружает в GPU **все** готовые буферы чанков. После телепорта/входа в мир счёт — сотни аплоадов за один кадр. Каждый аплоад — синхронная работа с GPU (glBufferData), а пачка таких работ в одном кадре даёт скачок frametime и stall render thread.

**Решение.** Считаем аплоады за кадр (`scheduleUpload` → счётчик). Если достигнут лимит (по умолчанию 32, адаптируется) — вызов `upload()` отменяется, очередь аплоадов сохраняется, лишние буферы грузятся в следующих кадрах.

**Почему это легально.** Порядок и состав аплоадов не меняется, меняется только то, **в каком кадре** они происходят. Чанки появляются чуть растянуто во времени (незаметно при лимите 32), но без фризов.

**Код:** `chunk/ChunkUploadPacer.java`, `mixin/MixinChunkBuilder.java`.

## 2. Пропуск sky/clouds/weather под землёй (MixinWorldRenderer)

**Проблема.** В пещере `WorldRenderer` всё равно отрисовывает небо, облака и погоду. Туман полностью перекрывает их — GPU-работа уходит впустую (особенно облака — отдельный буфер/проход).

**Решение.** Если `world.getTopY(MOTION_BLOCKING, camera)` выше камеры + 2 — пропускаем `renderClouds`, `renderWeather`, а `renderSky` пропускаем **после выполнения fogCallback** (fog обязан быть выставлен, иначе тьма/туман сломаются).

**Проверка на честность.** В пещере игрок не видит разницы (туман непрозрачный). На поверхности — ни одного пропущенного кадра. Риски: моды, рисующие в небо сквозь блоки (редкость) — mitigated: фича в профиле и конфиге, выключается одной кнопкой.

**Код:** `render/CullingUtil.java` (мемоизированная на кадр проверка), `mixin/MixinWorldRenderer.java`.

## 3. Кэш lightmap-координат на кадр (LightmapCache + MixinWorldRenderer)

**Проблема.** `WorldRenderer.getLightmapCoordinates(world, pos)` — горячий путь: вызывается для каждой сущности и block entity каждый кадр. Внутри: `LightingProvider.get(...)` → чанк → секция → массив → `LightmapTextureManager.pack`. Несколько indirections на один int, тысячи раз за кадр.

**Инвариант.** На клиенте свет не меняется внутри одного кадра (light-пакеты применяются между кадрами). Значит, результат валиден до конца кадра.

**Решение.** Кэш `BlockPos.asLong() → packed int` в `CompactLong2IntMap` с generation-stamp: инвалидация между кадрами — O(1) инкремент, без обнуления памяти и без аллокаций.

**Код:** `world/LightmapCache.java`, `util/CompactLong2IntMap.java`, `mixin/MixinWorldRenderer.java`.

## 4. Дедупликация bind'ов текстур (TextureBindOptimizer + MixinRenderSystem)

**Проблема.** GUI и HUD вызывают `RenderSystem.setShaderTexture` сотни раз за кадр, часто подряд с одной и той же текстурой. Каждый вызов (Identifier-вариант) — это lookup в `TextureManager` + `getGlId()` + проход по состоянию.

**Честность.** GlStateManager уже дедуплицирует сами GL-вызовы. Наша экономия — пропуск lookup'а и накладных расходов RenderSystem; выгода скромная, но цена нулевая.

**Безопасность.** Кэш синхронизируется со всеми путями bind'а (`setShaderTexture(int,int)`, `setShaderTexture(int,Identifier)`, `bindTexture(int)`, `activeTexture(int)`). При шейдерах (Iris/Oculus) и VulkanMod — **автоматически выключен** (Compatibility Guard), потому что шейдерные пайплайны делают кэш потенциально небезопасным.

**Код:** `render/TextureBindOptimizer.java`, `mixin/MixinRenderSystem.java`, `compat/CompatibilityGuard.java`.

## 5. Culling сущностей по дистанции (EntityCuller + MixinEntityRenderDispatcher)

**Проблема.** Ванилла отсекает сущности только по frustum. Item/XP сущности и далёкие block entities попадают во frustum и рисуются, хотя заметны лишь как пиксели.

**Решение.** Дополнительное отсечение по квадрату дистанции до камеры: item/XP — за пределом `itemEntityCullDistance` (по умолчанию 64), block entities — за `blockEntityCullDistance` (96), обычные мобы — только в агрессивном режиме (по умолчанию **выключен**, т.к. на границе заметен поп-in).

**Влияние.** Только на отрисовку. Сущности продолжают тикать, подбираться, despawn'иться. Бонус: `EntityRenderDispatcher.render` вызывается и в shadow pass — culling уменьшает и тени.

**Код:** `entity/EntityCuller.java`, `mixin/MixinEntityRenderDispatcher.java`, `mixin/MixinBlockEntityRenderDispatcher.java`.

## 6. Throttling тиков далёких мобов (EntityTickThrottler + MixinServerWorld)

**Проблема.** В синглплеере integrated server тикает всех мобов каждый тик: AI, pathfinding, физика. Моб в 100+ блоках от игрока не делает ничего полезного, но съедает CPU.

**Решение.** Моб дальше `entityThrottleDistance` (64) от ближайшего игрока тикает с интервалом 5/10/20 тиков (растёт с дистанцией). Условие тика — `(worldTime + entityId) % interval == 0`: тики размазаны, без «волн» нагрузки. `entity.age` не используем (он не растёт при пропусках — был бы баг).

**Защита механики.** Не трогаем: игроков, сущностей с именем, на поводке, в транспорте, с пассажирами, спектаторов. Работает **только на integrated server** (в синглплеере). На выделенный сервер мод не лезет (клиентский мод).

**Известный побочный эффект (задокументирован).** У далёких мобов чуть медленнее despawn-проверки. На геймплей не влияет.

**Код:** `entity/EntityTickThrottler.java`, `entity/ThrottlePolicy.java`, `mixin/MixinServerWorld.java`.

## 7. Culling спавна частиц (ParticleCulling + MixinParticleManager)

**Проблема.** Взрывы/фермы спавнят тысячи частиц; каждая — аллокация + тик + рендер. Vanilla cap (16384) высок, спавн-сторона не отсекает по дистанции.

**Решение.** В `createParticle` (до аллокации!) отбрасываем частицу, если: достигнут cap (по умолчанию 10000, адаптируется) или она дальше `particleSpawnDistance` (64). Существующие частицы дорисовываются — «хвост» взрыва не обрезается.

**Безопасность.** `createParticle` в ванилле уже умеет возвращать `null` (нет фабрики) — наш `null` штатно обрабатывается.

**Код:** `render/ParticleCulling.java`, `mixin/MixinParticleManager.java`.

## 8. Батчинг particle-пакетов (NetworkOptimizer + MixinClientPlayNetworkHandler)

**Проблема.** Один взрыв на сервере = десятки `ParticleS2CPacket`, каждый до тысяч частиц, все обрабатываются в одном тике → spike на main thread.

**Решение.** Пакет ставится в FIFO-очередь; в конце тика обрабатывается не более N (по умолчанию 4). Очередь ограничена (512), переполнение отбрасывает новое (визуально безопасно). Порядок пакетов сохраняется, протокол не меняется.

**Код:** `network/NetworkOptimizer.java`, `mixin/MixinClientPlayNetworkHandler.java`.

## 9. Кэш ширины текста + пропуск пустых строк (GuiOptimizer + MixinTextRenderer)

**Проблема.** `TextRenderer.getWidth` и `draw` вызываются постоянно в GUI/HUD, часто с одними и теми же строками (лейблы, F3) и пустыми строками.

**Решение.** LRU-кэш `String → width` (SmartCache, бюджет 4 МБ, hit-rate в статистике). Пустые строки не рисуются. Кэш сбрасывается при reload ресурсов (смена языка/шрифта/ресурс-пака) — зарегистрировано в `MyOptimizeUltra`.

**Код:** `gui/GuiOptimizer.java`, `mixin/MixinTextRenderer.java`.

## 10. Трекинг изменений блоков по секциям (ChunkUpdateTracker + MixinWorld)

**Проблема.** Нужно понимать, сколько работы по ребилдам создаёт игра/сервер за кадр — без этого AdaptiveEngine и профилировщик слепы.

**Решение.** `World.setBlockState` (только `isClient`) → ключ секции (точная упаковка 26+12+26 бит, без коллизий) в `CompactLongSet`; граничные блоки отмечают соседние секции. Zero-allocation на изменение; сброс на границе кадра — O(1).

**Код:** `chunk/ChunkUpdateTracker.java`, `util/CompactLongSet.java`, `mixin/MixinWorld.java`.

## 11. Ultra Frame Scheduler

**Проблема.** Любая фоновая работа (trim кэшей, расчёт статистики, prewarm), выполненная «в случайном месте», попадает в случайный кадр и даёт микростаттер — главного врага плавности.

**Решение.** Приоритетная очередь + пул воркеров вне render thread + **бюджет наносекунд на кадр**: воркеры засыпают, когда бюджет исчерпан, и ждут следующего кадра. Render-thread задачи (инвалидация кэшей) — отдельная очередь с микробюджетом в `onFrameStart`.

**Потребители (реальные, не «для галочки»).** Расчёт статистики frametime (сортировка 600 сэмплов — вне render thread), trim кэшей раз в 600 кадров, prewarm при старте.

**Код:** `scheduler/UltraFrameScheduler.java`, `profiler/FrameTimeTracker.java`.

## 12. Zero-allocation per-frame структуры (CompactLongSet / CompactLong2IntMap)

**Проблема.** В per-tick/per-frame hot path (трекинг секций, lightmap-кэш, дедупликация) аллокации → GC-давление → микрофризы.

**Решение.** Open addressing на примитивах, generation-stamp вместо очистки (O(1)), SplitMix64 для равномерного хеширования chunk key'ей. Без боксинга, без `new` в steady state.

**Проверено smoke-тестом** (дедупликация, рост до 10k ключей, инвалидация по generation).

**Код:** `util/CompactLongSet.java`, `util/CompactLong2IntMap.java`, `src/test/java/.../SmokeTest.java`.

## 13. Мониторинг GC и аллокаций (GcMonitor, AllocationTracker)

**Проблема.** GC-паузы — главный источник микростаттеров; без измерений не понять, давит ли GC.

**Решение.** Подписка на нотификации `GarbageCollectorMXBean` (стандартный механизм, без флагов JVM): счётчики, суммарное/последнее время, young/old. Трекинг аллокаций по render/server thread через `com.sun.management.ThreadMXBean` (HotSpot). Данные идут в F3, профилировщик и AdaptiveEngine (например, при GC > 200 мс — кэши режутся вдвое).

**Код:** `memory/GcMonitor.java`, `profiler/AllocationTracker.java`.

## 14. MyOptimize Adaptive Engine

**Проблема.** «Производительность» — не одно число: разные машины и сцены требуют разных настроек, и ручная подстройка — боль.

**Решение.** Раз в секунду: pacing score < 0.90 → лимит аплоадов чанков уменьшается; heap > 85% или GC давит → бюджет кэшей режется; сущностей > 600 → cap частиц уменьшается; целевой FPS задаёт budget планировщика. **Только наши ручки** — ничего игрового.

**Код:** `adaptive/AdaptiveEngine.java`, `adaptive/AutoOptimizer.java` (AUTO OPTIMIZE + автоопределение профиля по SystemInfo/GpuInfo).

## 15. Compatibility Guard (mixin plugin + runtime)

**Проблема.** Оптимизационные моды конфликтуют молча: картинка ломается, читерских преимуществ не дают, но доверие пропадает.

**Решение.** Два уровня: (1) mixin plugin не применяет render-миксины при VulkanMod; (2) runtime-скан watchlist-модов (Sodium, Iris, Lithium, …) с точечным отключением конфликтующих функций (shader safe mode) и понятным отчётом `compat-report.txt`.

**Код:** `mixin/MyOptimizeMixinPlugin.java`, `compat/CompatibilityGuard.java`, `compat/CompatReport.java`.

---

## Чего здесь нет — и почему (осознанные отказы)

| Идея | Почему отказались |
|---|---|
| «Свой» chunk mesh builder / рендерер | В 1.21.1 ванильный пайплайн уже асинхронный; замена = месяцы работы и регрессии (это niche Sodium). Мы работаем поверх: pacing, метрики, статистика. |
| Свой light engine | То же: ванильный свет уже async; замена = Starlight-niche. Мы кэшируем чтение и меряем. |
| Изменение физики воды/лавы/redstone | Ломает баланс и механики. Запрещено принципом мода. |
| Сжатие/изменение сетевых пакетов | Ломает протокол и совместимость с серверами. |
| Отключение теней/графики «ради FPS» | Ломает картинку; вместо этого — culling, который не виден. |
| «Магические» эвристики без замеров | Каждая фича либо мерима (профилировщик), либо выключена по умолчанию. |
