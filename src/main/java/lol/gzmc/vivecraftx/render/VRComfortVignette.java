package lol.gzmc.vivecraftx.render;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * Narrows what you can see while you are being moved.
 *
 * <p>This is the single most effective thing in the mod against motion sickness, and the reason
 * is in the edges: what makes people ill is the wide, fast-moving periphery saying the body is
 * travelling while the inner ear says it is standing still. Covering the periphery while that
 * disagreement is happening removes most of it, and because it only appears while you are
 * actually moving under power, it is almost unnoticeable the rest of the time.
 *
 * <p>Drawn flat across the eye rather than as a shader: a ring of darkening around the edge is
 * what a shader would produce anyway, and this version needs no shader support, which matters
 * where another mod owns the rendering pipeline.
 */
public final class VRComfortVignette {

    private VRComfortVignette() {}

    /** How quickly it closes and opens, as a fraction per frame at sixty of them a second. */
    private static final float EASE = 0.08F;

    /** Speed in blocks per tick at which it is fully closed. */
    private static final float FULL_SPEED = 0.14F;

    private static float amount;

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!VRState.running() || mc.thePlayer == null || VR.settings() == null) {
            amount = 0;
            return;
        }
        if (!VRCamera.isEyePass(VR.currentPass())) {
            return;
        }
        if (!VR.settings().useFOVReduction) {
            amount = 0;
            return;
        }

        // Eased, because the cure for a lurch is not another lurch: an edge that snapped shut the
        // instant the stick moved would be its own discomfort.
        float target = travelling(mc.thePlayer);
        amount += (target - amount) * EASE;
        if (amount < 0.01F) {
            return;
        }

        try {
            draw(VR.settings().fovReductionMin, amount);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("The comfort vignette failed to draw", t);
        }
    }

    /**
     * How much of this the player's movement calls for, from nothing to all of it.
     *
     * <p>Measured from how fast they are actually travelling rather than from whether a stick is
     * pushed, so being carried by a boat or a minecart counts -- those make people just as ill --
     * and walking about the room on their own feet does not, because that is real movement their
     * inner ear agrees with.
     */
    private static float travelling(EntityPlayerSP player) {
        if (player.ridingEntity == null && !VR.player().getFreeMoveMode()) {
            return 0F;
        }
        double dx = player.posX - player.prevPosX;
        double dz = player.posZ - player.prevPosZ;
        double speed = Math.sqrt(dx * dx + dz * dz);
        float t = (float) (speed / FULL_SPEED);
        return t > 1F ? 1F : t;
    }

    /**
     * A ring of black closing in from the edge of vision.
     *
     * <p>In clip space with both matrices at identity, which is the whole eye from corner to
     * corner however the headset's lenses are shaped, and costs no camera arithmetic.
     */
    private static void draw(float minimum, float strength) {
        // Everything this turns off, it turns off for the rest of the eye unless it is put
        // back. Lighting is the one that shows: a later item drawn correctly comes out
        // unshaded, and reads as flat. Handing the whole block of state to the driver to
        // remember is both shorter than undoing each piece and proof against an exception
        // part way through, which is how it escaped in the first place.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDepthMask(false);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                // Where the clear circle ends and the darkening begins. Clip space runs from minus
                // one to one, and the corners are further out than that, so a radius of one still
                // leaves the corners covered -- which is the point.
                float clear = 1.6F - (1.6F - minimum) * strength;
                float edge = clear + 0.45F;

                Tessellator tes = Tessellator.instance;
                tes.startDrawingQuads();
                final int sides = 48;
                for (int i = 0; i < sides; i++) {
                    double a0 = Math.PI * 2 * i / sides;
                    double a1 = Math.PI * 2 * (i + 1) / sides;
                    double c0 = Math.cos(a0);
                    double s0 = Math.sin(a0);
                    double c1 = Math.cos(a1);
                    double s1 = Math.sin(a1);
                    tes.setColorRGBA_F(0F, 0F, 0F, 0F);
                    tes.addVertex(c0 * clear, s0 * clear, 0);
                    tes.addVertex(c1 * clear, s1 * clear, 0);
                    tes.setColorRGBA_F(0F, 0F, 0F, 1F);
                    tes.addVertex(c1 * edge, s1 * edge, 0);
                    tes.addVertex(c0 * edge, s0 * edge, 0);
                }
                tes.draw();

                // Beyond the ring, solid: the corners of the eye are further from the middle than
                // any circle drawn above reaches.
                tes.startDrawingQuads();
                tes.setColorRGBA_F(0F, 0F, 0F, 1F);
                for (int i = 0; i < sides; i++) {
                    double a0 = Math.PI * 2 * i / sides;
                    double a1 = Math.PI * 2 * (i + 1) / sides;
                    tes.addVertex(Math.cos(a0) * edge, Math.sin(a0) * edge, 0);
                    tes.addVertex(Math.cos(a1) * edge, Math.sin(a1) * edge, 0);
                    tes.addVertex(Math.cos(a1) * 8, Math.sin(a1) * 8, 0);
                    tes.addVertex(Math.cos(a0) * 8, Math.sin(a0) * 8, 0);
                }
                tes.draw();

                GL11.glDepthMask(true);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1F, 1F, 1F, 1F);
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }
}
