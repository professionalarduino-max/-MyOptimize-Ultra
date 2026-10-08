package com.myoptimize.ultra.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Compatibility Guard (уровень mixin plugin).
 *
 * Единственный известный жёсткий конфликт — VulkanMod: он заменяет весь
 * рендерер целиком, поэтому наши render-миксины вместе с ним применять нельзя.
 * Plugin решает это ДО применения миксинов: такие миксины просто не
 * применяются, остальные (сущности, мир, сеть, чанк-логика) работают.
 *
 * Всё остальное (Sodium, Iris, Lithium и т.д.) конфликтует точечно и
 * разруливается рантайм-флагами — см. compat/CompatibilityGuard.
 */
public class MyOptimizeMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("MyOptimize Ultra/Compat");

    /** Рендер-миксины, несовместимые с VulkanMod (yarn- и mojmap-ветки). */
    private static final Set<String> RENDER_MIXINS = Set.of(
        // yarn (1.20–1.21.11)
        "MixinWorldRenderer",
        "MixinWorldRendererClouds",
        "MixinWorldRendererSky",
        "MixinRenderSystem",
        "MixinTextRenderer",
        "MixinEntityRenderDispatcher",
        "MixinBlockEntityRenderDispatcher",
        "MixinGameRenderer",
        "MixinChunkBuilder",
        // mojmap (26.x)
        "MixinSkyRenderer",
        "MixinCloudRenderer",
        "MixinWeatherEffectRenderer",
        "MixinSectionRenderDispatcher",
        "MixinGuiGraphicsExtractor"
    );

    private boolean vulkanModLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        vulkanModLoaded = FabricLoader.getInstance().isModLoaded("vulkanmod");
        if (vulkanModLoaded) {
            LOGGER.warn("Обнаружен VulkanMod — наши render-миксины будут отключены (Compatibility Guard). " +
                "Остальные оптимизации (сущности, память, кэши, профилировщик) продолжают работать.");
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (vulkanModLoaded) {
            String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
            if (RENDER_MIXINS.contains(simple)) {
                LOGGER.info("Compatibility Guard: миксин {} пропущен (VulkanMod)", simple);
                return false;
            }
        }
        return true;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
