package lol.gzmc.vivecraftx.render;

import com.mtbs3d.minecrift.api.IStereoProvider;
import com.mtbs3d.minecrift.render.RenderConfigException;
import com.mtbs3d.minecrift.settings.VRSettings;

import de.fruitfly.ovr.structs.RenderTextureInfo;
import de.fruitfly.ovr.structs.RenderTextureSet;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.api.IMinecraftExt;
import lol.gzmc.vivecraftx.api.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.opengl.GL11;

/**
 * Renders the frame once per eye and hands the result to the compositor.
 *
 * <p>The shape is Vivecraft's. The scene is drawn into one offscreen buffer sized by the
 * headset's recommended resolution, then blitted into the texture the runtime owns for that eye,
 * and both are submitted at the end of the frame. Keeping the scene buffer separate from the eye
 * textures is what lets the render scale differ from the headset's native size.
 *
 * <p>Driven from a single {@code @Redirect} of the one render call in {@code runGameLoop},
 * rather than by rewriting the loop the way the patch set did: the body has to stay intact
 * because Angelica injects into it as well.
 */
public final class VRRenderer {

    private VRRenderer() {}

    private static VRFramebuffer sceneBuffer;
    private static VRFramebuffer leftEye;
    private static VRFramebuffer rightEye;

    /**
     * Who the eye textures belong to.
     *
     * <p>Remembered rather than asked for, because the provider is replaced whenever VR is
     * switched off and on, and asking the new one to free the old one textures would free
     * nothing and leak two full-resolution textures per cycle.
     */
    private static IStereoProvider textureOwner;

    private static int eyeWidth;
    private static int eyeHeight;

    /** Set after a setup failure, so a broken configuration is reported once and not every frame. */
    private static boolean broken;

    public static boolean isReady() {
        return sceneBuffer != null && leftEye != null && rightEye != null;
    }

    /**
     * Builds the buffers, or rebuilds them when the render scale changed.
     *
     * @return false when VR rendering cannot run this frame
     */
    public static boolean ensureSetup() {
        if (broken || !VRState.running() || VR.stereo() == null) {
            return false;
        }
        if (isReady() && !VRRenderState.reinitFramebuffers) {
            return true;
        }
        try {
            build();
            VRRenderState.reinitFramebuffers = false;
            return true;
        } catch (Throwable t) {
            ViveCraftX.LOG.error("Could not set up VR framebuffers, falling back to flat", t);
            broken = true;
            release();
            return false;
        }
    }

    private static void build() throws Exception {
        release();

        float scale = VR.settings() != null ? VR.settings().renderScaleFactor : 1.0F;
        RenderTextureInfo info = VR.stereo().getRenderTextureSizes(scale);
        if (info == null) {
            throw new RenderConfigException("VR setup", "Headset gave no render texture size");
        }
        eyeWidth = info.LeftFovTextureResolution.w;
        eyeHeight = info.LeftFovTextureResolution.h;
        if (eyeWidth <= 0 || eyeHeight <= 0) {
            throw new RenderConfigException("VR setup", "Headset reported a zero eye size");
        }

        RenderTextureSet textures = VR.stereo().createRenderTexture(eyeWidth, eyeHeight);
        textureOwner = VR.stereo();
        if (textures == null || textures.leftEyeTextureIds.isEmpty()
                || textures.rightEyeTextureIds.isEmpty()) {
            throw new RenderConfigException("VR setup", "Could not create the eye textures");
        }
        int texLeft = textures.leftEyeTextureIds.get(0);
        int texRight = textures.rightEyeTextureIds.get(0);

        leftEye = VRFramebuffer.wrapping("L Eye", eyeWidth, eyeHeight, texLeft);
        rightEye = VRFramebuffer.wrapping("R Eye", eyeWidth, eyeHeight, texRight);

        int sceneW = (int) Math.ceil(eyeWidth * scale);
        int sceneH = (int) Math.ceil(eyeHeight * scale);
        sceneBuffer = new VRFramebuffer("3D Render", sceneW, sceneH, true);

        ViveCraftX.LOG.info("VR framebuffers ready: eye {}x{}, scene {}x{} (scale {})",
                eyeWidth, eyeHeight, sceneW, sceneH, scale);
    }

    private static void release() {
        for (VRFramebuffer fb : new VRFramebuffer[] { sceneBuffer, leftEye, rightEye }) {
            if (fb != null) {
                try {
                    fb.deleteFramebuffer();
                } catch (Throwable ignored) {
                    // tearing down after a failure; nothing useful to do about a second failure
                }
            }
        }
        sceneBuffer = null;
        leftEye = null;
        rightEye = null;

        // After the framebuffers, which were wrapping them: the wrappers deliberately leave the
        // textures alone, because the runtime owns them, so this is the only place they go.
        if (textureOwner != null) {
            try {
                textureOwner.deleteRenderTextures();
            } catch (Throwable ignored) {
                // tearing down; a second failure here tells nobody anything useful
            }
            textureOwner = null;
        }
    }

    /**
     * Draws the frame for both eyes and submits it.
     *
     * @param drawOnce renders the scene once, with the current pass already set
     * @return false if VR could not render, so the caller should fall back to a flat frame
     */
    public static boolean renderStereo(Runnable drawOnce) {
        if (!ensureSetup()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        IMinecraftExt ext = (IMinecraftExt) mc;
        Framebuffer previous = ext.vivecraftx$getFramebufferMc();

        try {
            drawEye(RenderPass.Left, leftEye, ext, drawOnce);
            // Onto the monitor while this eye is still in the scene buffer, before the other one
            // overwrites it. Without this the window keeps whatever flat frame it had when VR
            // started, frozen -- which is all anybody watching sees, and all the player sees when
            // they take the headset off.
            mirrorEye(RenderPass.Left, previous);
            drawEye(RenderPass.Right, rightEye, ext, drawOnce);
            mirrorEye(RenderPass.Right, previous);
            VR.stereo().endFrame();
            return true;
        } catch (RenderConfigException e) {
            ViveCraftX.LOG.error("Compositor rejected the frame ({}), falling back to flat: {}",
                    e.title, e.error);
            fail();
            return false;
        } catch (Throwable t) {
            ViveCraftX.LOG.error("VR frame failed, falling back to flat", t);
            fail();
            return false;
        } finally {
            ext.vivecraftx$setFramebufferMc(previous);
            // And bind it, not just name it. The last thing an eye does is unbind itself, which
            // leaves the window itself bound -- so the overlays the game draws after this, the
            // debug screen and the achievement pop-ups, were landing on the window and then
            // being painted over by the blit that follows. Vanilla keeps this bound across the
            // whole stretch; so must we.
            if (previous != null) {
                previous.bindFramebuffer(true);
            }
            VR.setCurrentPass(RenderPass.Center);
        }
    }

    /**
     * Copies what the headset is being shown onto the desktop window.
     *
     * <p>Called once after each eye, because one of the modes wants both and the second eye
     * overwrites the first in the scene buffer. The modes that want one eye do nothing on the
     * second call.
     *
     * <p>The target is the window framebuffer, because the game blits that to the screen itself
     * once the frame returns -- so nothing else has to be arranged, and in particular there is no
     * second submit to the compositor. That is how minecrift did this, and why it used to offer
     * "full frame rate" and "one third frame rate" variants of every mode; neither distinction
     * means anything when the picture is a texture we already have.
     */
    private static void mirrorEye(RenderPass pass, Framebuffer window) {
        if (window == null) {
            return;
        }
        VRSettings settings = VR.settings();
        int mode = settings == null ? VRSettings.MIRROR_ON_CROPPED : settings.displayMirrorMode;
        boolean dual = mode == VRSettings.MIRROR_ON_DUAL;
        boolean left = pass == RenderPass.Left;

        // Which of the two calls does the work. The modes that show one eye do it on that eye;
        // the ones that show neither do it on the first call, so the window is not left holding
        // the previous frame for half of one.
        boolean showsAnEye = mode != VRSettings.MIRROR_OFF && mode != VRSettings.MIRROR_GUI;
        boolean wantsLeft = settings == null || settings.displayMirrorLeftEye;
        if (!dual && left != (showsAnEye ? wantsLeft : true)) {
            return;
        }

        int width = window.framebufferWidth;
        int height = window.framebufferHeight;
        if (width <= 0 || height <= 0) {
            return;
        }

        beginBlit(window, width, height);
        try {
            // Dual draws two halves in two calls, so only the first may clear.
            if (!dual || left) {
                GL11.glClearColor(0F, 0F, 0F, 1F);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            }
            switch (mode) {
                case VRSettings.MIRROR_OFF:
                    // Cleared above and nothing drawn: a black window rather than a frozen one.
                    break;
                case VRSettings.MIRROR_ON_SINGLE:
                    fit(sceneBuffer, 0, 0, width, height);
                    break;
                case VRSettings.MIRROR_ON_DUAL:
                    fill(sceneBuffer, left ? 0 : width / 2F, 0,
                            left ? width / 2F : width, height, 0F);
                    break;
                case VRSettings.MIRROR_GUI:
                    // Already the size of the window, so this comes out one to one.
                    fit(VRGui.panelBuffer(), 0, 0, width, height);
                    break;
                case VRSettings.MIRROR_ON_CROPPED:
                default:
                    fill(sceneBuffer, 0, 0, width, height,
                            settings == null ? 0.15F : settings.mirrorCrop);
                    break;
            }
            // On the last call, so it is not drawn under the other half of a dual mirror.
            if (mode != VRSettings.MIRROR_GUI && (!dual || !left)) {
                drawGuiOverlay(width, height);
            }
        } finally {
            endBlit(window);
        }
    }

    /**
     * Draws the interface flat on top of the mirror, for whoever is watching the monitor.
     *
     * <p>The interface is in the world either way, and the mirror already shows it there as a
     * panel the player is looking at -- which is to say, at whatever angle and distance the
     * player happens to be holding it, and often off the side of the picture entirely. This puts
     * a readable copy over the top for everyone else.
     *
     * <p>Off by default, because for the player it is a second copy of something they can already
     * see.
     */
    private static void drawGuiOverlay(int width, int height) {
        VRSettings settings = VR.settings();
        if (settings == null || settings.guiOnMirror == VRSettings.GUI_MIRROR_OFF) {
            return;
        }
        if (settings.guiOnMirror == VRSettings.GUI_MIRROR_HUD_ONLY
                && Minecraft.getMinecraft().currentScreen != null) {
            return;
        }
        VRFramebuffer panel = VRGui.panelBuffer();
        if (panel == null) {
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColorMask(true, true, true, true);
        fit(panel, 0, 0, width, height);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * The whole source, as large as fits inside the rectangle without changing its shape.
     *
     * <p>An eye is close to square and a monitor is wide, so filling the window would squash the
     * picture; this keeps the shape and leaves black at the sides.
     */
    private static void fit(VRFramebuffer src, float x0, float y0, float x1, float y1) {
        if (src == null) {
            return;
        }
        float boxWidth = x1 - x0;
        float boxHeight = y1 - y0;
        float aspect = (float) src.framebufferWidth / src.framebufferHeight;
        float drawWidth = boxWidth;
        float drawHeight = boxWidth / aspect;
        if (drawHeight > boxHeight) {
            drawHeight = boxHeight;
            drawWidth = boxHeight * aspect;
        }
        float left = x0 + (boxWidth - drawWidth) * 0.5F;
        float top = y0 + (boxHeight - drawHeight) * 0.5F;
        blit(src, left, top, left + drawWidth, top + drawHeight, 0F, 0F, 1F, 1F);
    }

    /**
     * The rectangle filled edge to edge, with whatever of the source does not fit cut off.
     *
     * <p>What a headset mirror normally looks like, and the default here: a monitor showing black
     * bars is a monitor wasting most of itself, and what gets cut are the extreme edges of
     * vision, which the lens distorts beyond use anyway.
     */
    private static void fill(VRFramebuffer src, float x0, float y0, float x1, float y1,
            float crop) {
        if (src == null) {
            return;
        }
        float boxAspect = (x1 - x0) / (y1 - y0);
        float aspect = (float) src.framebufferWidth / src.framebufferHeight;
        // Half the span kept on each axis, starting from the edge the player asked to throw away.
        float halfU = 0.5F - crop;
        float halfV = 0.5F - crop;
        if (boxAspect > aspect) {
            // the window is the wider shape: keep the full width and take more off top and bottom
            halfV *= aspect / boxAspect;
        } else {
            halfU *= boxAspect / aspect;
        }
        blit(src, x0, y0, x1, y1, 0.5F - halfU, 0.5F - halfV, 0.5F + halfU, 0.5F + halfV);
    }

    /**
     * Draws part of a framebuffer onto part of the window.
     *
     * <p>The texture coordinates are fractions of the picture, not of the texture: on hardware
     * that insists on powers of two the picture occupies only a corner of its texture, and every
     * caller would otherwise have to know that.
     */
    private static void blit(VRFramebuffer src, float x0, float y0, float x1, float y1,
            float u0, float v0, float u1, float v1) {
        float uScale = (float) src.framebufferWidth / src.framebufferTextureWidth;
        float vScale = (float) src.framebufferHeight / src.framebufferTextureHeight;
        float tu0 = u0 * uScale;
        float tu1 = u1 * uScale;
        float tv0 = v0 * vScale;
        float tv1 = v1 * vScale;

        src.bindFramebufferTexture();
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorOpaque_I(-1);
        // y1 is the bottom of the rectangle on screen, and a texture starts at its bottom
        t.addVertexWithUV(x0, y1, 0.0D, tu0, tv0);
        t.addVertexWithUV(x1, y1, 0.0D, tu1, tv0);
        t.addVertexWithUV(x1, y0, 0.0D, tu1, tv1);
        t.addVertexWithUV(x0, y0, 0.0D, tu0, tv1);
        t.draw();
        src.unbindFramebufferTexture();
    }

    /**
     * Sets the window up to be drawn into flat, remembering everything it changes.
     *
     * <p>Remembering matters: this runs in the middle of the stereo frame, between the two eye
     * passes. Before it pushed and popped, it worked only because the next eye happened to set
     * up its own camera before drawing anything.
     */
    private static void beginBlit(Framebuffer window, int width, int height) {
        window.bindFramebuffer(true);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_VIEWPORT_BIT | GL11.GL_CURRENT_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        GL11.glViewport(0, 0, width, height);
        GL11.glColorMask(true, true, true, false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
    }

    private static void endBlit(Framebuffer window) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
        window.unbindFramebuffer();
    }

    private static void drawEye(RenderPass pass, VRFramebuffer eye, IMinecraftExt ext,
            Runnable drawOnce) {
        VR.setCurrentPass(pass);

        // the scene goes into our own buffer, so the render scale can differ from the eye size
        ext.vivecraftx$setFramebufferMc(sceneBuffer);
        sceneBuffer.bindFramebuffer(true);
        drawOnce.run();

        // The hands and the crosshair are drawn from inside the world render, where vanilla drew
        // the hand -- not here. By this point the interface phase has bound the panel's own
        // framebuffer, so anything drawn here lands on the panel instead of in the world, which
        // looks exactly like a flat copy of itself stuck to a screen.

        // the interface was captured to its own texture during that call; put it back into the
        // eye as a panel standing in the world
        VRGui.compositeInto(sceneBuffer);

        // then into the texture the runtime owns for this eye
        eye.bindFramebuffer(true);
        sceneBuffer.framebufferRender(eyeWidth, eyeHeight);
        eye.unbindFramebuffer();
    }

    /**
     * Gives up on VR for this session.
     *
     * <p>Polling has to stop with it: {@code WaitGetPoses} blocks until the compositor is ready
     * for the next frame, so continuing to poll while no longer submitting hangs the client
     * thread outright.
     */
    private static void fail() {
        broken = true;
        VRState.failed("a frame could not be drawn", null);
    }

    /** Lets a later configuration change rebuild what a failure tore down. */
    public static void clearFailure() {
        broken = false;
        VRRenderState.reinitFramebuffers = true;
    }

    public static void shutdown() {
        release();
        broken = false;
    }
}
