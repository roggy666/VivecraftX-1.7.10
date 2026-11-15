package lol.gzmc.vivecraftx.render;

import com.mtbs3d.minecrift.gameplay.VRMovementStyle;
import com.mtbs3d.minecrift.provider.OpenVRPlayer;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.api.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

/**
 * The beam that shows where a teleport will put you.
 *
 * <p>Aiming a teleport blind is guesswork: the arc is thrown from the hand and bends under its
 * own gravity, so without seeing it you cannot tell whether you are about to land on the ledge or
 * short of it. The colour says whether the spot is somewhere you can stand, and the rings on the
 * ground say exactly where.
 *
 * <p>Drawn where the hands and the crosshair are drawn -- inside the world pass, through the eye's
 * own camera -- because it belongs in the world and has to be occluded by the world like anything
 * else at that distance.
 */
public final class VRTeleportArc {

    private VRTeleportArc() {}

    /** How much of the beam's own length each drawn segment covers. */
    private static final int SEGMENTS = 48;

    /** Pale violet where the landing is good, grey where it is not. */
    private static final float[] GOOD = { 205F / 255F, 169F / 255F, 205F / 255F };
    private static final float[] BAD = { 83F / 255F, 75F / 255F, 83F / 255F };

    /** How fast the texture runs along the beam, which is what makes it read as pointing. */
    private static double scroll;
    private static long scrollAt;

    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!VRState.running() || mc.theWorld == null || mc.thePlayer == null
                || VR.player() == null || VR.settings() == null) {
            return;
        }
        if (!VRCamera.isEyePass(VR.currentPass())) {
            return;
        }
        OpenVRPlayer player = VR.player();
        VRMovementStyle style = player.vrMovementStyle;
        if (player.getFreeMoveMode() || !style.showBeam || !style.arcAiming) {
            return;
        }
        if (player.movementTeleportProgress <= 0.0 || player.movementTeleportArcSteps <= 1) {
            return;
        }

        Vec3 destination = player.movementTeleportDestination;
        boolean valid = destination.xCoord != 0 || destination.yCoord != 0
                || destination.zCoord != 0;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            if (!VRCamera.reapplyEyeCamera()) {
                return;
            }
            draw(mc, player, style, valid, destination);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("The teleport arc failed to draw", t);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    private static void draw(Minecraft mc, OpenVRPlayer player, VRMovementStyle style,
            boolean valid, Vec3 destination) {
        // Everything this turns off, it turns off for the rest of the eye unless it is put
        // back. Lighting is the one that shows: a later item drawn correctly comes out
        // unshaded, and reads as flat. Handing the whole block of state to the driver to
        // remember is both shorter than undoing each piece and proof against an exception
        // part way through, which is how it escaped in the first place.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        try {
            Vec3 origin = VRRenderState.interpolatedPlayerPos;
            Vec3 eye = VRRenderState.getEyeRenderPos(VR.currentPass());
            if (origin == null || eye == null) {
                return;
            }

            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);

            float[] colour = valid ? GOOD : BAD;
            float alpha = valid ? 0.86F : 0.5F;

            // Only moves while the landing is good, so a beam that has stopped being useful also
            // stops drawing the eye.
            long now = System.currentTimeMillis();
            if (valid) {
                if (scrollAt != 0) {
                    scroll += (now - scrollAt) / 1000.0D * style.textureScrollSpeed * 0.6D;
                }
            }
            scrollAt = now;

            mc.getTextureManager().bindTexture(style.texture);

            int segments = SEGMENTS;
            if (style.beamGrow) {
                segments = (int) (segments * player.movementTeleportProgress);
            }
            if (segments < 2) {
                segments = 2;
            }
            float halfWidth = style.beamHalfWidth * 0.15F;

            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            tes.setColorRGBA_F(colour[0], colour[1], colour[2], alpha);

            for (int i = 0; i < segments; i++) {
                float from = (float) i / segments;
                float to = (float) (i + 1) / segments;
                Vec3 a = player.getInterpolatedArcPosition(from);
                Vec3 b = player.getInterpolatedArcPosition(to);
                if (a == null || b == null) {
                    continue;
                }
                ribbon(tes, a, b, origin, eye, halfWidth,
                        (float) (from * 4 - scroll), (float) (to * 4 - scroll));
            }
            tes.draw();

            if (valid) {
                rings(tes, destination, origin, colour);
            }

            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * One length of beam, turned to face the eye.
     *
     * <p>A flat strip rather than a tube: a tube costs four times the triangles to look the same
     * from the one direction anybody sees it from, which is along it.
     */
    private static void ribbon(Tessellator tes, Vec3 a, Vec3 b, Vec3 origin, Vec3 eye,
            float halfWidth, float vFrom, float vTo) {
        double dx = b.xCoord - a.xCoord;
        double dy = b.yCoord - a.yCoord;
        double dz = b.zCoord - a.zCoord;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-5D) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;

        double ex = a.xCoord - eye.xCoord;
        double ey = a.yCoord - eye.yCoord;
        double ez = a.zCoord - eye.zCoord;

        // Across the beam and across the line of sight, which is the direction a flat strip has
        // to be widened in to keep its width from the viewer's side.
        double sx = dy * ez - dz * ey;
        double sy = dz * ex - dx * ez;
        double sz = dx * ey - dy * ex;
        double side = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (side < 1.0E-5D) {
            return;
        }
        sx = sx / side * halfWidth;
        sy = sy / side * halfWidth;
        sz = sz / side * halfWidth;

        double ax = a.xCoord - origin.xCoord;
        double ay = a.yCoord - origin.yCoord;
        double az = a.zCoord - origin.zCoord;
        double bx = b.xCoord - origin.xCoord;
        double by = b.yCoord - origin.yCoord;
        double bz = b.zCoord - origin.zCoord;

        tes.addVertexWithUV(ax - sx, ay - sy, az - sz, 0, vFrom);
        tes.addVertexWithUV(bx - sx, by - sy, bz - sz, 0, vTo);
        tes.addVertexWithUV(bx + sx, by + sy, bz + sz, 1, vTo);
        tes.addVertexWithUV(ax + sx, ay + sy, az + sz, 1, vFrom);
    }

    /** Three rings flat on the landing spot, so its size and centre are both readable. */
    private static void rings(Tessellator tes, Vec3 destination, Vec3 origin, float[] colour) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        double x = destination.xCoord - origin.xCoord;
        double y = destination.yCoord - origin.yCoord + 0.02D;
        double z = destination.zCoord - origin.zCoord;

        for (int ring = 0; ring < 3; ring++) {
            double radius = 0.2D - ring * 0.05D;
            tes.startDrawingQuads();
            tes.setColorRGBA_F(colour[0], colour[1], colour[2], 0.25F);
            final int sides = 32;
            for (int i = 0; i < sides; i++) {
                double a0 = Math.PI * 2 * i / sides;
                double a1 = Math.PI * 2 * (i + 1) / sides;
                double inner = radius - 0.015D;
                tes.addVertex(x + Math.cos(a0) * inner, y, z + Math.sin(a0) * inner);
                tes.addVertex(x + Math.cos(a1) * inner, y, z + Math.sin(a1) * inner);
                tes.addVertex(x + Math.cos(a1) * radius, y, z + Math.sin(a1) * radius);
                tes.addVertex(x + Math.cos(a0) * radius, y, z + Math.sin(a0) * radius);
            }
            tes.draw();
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
