package lol.gzmc.vivecraftx.render;

import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.VRSettings;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRAim;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.api.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * The crosshair, standing in the world at the point being aimed at.
 *
 * <p>Flat, a crosshair in the middle of the screen is where you are aiming by definition. In VR it
 * is not: you aim with a hand, and the screen has no middle. Drawn on the interface panel it would
 * also drift with the panel, which is what makes it useless -- it would show where the panel is,
 * not where the hand points.
 *
 * <p>So it goes where modern Vivecraft puts it: at the point the aim ray ended, facing the player,
 * nudged a hair toward the eye so it does not fight the surface it is sitting on for depth.
 */
public final class VRWorldCrosshair {

    private VRWorldCrosshair() {}

    /** How far toward the eye to pull the crosshair off the surface, in metres. */
    private static final float SLIGHTLY_CLOSER = 0.001F;

    private static final int SIZE = 15;
    private static final float TEXEL = 0.00390625F;

    /**
     * Whether the crosshair should be drawn at all.
     *
     * <p>Hidden while teleporting, because the arc already shows where you are going and a second
     * marker only adds confusion, and while a bow is drawn, because the arrow's own aim replaces
     * it.
     */
    private static boolean shouldRender(Minecraft mc) {
        if (!VRState.running() || VR.settings() == null || VRAim.crossVec == null) {
            return false;
        }
        if (mc.currentScreen != null || mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        if (mc.gameSettings.thirdPersonView != 0 || MCOpenVR.keyboardShowing) {
            return false;
        }
        if (VR.player() != null && VR.player().movementTeleportProgress > 0.0D) {
            return false;
        }
        if (VR.bowTracker() != null && VR.bowTracker().isDrawing) {
            return false;
        }
        VRSettings settings = VR.settings();
        if (settings.renderInGameCrosshairMode == VRSettings.RENDER_CROSSHAIR_MODE_NEVER) {
            return false;
        }
        if (settings.renderInGameCrosshairMode == VRSettings.RENDER_CROSSHAIR_MODE_HUD
                && settings.hideGui) {
            return false;
        }
        return true;
    }

    /**
     * Draws it into the eye being rendered.
     *
     * <p>Called with the eye's own camera still in place, so the crosshair is seen through the same
     * lens as the world and sits at a believable distance rather than painted on.
     */
    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!shouldRender(mc)) {
            return;
        }
        RenderPass pass = VR.currentPass();
        if (!VRCamera.isEyePass(pass)) {
            return;
        }

        Vec3 eye = VRRenderState.getEyeRenderPos(pass);
        Vec3 cross = VRAim.crossVec;
        if (eye == null) {
            return;
        }
        // Everything in the world is drawn relative to the interpolated player position, so the
        // crosshair has to be too, or it would sit a fraction of a block off whatever it marks.
        Vec3 origin = VRRenderState.interpolatedPlayerPos;

        // The offset is along the line from the aim point back toward the eye, so the crosshair
        // lifts off whatever it is sitting on no matter which way that surface faces.
        double dx = eye.xCoord - cross.xCoord;
        double dy = eye.yCoord - cross.yCoord;
        double dz = eye.zCoord - cross.zCoord;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5D) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        float depth = (float) len;
        if (depth > VRAim.MAX_CROSSHAIR_DISTANCE) {
            depth = VRAim.MAX_CROSSHAIR_DISTANCE;
        }

        VRSettings settings = VR.settings();
        float scale = (float) (0.15F * settings.crosshairScale
                * Math.sqrt(VR.player().worldScale));
        if (settings.crosshairScalesWithDistance) {
            // Kept the same apparent size by growing it with distance, which is the readable
            // option; without it a crosshair sixty blocks out is a single pixel.
            scale *= 0.3F + 0.2F * depth;
        }

        // Its own camera, pushed and popped, so this does not care what the caller left in the
        // matrices or where in the frame it is called from.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            draw(mc, settings, cross, origin, dx, dy, dz, scale);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("The crosshair failed to draw", t);
        } finally {
            // There are only two levels guaranteed on the projection stack. Leaking one of them
            // twice stops every push and pop in the game working, and the image does not come
            // back from that -- so the restore cannot depend on reaching the end of the method.
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    /** The crosshair itself, with both matrices already saved by the caller. */
    private static void draw(Minecraft mc, VRSettings settings, Vec3 cross, Vec3 origin,
            double dx, double dy, double dz, float scale) {
        if (!VRCamera.reapplyEyeCamera()) {
            return;
        }

        GL11.glTranslated(cross.xCoord + dx * SLIGHTLY_CLOSER - origin.xCoord,
                cross.yCoord + dy * SLIGHTLY_CLOSER - origin.yCoord,
                cross.zCoord + dz * SLIGHTLY_CLOSER - origin.zCoord);
        GL11.glRotatef(-VR.player().getControllerMainYaw_World(), 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-VR.roomScale().getControllerMainPitch_World(), 1.0F, 0.0F, 0.0F);
        GL11.glScalef(scale, scale, scale);

        GL11.glDisable(GL11.GL_LIGHTING);
        if (!settings.useCrosshairOcclusion) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glEnable(GL11.GL_BLEND);
        // Inverting blend, so the crosshair reads against a bright sky and a dark cave alike.
        GL14.glBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR,
                GL11.GL_ONE, GL11.GL_ONE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // Turned forty-five degrees when nothing was hit, which is how you can tell at a glance
        // that you are aiming at open air rather than at something too far away.
        if (mc.objectMouseOver == null) {
            GL11.glRotatef(45, 0, 0, 1);
        }

        // Nearest filtering, so a crosshair blown up to a quarter of a metre across stays the
        // crisp cross it is drawn as rather than a blur. The wrapping mode is deliberately left
        // alone: this sheet also carries the hotbar, the hearts and the armour bar, the setting
        // lives on the texture rather than on this draw, and it would stay changed for the rest
        // of the session.
        mc.getTextureManager().bindTexture(Gui.icons);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(-1, 1, 0, 0, SIZE * TEXEL);
        t.addVertexWithUV(1, 1, 0, SIZE * TEXEL, SIZE * TEXEL);
        t.addVertexWithUV(1, -1, 0, SIZE * TEXEL, 0);
        t.addVertexWithUV(-1, -1, 0, 0, 0);
        t.draw();

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
