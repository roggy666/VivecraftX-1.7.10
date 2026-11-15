package lol.gzmc.vivecraftx.render;

import com.mtbs3d.minecrift.provider.MCOpenVR;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.OpenGlHelper;

/**
 * The pointer on the interface panel.
 *
 * <p>Flat, the operating system draws the mouse cursor and Minecraft never has to. In a headset
 * there is no operating system cursor on the panel, so without this the controller ray moves an
 * invisible point and the player has to guess where they are aiming from which button happens to
 * light up.
 *
 * <p>Deliberately not a crosshair of our own invention: it is the vanilla crosshair icon, as
 * Vivecraft drew it, scaled by the player's own setting.
 */
public final class VRCursor {

    private VRCursor() {}

    /** The vanilla crosshair, at the top left of the icon sheet. */
    private static final ResourceLocation ICONS = Gui.icons;

    private static final int SIZE = 15;

    /** One over the 256-pixel icon sheet, which is how the sheet's coordinates are normalised. */
    private static final float TEXEL = 0.00390625F;

    /**
     * Draws the pointer where the controller is aiming.
     *
     * <p>Expects to be called with the interface's own orthographic projection still in place,
     * while the panel's framebuffer is bound -- that is, from the end of drawing a screen.
     */
    public static void draw() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null || !VRState.running() || VR.settings() == null) {
            return;
        }
        // Only once the ray has actually landed on the panel. Until then there is nothing
        // meaningful to point at, and a pointer parked in a corner reads as a stuck cursor.
        if (!MCOpenVR.controllerMouseValid) {
            return;
        }

        int x = Mouse.getX() * mc.currentScreen.width / Math.max(1, mc.displayWidth);
        int y = mc.currentScreen.height
                - Mouse.getY() * mc.currentScreen.height / Math.max(1, mc.displayHeight) - 1;

        GL11.glEnable(GL11.GL_BLEND);
        // Inverting blend, the same one vanilla uses for the in-world crosshair: the pointer has
        // to stay legible over both a bright inventory and a dark one, which a fixed colour
        // cannot do.
        OpenGlHelper.glBlendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, 1, 0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        // The panel is composited by alpha test, so anything that writes alpha here would punch a
        // hole in it. The pointer may change the colour it sits on but not that pixel's alpha.
        GL11.glColorMask(true, true, true, false);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        mc.getTextureManager().bindTexture(ICONS);
        float size = 16.0F * VR.settings().menuCrosshairScale;
        drawCentred(x, y, size, size);

        GL11.glColorMask(true, true, true, true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
    }

    /** Vanilla's textured rect, but placed by its centre, since a pointer aims from its middle. */
    private static void drawCentred(int centreX, int centreY, float width, float height) {
        float halfW = width / 2.0F;
        float halfH = height / 2.0F;
        float maxU = SIZE * TEXEL;
        float maxV = SIZE * TEXEL;

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(centreX - halfW, centreY + halfH, 0.0D, 0.0D, maxV);
        t.addVertexWithUV(centreX + halfW, centreY + halfH, 0.0D, maxU, maxV);
        t.addVertexWithUV(centreX + halfW, centreY - halfH, 0.0D, maxU, 0.0D);
        t.addVertexWithUV(centreX - halfW, centreY - halfH, 0.0D, 0.0D, 0.0D);
        t.draw();
    }
}
