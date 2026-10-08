package com.myoptimize.ultra.compat;

import com.myoptimize.ultra.MyOptimize;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Пишет понятный отчёт о совместимости в config/myoptimize-ultra/compat-report.txt.
 */
public final class CompatReport {

    private CompatReport() {
    }

    public static Path write() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("myoptimize-ultra");
        Path file = dir.resolve("compat-report.txt");
        try {
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write("MyOptimize Ultra — отчёт о совместимости\n");
                writer.write("Дата: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n");
                writer.write("Версия мода: " + FabricLoader.getInstance()
                    .getModContainer("myoptimize-ultra")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown") + "\n\n");
                writer.write("=== Установленные моды из watchlist ===\n");
                List<CompatibilityGuard.Decision> decisions = CompatibilityGuard.decisions();
                if (decisions.isEmpty()) {
                    writer.write("(пусто — моды из watchlist не установлены)\n");
                }
                for (CompatibilityGuard.Decision d : decisions) {
                    writer.write(String.format("- %s (%s): %s%n  причина: %s%n",
                        d.modName(), d.modId(), d.decision(), d.reason()));
                }
                writer.write("\n=== Все установленные моды ===\n");
                FabricLoader.getInstance().getAllMods().forEach(mod -> {
                    try {
                        writer.write("- " + mod.getMetadata().getId() + " " + mod.getMetadata().getVersion().getFriendlyString() + "\n");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            MyOptimize.LOGGER.info("Compat-отчёт записан: {}", file);
        } catch (IOException e) {
            MyOptimize.LOGGER.warn("Не удалось записать compat-отчёт: {}", e.toString());
        }
        return file;
    }
}
