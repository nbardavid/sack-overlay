package io.github.sackoverlay.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * imageWidth / imageHeight are final in 26.2. They are set to the real size of our interface
 * so other mods (Skyblocker tabs, buttons around the inventory...) place themselves around it.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Mutable
    @Accessor("imageWidth")
    void sackoverlay_setImageWidth(int width);

    @Mutable
    @Accessor("imageHeight")
    void sackoverlay_setImageHeight(int height);
}
