package io.github.sackoverlay.mixin;

import io.github.sackoverlay.SackContainerScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * When the server opens the Sack of Sacks, show our screen instead,
 * built on the same menu: nothing is sent to the server.
 */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen sackoverlay$swapScreen(Screen screen) {
        return SackContainerScreen.swap(screen);
    }
}
