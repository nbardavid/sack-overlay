package io.github.sackoverlay.mixin;

import io.github.sackoverlay.GlintThrottle;
import net.minecraft.client.gui.render.GuiItemAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Limits how often animated items are redrawn in the GUI item atlas while our screens are open (see GlintThrottle).
 * Only the game's own atlas is touched. ImmediatelyFast's animated-item atlas clears itself every frame, so while
 * the limit applies it is told not to take the frame, and animated items stay in the game's atlas.
 */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasMixin {

    @Unique
    private static final String IMMEDIATELYFAST_ANIMATED_ATLAS =
            "net.raphimc.immediatelyfast.feature.batch_animated_item_updates.AnimatedItemAtlas";

    @ModifyArg(
            method = "getOrUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/DynamicAtlasAllocator;getOrAllocate(Ljava/lang/Object;Z)Lnet/minecraft/client/gui/render/DynamicAtlasAllocator$Slot;"
            ),
            index = 1
    )
    private boolean sackoverlay$throttleAnimated(boolean discardAfterFrame) {
        if (((Object) this).getClass() != GuiItemAtlas.class) return discardAfterFrame;
        return GlintThrottle.discardAfterFrame(discardAfterFrame);
    }

    @Inject(method = "tryPrepareFor", at = @At("HEAD"), cancellable = true, require = 0)
    private void sackoverlay$letImmediatelyFastStandAside(CallbackInfoReturnable<Boolean> cir) {
        if (GlintThrottle.isLimiting() && ((Object) this).getClass().getName().equals(IMMEDIATELYFAST_ANIMATED_ATLAS)) {
            cir.setReturnValue(false);
        }
    }
}
