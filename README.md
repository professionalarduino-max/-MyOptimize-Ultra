# ⚡ MyOptimize Ultra

**Ультра-оптимизационный мод для Minecraft (Fabric, 1.21.1). Цель — максимальная производительность без разрушения Minecraft: высокий FPS, минимальный input lag, стабильный frametime, меньше нагрузки на CPU/GPU/RAM, быстрая загрузка мира.**

> Главный принцип: **«Максимальная производительность без разрушения Minecraft»**. Ни одна оптимизация не меняет игровой баланс, не ломает ванильные механики и не трогает сетевой протокол. Любая функция при выключении возвращает поведение к ванильному.

Целевые платформы: от слабых ПК (профиль **Potato**) до мощных систем (профиль **MAX FPS**).

---

## ✨ Что умеет

### Рендер (Rendering / GPU)
- **Пропуск sky/clouds/weather под землёй** — когда камера в пещере, туман всё равно скрывает небо, а отрисовка уходит впустую. Реальная экономия GPU-работы в пещерах.
- **Pacing GPU-аплоадов чанков** — ванилла грузит *все* готовые буферы чанков в GPU в одном кадре; после телепорта это сотни аплоадов → фриз. Мы ограничиваем аплоады за кадр (по умолчанию 32), лишние ждут следующего кадра — чанки появляются чуть растянуто, но без скачков frametime.
- **Кэш координат освещения (lightmap)** на время кадра — `WorldRenderer.getLightmapCoordinates` вызывается для каждой сущности/block entity каждый кадр и лезет в чанк → секцию → массив; кэш отдаёт готовый int.
- **Дедупликация bind'ов текстур** — повторная привязка той же текстуры на тот же юнит пропускается (все пути bind'а синхронизируют кэш). Выключается автоматически при шейдерах.
- **Кэш ширины текста + пропуск пустых строк** — горячие пути GUI/HUD.
- **Culling сущностей и block entities по дистанции** — item/XP сущности исчезают за пределом дистанции (безопасно), block entities — за configurable-дистанцией, агрессивный culling мобов — опционально.

### Чанки (Chunk Engine)
- **Трекинг изменений блоков по секциям** — zero-allocation статистика (CompactLongSet): сколько секций трогают за кадр, с учётом граничных блоков. Питает профилировщик и AdaptiveEngine.
- **Метрики chunk-пайплайна** — аплоады за кадр, загруженные чанки (`getCompletedChunkCount`), время аплоадов.
- Асинхронная сборка мешей в 1.21.1 — уже ванильная; мы не ломаем её, а добавляем pacing и метрики поверх.

### Сущности (Entities)
- **Throttling тиков далёких мобов** (только integrated server / синглплеер) — моб дальше 64 блоков от ближайшего игрока тикает реже (5/10/20 тиков по дистанции), тики размазаны по `(worldTime + entityId) % interval`. Не трогаем: игроков, питомцев с именем, на поводке, в транспорте, с пассажирами.
- **Оптимизация item/XP сущностей** — culling по дистанции + throttling far-частиц рядом с ними.

### Частицы (Particles)
- **Cap + отсечение по дистанции при спавне** — частица не аллоцируется вообще, если лимит исчерпан или она далеко. Существующие частицы дорисовываются (хвост взрыва не обрезается).

### Память и GC
- **Object pool / переиспользование объектов**, compact-структуры (open addressing без боксинга), per-frame инвалидация кэшей через generation-stamp (O(1), без обнуления памяти).
- **Мониторинг GC** (стандартные MXBean-нотификации) — счётчики/время сборок, young/old, передаётся в профилировщик и AdaptiveEngine.
- **Трекер аллокаций** по потокам (HotSpot ThreadMXBean) — MB/s render/server thread.
- **Адаптивный бюджет кэшей** — CacheManager подрезает LRU-хвосты, когда heap/GC давят.
- **Советник по JVM-флагам** — логирует `-Xmx/-Xms/-XX:+UseG1GC -XX:MaxGCPauseMillis=50` под вашу машину.

### Сеть (клиент)
- **Батчинг particle-пакетов** — всплески частиц (взрывы, фермы) размазываются по тикам (N пакетов/тик), очередь ограничена, лишнее отбрасывается. Протокол не меняется, порядок пакетов сохраняется.

### Свет (Lighting)
- Ванильный light engine в 1.21.1 уже асинхронный — мы не лезем в него (рискованно и бессмысленно). Наш вклад: замер времени обработки light-пакетов (`onLightUpdate`) и кэширование финальных значений (lightmap cache).

### Старт игры (Startup)
- Фазовый замер инициализации, параллельный прогрев структур через наш планировщик, раннее логирование рекомендаций по JVM.

### ⚡ Ultra Frame Scheduler
Собственный планировщик: вся фоновая работа мода (trim кэшей, расчёт статистики, prewarm) выполняется воркерами **вне render thread** и жёстко ограничена **бюджетом наносекунд на кадр** (адаптируется под целевой FPS). Результат: наши подсистемы не создают скачков frametime — работа либо размазана, либо отложена.

### 🎯 MyOptimize Adaptive Engine
Раз в секунду снимает показания (FPS, pacing score, p1-low, entity count, heap, GC time) и подстраивает **только наши ручки**: бюджет кэшей, лимит аплоадов чанков за кадр, cap частиц. Ничего игрового не трогает.

### Профили: Potato · Low · Balanced · Performance · Ultra Performance · Extreme · MAX FPS · Custom
Кнопка **AUTO OPTIMIZE** определяет CPU/RAM/GPU/VRAM/разрешение/наличие шейдеров и применяет подходящий профиль.

### 🔍 Диагностика
- **Стилизованный F3** (включен по умолчанию): панели за текстом, дашборд с FPS, frametime (средний/p99/1% low/0.1% low), **график frametime за 120 кадров** с целевой линией, RAM-шкала, счётчики, адаптивные лимиты.
- **«Find Performance Killer»** — показывает, какая подсистема (entity tick/render, частицы, аплоады чанков, свет, сеть…) сейчас съедает больше всего времени.
- **Dev mode** — CPU/GPU %, GC, аллокации MB/s, планировщик, кэши, топ-3 категорий, счётчики пакетов.
- **compat-report.txt** — отчёт о совместимости в `config/myoptimize-ultra/`.

---

## 🚀 Быстрый старт

1. Установите [Fabric Loader](https://fabricmc.net/use/) для Minecraft **1.21.1**.
2. Установите [Fabric API](https://modrinth.com/mod/fabric-api).
3. Скачайте `myoptimize-ultra-<версия>.jar` из Releases и положите в `mods/`.
4. Запустите игру. Откройте настройки мода клавишей **K** (по умолчанию) → нажмите **AUTO OPTIMIZE**.
5. Нажмите **F3** — смотрите на новый дашборд. В пещерах обратите внимание, что небо/облака/погода больше не рендерятся (фPS растёт, визуал тот же — туман всё скрывает).

Конфиг: `config/myoptimize-ultra.json`. Любое ручное изменение переводит профиль в **Custom**.

---

## 🔨 Сборка из исходников

Требуется **JDK 21+** (для модулей под Java 17/25 Gradle подкачает toolchain автоматически).

```bash
git clone <repo> myoptimize-ultra
cd myoptimize-ultra
gradle wrapper         # один раз: создаст ./gradlew (в репозитории wrapper не хранится)
./gradlew build        # все 24 версии сразу
# или одну: ./gradlew :v1.21.1:build
```

Готовый jar: `versions/v<версия>/build/libs/myoptimize-ultra-1.0.0+<версия>.jar`

В репозитории уже лежит собранный jar для **1.21.1**: `build/libs/myoptimize-ultra-1.0.0.jar` (113 КБ, intermediary, готов к игре — положите в `mods/`). Он собран ручным пайплайном `scripts/build-manual.sh` (см. ниже), который делает то же, что Loom, но шаг за шагом — полезно на слабых машинах, где демон Gradle падает от нехватки памяти.

Зависимости подхватываются автоматически (Fabric Loom): Minecraft 1.21.1, yarn `1.21.1+build.3`, Fabric Loader `0.16.9`, Fabric API `0.110.0+1.21.1`.

### Сборка без Gradle (ручной пайплайн)

```bash
JAVA_HOME=/path/to/jdk21 bash scripts/build-manual.sh
# выход: build/libs/myoptimize-ultra-1.0.0.jar
```

### Сборка на GitHub (Actions + Codespaces)

- **Actions**: `.github/workflows/build.yml` — матрица из 24 модулей (1.20 → 26.3), каждый собирает jar и заливает артефакт; по тегу — автоматический релиз. Просто запушьте репозиторий.
- **Codespaces**: откройте репозиторий → Code → Create codespace → в терминале `./gradlew :v1.21.1:build` (или любой модуль). `.devcontainer/devcontainer.json` уже настроен (Java 21 + Gradle).

### Мультиверсионность (1.20 → 26.3)

Проект собран под **все 24 версии Minecraft от 1.20 до 26.3** (включая новую годовую нумерацию Mojang). Устройство: общее ядро + yarn-ветка (1.20–1.21.11, с era-вариантами миксинов) + mojmap-ветка (26.x, Java 25, без обфускации). Подробности — в [`docs/MULTIVERSION_PLAN.md`](docs/MULTIVERSION_PLAN.md).

### Проверка чисто-Java ядра без Gradle
Ядро (пулы, кэши, планировщик, профилировщик, профили) не зависит от Minecraft и покрыто smoke-тестом:

```bash
javac -encoding UTF-8 -d out \
  src/main/java/com/myoptimize/ultra/util/*.java \
  src/main/java/com/myoptimize/ultra/memory/*.java \
  src/main/java/com/myoptimize/ultra/profiler/FrameTimeTracker.java \
  src/main/java/com/myoptimize/ultra/profiler/KillerFinder.java \
  src/main/java/com/myoptimize/ultra/profiler/AllocationTracker.java \
  src/main/java/com/myoptimize/ultra/scheduler/*.java \
  src/main/java/com/myoptimize/ultra/cache/*.java \
  src/main/java/com/myoptimize/ultra/config/ModConfig.java \
  src/main/java/com/myoptimize/ultra/config/OptimizationProfile.java \
  src/main/java/com/myoptimize/ultra/adaptive/SystemInfo.java \
  src/main/java/com/myoptimize/ultra/entity/ThrottlePolicy.java \
  src/test/java/com/myoptimize/ultra/SmokeTest.java
java -cp out com.myoptimize.ultra.SmokeTest
```

## 🖥 Системные требования и JVM

- Minecraft 1.21.1, Java 21, Fabric.
- Мод потребляет минимум ресурсов сам по себе (кэши ограничены бюджетом, по умолчанию 96 МБ, адаптируется).
- Рекомендуемые флаги (логируются при старте под вашу машину):
  `-Xms4G -Xmx4G -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1MixedGCCountTarget=8`

---

## 🧩 Архитектура

Модульная, каждая подсистема — независимый пакет и может быть выключена своим флагом:

```
com.myoptimize.ultra
├── MyOptimizeUltra          # точка входа (client), регистрация keybind/ивентов, границы кадра
├── MyOptimize               # константы, логгер
├── config/                  # ModConfig (POJO), ConfigManager (Gson), OptimizationProfile (8 профилей)
├── gui/                     # ConfigScreen (12 категорий, AUTO OPTIMIZE), F3OverlayRenderer (красивый F3), GuiOptimizer
├── adaptive/                # AdaptiveEngine (автонастройка), AutoOptimizer (AUTO OPTIMIZE), SystemInfo, GpuInfo
├── scheduler/               # UltraFrameScheduler (бюджет наносекунд на кадр, воркеры вне render thread)
├── profiler/                # PerformanceProfiler, FrameTimeTracker (p99/1% low/0.1% low/pacing), KillerFinder, AllocationTracker
├── memory/                  # ObjectPool, GcMonitor (MXBean-нотификации), MemoryAdvisor (JVM-советы)
├── cache/                   # SmartCache (адаптивный LRU), CacheManager (бюджеты, trim)
├── util/                    # CompactLongSet / CompactLong2IntMap (open addressing, без боксинга, O(1) clear)
├── render/                  # CullingUtil, TextureBindOptimizer, ParticleCulling
├── chunk/                   # ChunkUpdateTracker (статистика по секциям), ChunkUploadPacer (pacing аплоадов)
├── entity/                  # EntityTickThrottler, EntityCuller, ThrottlePolicy (чистая математика)
├── world/                   # LightmapCache (per-frame кэш света)
├── network/                 # NetworkOptimizer (батчинг particle-пакетов)
├── startup/                 # StartupOptimizer (фазы, prewarm, советы по JVM)
├── compat/                  # CompatibilityGuard (скан modов, решения), CompatReport (отчёт)
└── mixin/                   # 12 миксинов + accessor + MyOptimizeMixinPlugin (Compatibility Guard)
```

### Как устроены границы кадра
`MixinGameRenderer.renderWorld` (HEAD/RETURN) → `MyOptimizeUltra.onFrameStart/onFrameEnd` → инвалидация per-frame кэшей (O(1) generation bump), сброс счётчиков, запуск `UltraFrameScheduler`, периодический расчёт статистики (на воркере) и trim кэшей.

### Совместимость (Compatibility Guard)
- **VulkanMod** — единственный жёсткий конфликт: он заменяет весь рендерер. Наш mixin plugin **не применяет** render-миксины при его наличии; остальные оптимизации работают.
- **Iris/Oculus (шейдеры)** — включается *shader safe mode*: выключается texture-bind dedupe (cache bind'ов небезопасен в шейдерных пайплайнах). Остальное совместимо.
- **Sodium/Rubidium/Embeddium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, Krypton, C2ME, Starlight, Bobby, ModernFix, DynamicFPS** — совместимы (либо не пересекаются по точкам хука, либо делают родственную работу). Подробности — в `config/myoptimize-ultra/compat-report.txt`.

---

## 📏 Как измерять результат

1. **F3 → дашборд**: смотрите `FT` (средний frametime), `p99`, `1% low` и график. Цель — ровная линия графика и pacing score → 1.0.
2. **«Killer»** — если вдруг стало хуже, строка покажет, какая подсистема виновата.
3. **Dev mode** — для глубокого разбора (GC, аллокации, планировщик).
4. Сравните с ваниллой: выключите мод или профиль `vanilla`-эквивалент (все флаги по умолчанию безопасны; для чистого сравнения удалите мод).

---

## 🚫 Что мы намеренно НЕ делаем (честность)

- **Не меняем сетевой протокол** и не «оптимизируем» пакеты сервера — это читерство/бан.
- **Не трогаем redstone, физику жидкостей, despawn-таймеры, механики спавна** — это баланс.
- **Не делаем «фейковых» оптимизаций** (типа «отключим частицы совсем» по умолчанию или «уменьшим render distance» без спроса).
- **Не перехватываем vanilla chunk mesh builder** — в 1.21.1 он уже асинхронный; попытка «ускорить» его без глубокого рефакторинга даёт регрессии. Мы работаем поверх (pacing, метрики, статистика).
- **Не обещаем «+500 FPS»** — мы убираем реальные источники нагрузки и скачков frametime; конкретный прирост зависит от машины, сцены и модпака.
- Полный разбор по каждой оптимизации — в [`docs/OPTIMIZATION_JUSTIFICATION.md`](docs/OPTIMIZATION_JUSTIFICATION.md).

---

## 🗺 Структура проекта

```
myoptimize-ultra/
├── build.gradle / settings.gradle / gradle.properties   # сборка (Fabric Loom, Java 21)
├── src/main/java/com/myoptimize/ultra/...              # 40+ классов по модулям
├── src/main/resources/
│   ├── fabric.mod.json                                 # метаданные мода
│   ├── myoptimize-ultra.mixins.json                    # 12 миксинов + plugin + accessor
│   └── assets/myoptimize-ultra/{icon.png, lang/en_us.json, lang/ru_ru.json}
├── src/test/java/com/myoptimize/ultra/SmokeTest.java   # smoke-тест чистого ядра
├── docs/OPTIMIZATION_JUSTIFICATION.md                  # обоснование каждой оптимизации
├── README.md                                           # этот файл
└── LICENSE                                             # MIT
```

## 📄 Лицензия

MIT — см. `LICENSE`. Код написан с нуля; архитектура и оптимизационные механизмы — собственные (мы не копируем код Sodium/Lithium и др.).
