package io.github.sackoverlay.mixin;

import io.github.sackoverlay.GlintThrottle;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ends the frame for GlintThrottle once everything has been drawn. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void sackoverlay$endFrame(CallbackInfo ci) {
        GlintThrottle.endFrame();
    }
}
