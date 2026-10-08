package com.myoptimize.ultra.compat;

import com.myoptimize.ultra.MyOptimize;
import com.myoptimize.ultra.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Compatibility Guard — обнаруживает конфликты с популярными модами и
 * отключает ТОЛЬКО конфликтующую функцию, а не весь мод.
 *
 * Два уровня защиты:
 *  1. Mixin plugin (MyOptimizeMixinPlugin) — при загрузке VulkanMod не
 *     применяются наши render-миксины вовсе (VulkanMod заменяет весь
 *     рендерер; сосуществование невозможно).
 *  2. Runtime-флаги (этот класс) — функции, конфликт которых можно обойти
 *     точечным отключением (например, dedupe bind'ов текстур при шейдерах).
 *
 * По結果ам строится понятный отчёт (CompatReport).
 */
public final class CompatibilityGuard {

    /** Описание одного решения/конфликта. */
    public record Decision(String modId, String modName, String decision, String reason) {
    }

    /** Моды, за которыми мы наблюдаем (есть или нет — отражается в отчёте). */
    private static final String[] WATCHED_MODS = {
        "sodium", "lithium", "ferritecore", "immediatelyfast", "entityculling",
        "iris", "oculus", "rubidium", "embeddium", "vulkanmod", "modernfix",
        "krypton", "c2me", "starlight", "bobby", "dynamicfps", "polytone", "optifine"
    };

    private static final List<Decision> DECISIONS = new CopyOnWriteArrayList<>();
    private static volatile boolean shadersLoaded = false;
    private static volatile boolean vulkanLoaded = false;

    private CompatibilityGuard() {
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static boolean isShadersModLoaded() {
        return shadersLoaded;
    }

    public static boolean isVulkanModLoaded() {
        return vulkanLoaded;
    }

    /** true, если texture-bind оптимизация должна быть выключена (шейдеры/VulkanMod). */
    public static boolean shaderSafeModeActive() {
        return shadersLoaded || vulkanLoaded || ConfigManager.config().shaderSafeMode;
    }

    /** Сканирование при старте. Возвращает список принятых решений. */
    public static List<Decision> scan() {
        DECISIONS.clear();
        for (String modId : WATCHED_MODS) {
            if (!isModLoaded(modId)) {
                continue;
            }
            String name = modName(modId);
            switch (modId) {
                case "vulkanmod" -> {
                    vulkanLoaded = true;
                    ConfigManager.config().shaderSafeMode = true;
                    DECISIONS.add(new Decision(modId, name,
                        "render-миксины отключены (mixin plugin), texture-bind optimization выключен",
                        "VulkanMod заменяет весь рендерер; совместное применение невозможно"));
                }
                case "iris", "oculus" -> {
                    shadersLoaded = true;
                    ConfigManager.config().shaderSafeMode = true;
                    DECISIONS.add(new Decision(modId, name,
                        "включён shader safe mode: texture-bind optimization выключен",
                        "шейдерные пайплайны делают кэш bind'ов небезопасным"));
                }
                case "sodium", "rubidium", "embeddium" ->
                    DECISIONS.add(new Decision(modId, name, "совместим",
                        "заменяет chunk-рендер, наши хуки (сущности/частицы/сеть/память) не пересекаются"));
                case "lithium" ->
                    DECISIONS.add(new Decision(modId, name, "совместим",
                        "оптимизирует server-side AI; наш throttling tick'ов работает поверх него"));
                case "entityculling" ->
                    DECISIONS.add(new Decision(modId, name, "совместим (избыточен)",
                        "делает то же culling; ours просто не видит сущностей — хуже не становится"));
                case "immediatelyfast" ->
                    DECISIONS.add(new Decision(modId, name, "совместим",
                        "font batching; наши хуки TextRenderer — orthogonalны"));
                case "starlight" ->
                    DECISIONS.add(new Decision(modId, name, "совместим",
                        "заменяет light engine; наш lightmap-кэш читает уже финальные значения"));
                case "krypton", "c2me", "bobby", "modernfix", "ferritecore", "dynamicfps", "polytone" ->
                    DECISIONS.add(new Decision(modId, name, "совместим",
                        "не пересекается с нашими точками хука"));
                default ->
                    DECISIONS.add(new Decision(modId, name, "проверен", "в watchlist, конфликтов не ожидается"));
            }
        }
        MyOptimize.LOGGER.info("Compatibility Guard: {} модов из watchlist, решений: {}",
            countLoadedWatched(), DECISIONS.size());
        return decisions();
    }

    public static List<Decision> decisions() {
        return List.copyOf(DECISIONS);
    }

    private static int countLoadedWatched() {
        int count = 0;
        for (String modId : WATCHED_MODS) {
            if (isModLoaded(modId)) {
                count++;
            }
        }
        return count;
    }

    private static String modName(String modId) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        return container.map(c -> c.getMetadata().getName()).orElse(modId);
    }
}
