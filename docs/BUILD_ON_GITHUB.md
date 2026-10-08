# Как собрать MyOptimize Ultra на GitHub (пошагово)

Вам нужно: аккаунт на GitHub. Всё остальное — автоматом.

## Способ 1. GitHub Actions — собрать ВСЕ 24 версии одной кнопкой

1. Создайте репозиторий на github.com (например, `myoptimize-ultra`).
2. Скачайте проект себе, распакуйте, залейте в репозиторий:
   ```bash
   cd myoptimize-ultra
   git init
   git add .
   git commit -m "MyOptimize Ultra: multi-version 1.20-26.3"
   git branch -M main
   git remote add origin https://github.com/<ваш-ник>/myoptimize-ultra.git
   git push -u origin main
   ```
3. Откройте репозиторий на GitHub → вкладка **Actions** → слева выберите
   **«Build all Minecraft versions»** → кнопка **«Run workflow»** → **Run workflow**.
4. Ждите (≈10–20 минут, 24 параллельные сборки). Когда зелёное —開いてте любую
   сборку → внизу **Artifacts** → `myoptimize-ultra-v1.21.1` (и т.д.) → **Download**.
5. Готовый jar скачан. Чтобы получить **релиз со всеми jar'ами сразу**:
   ```bash
   git tag v1.0.0
   git push --tags
   ```
   → на странице репозитория появится **Releases → v1.0.0** со всеми 24 jar'ами.

Готово. Каждый push в репозиторий автоматически собирает все версии заново.

## Способ 2. GitHub Codespaces — собрать в браузере (как вы хотели)

1. На странице репозитория нажмите зелёную кнопку **«Code»**.
2. Вкладка **«Codespaces»** → **«Create codespace on main»**.
3. Откроется VS Code в браузере с терминалом внизу. В терминале:
   ```bash
   gradle wrapper          # один раз: создаст ./gradlew
   ./gradlew build         # собрать ВСЕ 24 версии
   ```
   Или одну конкретную:
   ```bash
   ./gradlew :v1.21.1:build     # jar для 1.21.1
   ./gradlew :v26.3:build       # jar для 26.3 (нужен Java 25 — подкачается сам)
   ```
4. Готовые jar'ы лежат в `versions/v<версия>/build/libs/`.
   Скачать из Codespaces: правый клик по файлу → **Download**.

## Способ 3. Локально на своём компьютере

Требуется JDK 21+ (скачать: https://adoptium.net).

```bash
git clone https://github.com/<ваш-ник>/myoptimize-ultra.git
cd myoptimize-ultra
gradle wrapper
./gradlew build
```

Готовые jar'ы: `versions/v<версия>/build/libs/`.

## Способ 4. Вообще без Gradle (слабый компьютер / 2 ГБ RAM)

Gradle на таких машинах падает от нехватки памяти — для этого есть ручной сборщик
(проверенный на 1.21.1, 1.20.1 и 26.3):

```bash
# для версий на yarn (1.20–1.21.11), нужен JDK 21:
JAVA_HOME=/path/to/jdk-21 bash scripts/build-manual.sh v1.21.1

# для 26.x, нужен JDK 25:
JAVA_HOME=/path/to/jdk-25 bash scripts/build-manual.sh v26.3
```

Выход: `versions/v<модуль>/build/libs/myoptimize-ultra-1.0.0+<версия>.jar`.

## Где что лежит в репозитории

| Путь | Что это |
|---|---|
| `versions/v1.20 … v26.3/` | 24 модуля, по одному на версию Minecraft |
| `versions/vX/gradle.properties` | зависимости модуля (yarn/mojmap, loader, Fabric API, Java) |
| `src/main/java` | общее ядро (все версии) |
| `src/yarn/java` + `src/mixin/yarn*` | код и миксины для 1.20–1.21.11 |
| `src/mojmap/java` + `src/mixin/mojmap` | код и миксины для 26.x |
| `.github/workflows/build.yml` | CI: матрица из 24 сборок + релиз по тегу |
| `.devcontainer/devcontainer.json` | окружение для Codespaces |
| `scripts/build-manual.sh` | ручная сборка без Gradle |
| `docs/MULTIVERSION_PLAN.md` | план мультиверсионности (матрица, era-группы) |
| `build/libs/`, `versions/*/build/libs/` | готовые jar'ы (тоже коммитятся) |

## Уже собранные jar'ы (лежат в репозитории)

- `build/libs/myoptimize-ultra-1.0.0.jar` — для Minecraft 1.21.1 (Fabric, Java 21)
- `versions/v1.20.1/build/libs/myoptimize-ultra-1.0.0+1.20.1.jar` — для 1.20.1 (Java 17)
- `versions/v26.3/build/libs/myoptimize-ultra-1.0.0+26.3.jar` — для 26.3 (Java 25)

Остальные 21 версия собираются Способом 1 или 2 — код для них уже написан.

## Установка мода в игру

1. Установите Fabric Loader под нужную версию Minecraft (https://fabricmc.net/use/).
2. Установите Fabric API (https://modrinth.com/mod/fabric-api) под ту же версию.
3. Закиньте jar мода в папку `mods/`.
4. Для 26.x: Fabric Loader 0.17+ и Java 25. Для 1.21.x: Java 21. Для 1.20–1.20.4: Java 17.
5. Настройки: клавиша **K** в игре (или файл `config/myoptimize-ultra.json`).
