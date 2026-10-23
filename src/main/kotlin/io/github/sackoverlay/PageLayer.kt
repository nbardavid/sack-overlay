package io.github.sackoverlay

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import org.joml.Matrix3x2f
import org.joml.Vector2f
import java.util.function.Function

/**
 * Offscreen layer for the sack pages, the same technique as Enhanced Storage's page cache.
 *
 * The game rebuilds the whole GUI every frame: every slot, item, label and frame of every page is extracted,
 * turned into vertices and drawn again, although the pages almost never change. Instead, the pages are extracted
 * into this layer's own GUI state only when something they show changes, rendered by a second vanilla GuiRenderer
 * into a window-sized texture, and every frame the texture is drawn as one quad.
 *
 * The layer is rendered right before the GUI renderer that draws our screen (see GuiRendererMixin), which also
 * points the layer's renderer at our texture instead of the main render target.
 */
object PageLayer {

    /** Mouse position used while extracting, so nothing in the layer draws as hovered. */
    private const val OFFSCREEN_MOUSE = -9999
    private const val RELEASE_AFTER_IDLE_MS = 10_000L

    private val state = GuiRenderState()
    private val dirty = mutableListOf<ScreenRectangle>()
    private var renderer: GuiRenderer? = null
    private var target: RenderTarget? = null
    private var generation = 0
    // The main GUI extractor of the frame the pending layer was extracted in (the game makes a new one per frame)
    private var pendingExtractor: GuiGraphicsExtractor? = null
    private var lastUsed = 0L

    @JvmStatic
    fun targetFor(guiRenderer: GuiRenderer): RenderTarget? = if (guiRenderer === renderer) target else null

    @JvmStatic
    fun isLayerRenderer(guiRenderer: GuiRenderer): Boolean = guiRenderer === renderer

    /**
     * Prepares the texture for this frame and returns the layer generation. It changes whenever the layer's pixels
     * may have been lost (texture created or resized, or an extraction that never got rendered): the pages must
     * then be extracted again.
     */
    fun sync(main: GuiGraphicsExtractor): Int {
        lastUsed = System.currentTimeMillis()
        ensureTarget()
        if (pendingExtractor != null && pendingExtractor !== main) {
            discardPending()
            generation++
        }
        return generation
    }

    /**
     * Starts redrawing the area (x, y, w, h): returns an extractor writing into the layer, with the caller's pose,
     * a scissor to the area and the mouse far away. Finish with [endLayer].
     */
    fun beginLayer(main: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int): GuiGraphicsExtractor {
        pendingExtractor = main
        val layer = GuiGraphicsExtractor(Minecraft.getInstance(), state, OFFSCREEN_MOUSE, OFFSCREEN_MOUSE)
        layer.pose().set(main.pose())
        layer.enableScissor(x, y, x + w, y + h)
        // The scissor stack holds the area already transformed by the pose, i.e. where the pixels land
        layer.scissorStack.peek()?.let { dirty += it }
        return layer
    }

    fun endLayer(layer: GuiGraphicsExtractor) {
        while (layer.scissorStack.peek() != null) layer.disableScissor()
    }

    /** Draws the layer's pixels for this screen rectangle into the main GUI, clipped to its current scissor. */
    fun blit(main: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int) {
        val layer = target ?: return
        val view = layer.colorTextureView ?: return
        val scale = Minecraft.getInstance().window.guiScale
        // The layer was drawn with the same pose, so sample where the pose put these corners
        val from = main.pose().transformPosition(x0.toFloat(), y0.toFloat(), Vector2f())
        val to = main.pose().transformPosition(x1.toFloat(), y1.toFloat(), Vector2f())
        val u0 = from.x * scale / layer.width
        val u1 = to.x * scale / layer.width
        // Render targets are stored bottom-up
        val v0 = 1f - from.y * scale / layer.height
        val v1 = 1f - to.y * scale / layer.height
        main.guiRenderState.addGuiElement(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                Matrix3x2f(main.pose()),
                x0, y0, x1, y1,
                u0, u1, v0, v1,
                -1,
                main.scissorStack.peek(),
            ),
        )
    }

    /**
     * Renders the layer extracted this frame, right before the GUI renderer that draws our screen (whose state is
     * [screenState]). Called from GuiRendererMixin.
     */
    @JvmStatic
    fun renderPending(screenState: GuiRenderState, factory: Function<GuiRenderState, GuiRenderer>) {
        val main = pendingExtractor
        if (main == null) {
            if ((renderer != null || target != null) && System.currentTimeMillis() - lastUsed > RELEASE_AFTER_IDLE_MS) release()
            return
        }
        // Wait for the renderer that draws the blit, so the layer gets its GUI scale
        if (main.guiRenderState !== screenState) return
        val layer = target ?: return discardPending()
        val layerRenderer = renderer ?: factory.apply(state).also { renderer = it }

        clearDirty(layer)
        pendingExtractor = null
        layerRenderer.render()
        layerRenderer.endFrame()
        // Leave the lighting the main GUI renderer expects for 3D items
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D)
    }

    private fun clearDirty(layer: RenderTarget) {
        val scale = Minecraft.getInstance().window.guiScale
        val color = layer.colorTexture ?: return
        val depth = layer.depthTexture ?: return
        for (rect in dirty) {
            val left = (rect.left() * scale).coerceIn(0, layer.width)
            val right = (rect.right() * scale).coerceIn(0, layer.width)
            val top = (rect.top() * scale).coerceIn(0, layer.height)
            val bottom = (rect.bottom() * scale).coerceIn(0, layer.height)
            if (right <= left || bottom <= top) continue
            // 26.2 clears the GUI depth buffer to 0 (reverse Z)
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                color, GuiRenderer.CLEAR_COLOR, depth, 0.0,
                left, layer.height - bottom, right - left, bottom - top,
            )
        }
        dirty.clear()
    }

    // Sized from the window, not the main render target, which may still have last frame's size during extraction
    private fun ensureTarget() {
        val window = Minecraft.getInstance().window
        val width = window.width
        val height = window.height
        if (width <= 0 || height <= 0) return
        val current = target
        if (current == null) {
            target = TextureTarget("Sack Overlay page layer", width, height, true, GpuFormat.RGBA8_UNORM)
            generation++
        } else if (current.width != width || current.height != height) {
            current.resize(width, height)
            generation++
        }
    }

    private fun discardPending() {
        state.reset()
        dirty.clear()
        pendingExtractor = null
    }

    private fun release() {
        renderer?.close()
        target?.destroyBuffers()
        renderer = null
        target = null
        discardPending()
        generation++
    }
}
