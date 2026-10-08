package com.myoptimize.ultra.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.myoptimize.ultra.MyOptimize;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Загрузка/сохранение {@link ModConfig}.
 *
 * Формат — JSON (Gson, идёт в комплекте с Minecraft). Путь:
 * {@code config/myoptimize-ultra.json}.
 *
 * Поведение при отсутствии/битом файле: дефолты + предупреждение в логе.
 * После загрузки применяется миграция версий (configVersion).
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("myoptimize-ultra.json");

    private static volatile ModConfig config = new ModConfig();

    private ConfigManager() {
    }

    public static ModConfig config() {
        return config;
    }

    public static Path configPath() {
        return CONFIG_PATH;
    }

    public static synchronized void load() {
        ModConfig loaded = null;
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                MyOptimize.LOGGER.warn("Не удалось прочитать конфиг, будут использованы настройки по умолчанию: {}", e.toString());
            }
        }
        if (loaded == null) {
            loaded = new ModConfig();
        }
        migrate(loaded);
        config = loaded;
        MyOptimize.LOGGER.info("Конфигурация загружена: профиль={}, кэш-бюджет={} МБ",
            loaded.profile, loaded.cacheBudgetMb);
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            MyOptimize.LOGGER.error("Не удалось сохранить конфиг", e);
        }
    }

    /** Приведение старых конфигов к текущей версии. */
    private static void migrate(ModConfig cfg) {
        if (cfg.configVersion < 1) {
            cfg.configVersion = 1;
        }
        // Здесь будут миграции при появлении новых полей.
    }
}
