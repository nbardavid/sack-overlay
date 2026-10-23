package io.github.sackoverlay.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.sackoverlay.PageLayer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hooks for PageLayer: renders the pending page layer right before the GUI renderer that draws it,
 * and points the layer's own renderer at the layer texture instead of the main render target.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Shadow
    @Final
    private GuiRenderState renderState;

    @Inject(method = "render", at = @At("HEAD"))
    private void sackoverlay$renderPageLayer(CallbackInfo ci) {
        if (PageLayer.isLayerRenderer((GuiRenderer) (Object) this)) return;
        PageLayer.renderPending(renderState, state -> new GuiRenderer(state, featureRenderDispatcher, List.of()));
    }

    @WrapOperation(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
            )
    )
    private RenderTarget sackoverlay$layerTarget(GameRenderer gameRenderer, Operation<RenderTarget> original) {
        RenderTarget layer = PageLayer.targetFor((GuiRenderer) (Object) this);
        return layer != null ? layer : original.call(gameRenderer);
    }
}
