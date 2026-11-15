package lol.gzmc.vivecraftx.render;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.api.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

/**
 * A ring on the ground where your body actually is.
 *
 * <p>In room-scale your head and your body are two different things: you can lean a metre out of
 * yourself, and the game is still reaching, mining and being hit from where the body is. Without
 * something to show that, the only way to find out you had leaned too far is to swing at
 * something and miss, or to be hit by an arrow that looked like it went past you.
 *
 * <p>It fades in as you lean away and is invisible when you are standing in yourself, so it
 * appears exactly when it has something to say.
 */
public final class VRBodyMarker {

    private VRBodyMarker() {}

    /** Below this far from the body, nothing is drawn -- you are standing in yourself. */
    private static final double QUIET = 0.25D;

    /** At this far, it is as visible as it gets. */
    private static final double FULL = 0.9D;

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!VRState.running() || mc.thePlayer == null || VR.settings() == null
                || !VR.settings().vrShowBlueCircleBuddy) {
            return;
        }
        if (!VRCamera.isEyePass(VR.currentPass()) || VR.settings().seated) {
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        Vec3 head = VRRenderState.getEyeRenderPos(RenderPass.Center);
        Vec3 origin = VRRenderState.interpolatedPlayerPos;
        if (head == null || origin == null) {
            return;
        }

        double dx = head.xCoord - player.posX;
        double dz = head.zCoord - player.posZ;
        double lean = Math.sqrt(dx * dx + dz * dz);
        if (lean < QUIET) {
            return;
        }
        float strength = (float) Math.min((lean - QUIET) / (FULL - QUIET), 1.0D);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            if (!VRCamera.reapplyEyeCamera()) {
                return;
            }
            draw(player, origin, strength);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("The body marker failed to draw", t);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    private static void draw(EntityPlayerSP player, Vec3 origin, float strength) {
        // Everything this turns off, it turns off for the rest of the eye unless it is put
        // back. Lighting is the one that shows: a later item drawn correctly comes out
        // unshaded, and reads as flat. Handing the whole block of state to the driver to
        // remember is both shorter than undoing each piece and proof against an exception
        // part way through, which is how it escaped in the first place.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);

            // Just above the feet, so it lies on whatever the player is standing on rather than
            // inside it.
            double x = player.posX - origin.xCoord;
            double y = player.boundingBox.minY - origin.yCoord + 0.015D;
            double z = player.posZ - origin.zCoord;

            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            tes.setColorRGBA_F(0.35F, 0.6F, 1.0F, 0.55F * strength);
            final int sides = 40;
            final double outer = 0.32D;
            final double inner = 0.27D;
            for (int i = 0; i < sides; i++) {
                double a0 = Math.PI * 2 * i / sides;
                double a1 = Math.PI * 2 * (i + 1) / sides;
                tes.addVertex(x + Math.cos(a0) * inner, y, z + Math.sin(a0) * inner);
                tes.addVertex(x + Math.cos(a1) * inner, y, z + Math.sin(a1) * inner);
                tes.addVertex(x + Math.cos(a1) * outer, y, z + Math.sin(a1) * outer);
                tes.addVertex(x + Math.cos(a0) * outer, y, z + Math.sin(a0) * outer);
            }
            tes.draw();

            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
