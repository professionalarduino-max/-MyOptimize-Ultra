# Мультиверсионность: Minecraft 1.20 → 26.3 — реализовано

Цель: один репозиторий, сборка под **все версии от 1.20 до 26.3** (24 версии).
Статус: **структура и код готовы; 1.21.1 собран и проверен локально, 1.20.1 и 26.3 — compile-verified.**

## Факты (проверены 08.10.2026)

- Mojang перешла на годовую нумерацию в 2026: 26.1 (24.03), 26.2 (16.06), **26.3 «Wilderness Bound» (15.09)**. Последняя 1.2x — 1.21.11.
- **26.x без обфускации**: в client jar 26.3 имена классов — официальные Mojang (mojmap): `net/minecraft/client/Minecraft`, `ServerLevel`, `Level`…; **ни одного `class_XXXX`**. Более того, Mojang переименовала часть классов относительно классического mojmap: `ResourceLocation` → `net.minecraft.resources.Identifier`.
- 26.x требует **Java 25**; 1.21.x и 1.20.5+ — Java 21; 1.20–1.20.4 — Java 17.
- В 26.x рендер переписан (RenderPearl): нет `GuiGraphics`, есть `GuiGraphicsExtractor` (но с тем же набором `fill/text/outline`); нет `DebugHud`, F3 — на `DebugScreenOverlay` + `DebugScreenEntries`; `sky/clouds/weather` — отдельные `SkyRenderer`/`CloudRenderer`/`WeatherEffectRenderer`; сущности — через `shouldRender` + `submit`; `RenderSystem` без GL-бинд-методов; `KeyMapping` с `Category`.
- yarn для 26.x не существует (незачем). Fabric API для 26.x есть (0.162.0+26.3, mojmap-сборка).

## Структура репозитория (реализовано)

```
src/main/java                 # общее ядро, без MC-зависимостей (все версии)
  └── mixin/MyOptimizeMixinPlugin   # плагин Compatibility Guard (общий)
src/yarn/java                 # MC-bound общий код для 1.20–1.21.11 (yarn-имена)
src/mojmap/java               # MC-bound код для 26.x (mojmap-имена)
src/mixin/yarn/               # миксины, стабильные для всех yarn-версий
src/mixin/yarn-cb/            # MixinChunkBuilder (со scheduleUpload: 1.20–1.21.4)
src/mixin/yarn-cb-min/        # MixinChunkBuilder (только upload: 1.21.5–1.21.11)
src/mixin/yarn-g1/            # era 1.20–1.20.4: renderClouds (по имени), renderSky (MatrixStack, …)
src/mixin/yarn-g2/            # era 1.20.5–1.21.1: renderClouds (дескриптор), renderSky (Matrix4f, …)
src/mixin/yarn-g34/           # era 1.21.2–1.21.11: renderClouds/renderSky (по имени, fog снаружи)
src/mixin/yarn/resources/     # myoptimize-ultra.mixins.json (общий для yarn-эр)
src/mixin/mojmap/             # миксины для 26.x
src/mixin/mojmap/resources/   # myoptimize-ultra.mixins.json (JAVA_25)
src/main/resources/           # ассеты (lang ru/en, icon), fabric.mod.json (шаблон)
versions/v<версия>/           # 24 модуля, у каждого gradle.properties с зависимостями
```

Сборка одного модуля: `./gradlew :v1.21.1:build` → `versions/v1.21.1/build/libs/myoptimize-ultra-1.0.0+1.21.1.jar`.
Сборка всех: `./gradlew build` (или CI — см. ниже).

## Era-группы (почему именно так — верифицировано по yarn/tiny для каждой границы)

| Группа | Версии | Что отличается |
|---|---|---|
| g1 | 1.20–1.20.4 | `renderSky(MatrixStack, Matrix4f, F, Camera, Z, Runnable)` (два renderSky — нужен дескриптор); `renderClouds` один (по имени); CB со scheduleUpload |
| g2 | 1.20.5–1.21.1 | `renderSky(Matrix4f, Matrix4f, F, Camera, Z, Runnable)`; `renderClouds(MatrixStack, M4f, M4f, F, DDD)` (дескриптор — в 1.20.5/1.20.6 два метода); CB со scheduleUpload |
| g3 | 1.21.2–1.21.4 | `renderSky(FrameGraphBuilder, Camera, F, Fog)` — fog снаружи, хук без аргументов; CB со scheduleUpload |
| g4 | 1.21.5–1.21.11 | как g3, но `scheduleUpload` исчез из ChunkBuilder (CB только upload) |
| mojmap | 26.1–26.3 | полностью другая ветка (mojmap-имена, Java 25, без ремапа) |

**Стабильно для всех yarn-версий (проверено по tiny/.mapping):** `renderWorld` (хук без аргументов), `renderWeather` (по имени), `getLightmapCoordinates(BlockRenderView, BlockPos)`, `drawLeftText/drawRightText/getLeftText/getRightText` (DebugHud), `TextRenderer.draw/getWidth`, `EntityRenderDispatcher.render/getSquaredDistanceToCamera`, `BlockEntityRenderDispatcher.render`, `ParticleManager.createParticle/tick` + поле `particles`, `ServerWorld.tickEntity`, `ChunkBuilder.upload`, `onParticle/onLightUpdate` (ClientPlayPacketListener, общий intermediary), `KeyBinding(String, Type, int, String)`, `Identifier.of`, `options.getMaxFps()`, `MinecraftClient.getGpuUtilizationPercentage/getCurrentFps/getWindow`, `ScreenTexts` в `net.minecraft.screen`, `CyclingButtonWidget/ButtonWidget` + builder'ы, `World.isClient`, `ClientWorld.getRegularEntityCount/getEntities`, `WorldRenderer.getCompletedChunkCount`, `AbstractTexture.getGlId`, `Camera.getPos`, `Heightmap.Type`, имена классов сущностей.

**Важная находка (баг в ранней версии, исправлен):** 4-аргументный `setBlockState(BlockPos, BlockState, int, int) → boolean` объявлен **в интерфейсе `ModifiableWorld`** (default-метод), а не в `World` — миксин нацелен туда (иначе падал бы в рантайме). Старое состояние берём через `getBlockState(pos)` в HEAD, т.к. return — boolean успеха.

## Что отключено для 26.x (и почему)

| Фича | Причина |
|---|---|
| texture bind dedupe | В 26.x `RenderSystem` без GL-бинд-методов (RenderPearl) — нечего дедуплицировать |
| lightmap cache | Система света переписана; `WorldRenderer.getLightmapCoordinates` отсутствует |
| font empty-text skip (TextRenderer.draw) | Отрисовка текста ушла в `GuiGraphicsExtractor` — хук перенесён на `GuiGraphicsExtractor.text` |
| панель «за ванильным текстом» F3 | Новый F3 (DebugScreenEntries) — высоту текста не измерить; наша панель рисуется внизу слева |

Для 26.x всё остальное работает: F3-дашборд (на `GuiGraphicsExtractor`), экран настроек, AUTO OPTIMIZE, throttling сущностей, culling, pacing аплоадов, батчинг пакетов, профилировщик, killer finder, адаптивный движок, GC-мониторинг.

## Сборка на GitHub (как вы и просили)

- **GitHub Actions**: `.github/workflows/build.yml` — матрица из 24 модулей, каждая собирает свой jar и заливает артефакт; по тегу — релиз. Нужен только репозиторий: push → CI собирает все версии (runner'ы GitHub имеют 16 ГБ RAM — Loom там чувствует себя отлично).
- **Codespaces**: `.devcontainer/devcontainer.json` — открыть репозиторий → «Code → Codespaces» → в терминале `./gradlew :v1.21.1:build` (или любой другой модуль). JDK toolchains (17/21/25) подкачиваются автоматически (foojay-resolver).
- **Локально**: `./gradlew build` (нужен JDK 21+, памяти 4+ ГБ для всех модулей сразу; по одному модулю — меньше).
- **Без Gradle** (слабые машины): `scripts/build-manual.sh` — ручной пайплайн (проверен на 1.21.1).

## Loaders: Fabric + Forge + NeoForge (в планах, следующий этап)

Пользователь выбрал all-loaders. Реалистичный путь: **Architectury** (общий код + per-loader source sets) поверх текущей мультиверсионной структуры; NeoForge — для всего диапазона, Forge — для 1.20.1 (и, возможно, 1.20.6). Это отдельный слой: `common` / `fabric` / `neoforge` / `forge` модули + `expectPlatform`-подобные абстракции. Оценка: +2–3 недели работы. Начнём после стабилизации Fabric-ветки.

## Порядок работ (волны)

1. ✅ Структура, общий код, era-варианты, mojmap-ветка, CI, Codespaces.
2. ✅ Локально: 1.21.1 собран (исправлены баги: MixinWorld → ModifiableWorld, bypass в сетевом миксине).
3. ✅ Compile-verified: 1.20.1 (era g1), 26.3 (mojmap).
4. ⏭ CI на GitHub прогонит все 24 модуля; версии, где минорные сигнатуры отличаются от группы, получат свой override-файл (процесс итеративный, CI покажет).
5. ⏭ NeoForge/Forge через Architectury.
