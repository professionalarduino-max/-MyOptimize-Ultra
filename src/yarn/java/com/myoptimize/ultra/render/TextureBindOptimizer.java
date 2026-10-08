package com.myoptimize.ultra.render;

import com.myoptimize.ultra.compat.CompatibilityGuard;
import com.myoptimize.ultra.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

/**
 * Оптимизация bind'ов текстур.
 *
 * Что делает: отслеживает последний привязанный GL-id на каждом texture unit
 * и пропускает повторные bind'ы. В GUI и при отрисовке HUD одни и те же
 * текстуры биндятся сотни раз за кадр; каждый bind — это как минимум проход
 * по состоянию GL (а в Identifier-варианте ещё и lookup в TextureManager).
 *
 * Честность: GlStateManager уже дедуплицирует сами GL-вызовы на уровне
 * железа-состояния; наша экономия — это пропуск lookup'а в TextureManager,
 * вызовов getGlId и накладных расходов RenderSystem на каждый вызов.
 * Поэтому выгода скромная, но и цена нулевая.
 *
 * Безопасность: кэш обязан синхронизироваться со ВСЕМИ путями bind'а:
 *  - RenderSystem.setShaderTexture(int, Identifier)  → маппинг id→glId
 *  - RenderSystem.setShaderTexture(int, int)         → прямой glId
 *  - RenderSystem.bindTexture(int)                   → бинд на активный юнит
 *  - RenderSystem.activeTexture(int)                 → трекинг активного юнита
 * В shader safe mode (Iris/Oculus) и с VulkanMod — отключено (CompatibilityGuard).
 */
public final class TextureBindOptimizer {

    private static final int MAX_UNITS = 16;
    private static final int GL_TEXTURE0 = 0x84C0;

    private static final int[] lastBoundGlId = new int[MAX_UNITS];
    private static int activeUnit = 0;

    private TextureBindOptimizer() {
    }

    public static boolean isEnabled() {
        return ConfigManager.config().textureBindOptimization && !CompatibilityGuard.shaderSafeModeActive();
    }

    /** Вызывается из MixinRenderSystem.activeTexture. */
    public static void onActiveTexture(int textureEnum) {
        int unit = textureEnum - GL_TEXTURE0;
        if (unit >= 0 && unit < MAX_UNITS) {
            activeUnit = unit;
        }
    }

    /** setShaderTexture(slot, glId): true = bind уже выполнен, можно пропустить. */
    public static boolean shouldSkipUnitBind(int slot, int glId) {
        if (!isEnabled() || slot < 0 || slot >= MAX_UNITS) {
            return false;
        }
        if (lastBoundGlId[slot] == glId) {
            return true;
        }
        lastBoundGlId[slot] = glId;
        return false;
    }

    /** setShaderTexture(slot, id): резолвим glId и проверяем кэш. Возвращает glId или -1 если надо выполнить bind. */
    public static int resolveAndCheck(int slot, Identifier id) {
        if (!isEnabled() || slot < 0 || slot >= MAX_UNITS) {
            return -1;
        }
        int glId = resolveGlId(id);
        if (glId < 0) {
            return -1;
        }
        if (lastBoundGlId[slot] == glId) {
            return glId; // уже привязан — сигнализируем "пропустить"
        }
        lastBoundGlId[slot] = glId;
        return -1;
    }

    /** bindTexture(glId) на активном юните. */
    public static boolean shouldSkipActiveBind(int glId) {
        if (!isEnabled()) {
            return false;
        }
        if (lastBoundGlId[activeUnit] == glId) {
            return true;
        }
        lastBoundGlId[activeUnit] = glId;
        return false;
    }

    private static int resolveGlId(Identifier id) {
        try {
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(id);
            return texture != null ? texture.getGlId() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }
}
