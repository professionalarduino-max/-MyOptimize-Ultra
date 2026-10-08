package com.myoptimize.ultra.mixin;

import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Доступ к protected-геттерам DebugHud (списки строк F3) для нашего оверлея.
 */
@Mixin(DebugHud.class)
public interface DebugHudAccessor {

    @Invoker("getLeftText")
    List<String> myoptimize$getLeftText();

    @Invoker("getRightText")
    List<String> myoptimize$getRightText();
}
