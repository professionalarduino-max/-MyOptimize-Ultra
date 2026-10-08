package com.myoptimize.ultra.gui;

import com.myoptimize.ultra.cache.CacheManager;
import com.myoptimize.ultra.cache.SmartCache;
import com.myoptimize.ultra.config.ConfigManager;

/**
 * Оптимизация GUI/текста:
 *  - кэш ширины строк (TextRenderer.getWidth — горячий путь меню/HUD);
 *  - пропуск отрисовки пустых строк.
 *
 * Кэш привязан к ресурсам: при reload (смена языка/ресурс-пака/шрифта)
 * он очищается — см. регистрацию reload-listener в MyOptimizeUltra.
 */
public final class GuiOptimizer {

    /** 4 МБ "веса" лимита — строки короткие, помещается очень много. */
    private static final SmartCache<String, Integer> WIDTH_CACHE =
        CacheManager.register(new SmartCache<>("font-width", 4L * 1024 * 1024));

    private GuiOptimizer() {
    }

    public static boolean shouldSkipEmpty(String text) {
        return ConfigManager.config().emptyTextSkip && (text == null || text.isEmpty());
    }

    public static Integer getCachedWidth(String text) {
        if (!ConfigManager.config().fontWidthCache || text == null || text.isEmpty()) {
            return null;
        }
        return WIDTH_CACHE.get(text);
    }

    public static void cacheWidth(String text, int width) {
        if (!ConfigManager.config().fontWidthCache || text == null || text.isEmpty()) {
            return;
        }
        // Вес: строка (объект + char[]) + autoboxed Integer + нода LRU.
        WIDTH_CACHE.put(text, width, 64L + text.length() * 2L);
    }

    public static void clearCaches() {
        WIDTH_CACHE.clear();
    }

    public static double widthCacheHitRate() {
        return WIDTH_CACHE.hitRate();
    }
}
