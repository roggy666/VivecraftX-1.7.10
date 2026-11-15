package lol.gzmc.vivecraftx.render;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

/**
 * An arm drawn between two points in the world, with an elbow in the middle.
 *
 * <p>For the case where there is no body to hang an arm on. A forearm sitting at the controller
 * and pointing wherever the controller points is a block floating in the air: there is nothing
 * behind it and nothing it comes from. An arm that starts where a shoulder would be and ends at
 * the hand reads as an arm even though the shoulder itself is never drawn.
 *
 * <p>Built the long way round on purpose. The direction is turned into a rotation by writing out
 * the three axes and handing the matrix over whole, rather than by working out a yaw and a pitch
 * -- because a yaw and a pitch have four sign conventions between them, and the two previous
 * attempts at this each got one of them wrong in a way that looked almost right.
 */
public final class VRFreeArm {

    /** Where the arm's texture sits, and how tall a whole arm is there. */
    private static final int ARM_U = 40;
    private static final int ARM_V = 16;
    private static final int ARM_HEIGHT = 12;

    /** The pieces, in model units, drawn longer than their bones so they overlap at the elbow. */
    private static final int UPPER_DRAWN = 8;
    private static final int LOWER_DRAWN = 7;

    /** Model units to blocks. */
    private static final float UNIT = 0.0625F;

    /** How thick the arm is drawn, as a fraction of the model's own four pixels. */
    private static final float THICKNESS = 0.4F;

    /** How long the whole arm is, shoulder to hand, in blocks. */
    public static final double ARM_REACH = 0.62D;

    private final ModelRenderer upper;
    private final ModelRenderer lower;

    public VRFreeArm(ModelBase model, boolean left) {
        this.upper = piece(model, left, ARM_V, UPPER_DRAWN, 0F);
        this.lower = piece(model, left, ARM_V + ARM_HEIGHT - LOWER_DRAWN, LOWER_DRAWN, -0.05F);
    }

    /**
     * One piece, with its origin at the end it hangs from.
     *
     * <p>The model's own arm is built two pixels above its pivot, which is right for a shoulder
     * joint and wrong for a piece we are going to place by both ends. This one starts where it
     * is put.
     */
    private static ModelRenderer piece(ModelBase model, boolean left, int v, int length,
            float shrink) {
        ModelRenderer part = new ModelRenderer(model, ARM_U, v);
        part.mirror = left;
        part.addBox(-2.0F, 0.0F, -2.0F, 4, length, 4, shrink);
        part.setRotationPoint(0F, 0F, 0F);
        return part;
    }

    /**
     * Draws the arm from the shoulder to the hand, bending at the elbow.
     *
     * <p>The elbow is put halfway along and pushed sideways by however much slack the two bones
     * have, in whatever direction is both downward and square to the arm. That is where an elbow
     * goes, and it means a drawn-in hand bends the arm rather than shortening it.
     *
     * <p>Coordinates are relative to {@code origin}, which is what the eye's own matrix is
     * relative to.
     */
    public void draw(Vec3 shoulder, Vec3 hand, Vec3 origin, float worldScale) {
        double dx = hand.xCoord - shoulder.xCoord;
        double dy = hand.yCoord - shoulder.yCoord;
        double dz = hand.zCoord - shoulder.zCoord;
        double span = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (span < 1.0E-4D) {
            return;
        }

        double reach = ARM_REACH * worldScale;
        double ex = shoulder.xCoord + dx * 0.5D;
        double ey = shoulder.yCoord + dy * 0.5D;
        double ez = shoulder.zCoord + dz * 0.5D;
        if (span < reach) {
            double slack = Math.sqrt((reach * reach - span * span) * 0.25D);
            // The part of straight down that is square to the arm, so the elbow never tries to
            // bend along the arm's own length.
            double dot = -dy / span;
            double hx = -dot * dx / span;
            double hy = -1D - dot * dy / span;
            double hz = -dot * dz / span;
            double len = Math.sqrt(hx * hx + hy * hy + hz * hz);
            if (len > 1.0E-4D) {
                ex += hx / len * slack;
                ey += hy / len * slack;
                ez += hz / len * slack;
            }
        }

        Vec3 elbow = Vec3.createVectorHelper(ex, ey, ez);
        drawPiece(this.upper, shoulder, elbow, origin, UPPER_DRAWN, worldScale);
        drawPiece(this.lower, elbow, hand, origin, LOWER_DRAWN, worldScale);
    }

    private static void drawPiece(ModelRenderer part, Vec3 from, Vec3 to, Vec3 origin,
            int drawnLength, float worldScale) {
        double dx = to.xCoord - from.xCoord;
        double dy = to.yCoord - from.yCoord;
        double dz = to.zCoord - from.zCoord;
        double span = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (span < 1.0E-5D) {
            return;
        }
        // The piece's own long axis, and any two directions square to it. Which two does not
        // matter: the arm is a square tube and has no front.
        double yx = dx / span;
        double yy = dy / span;
        double yz = dz / span;
        double rx = Math.abs(yy) < 0.99D ? 0D : 1D;
        double ry = Math.abs(yy) < 0.99D ? 1D : 0D;
        double rz = 0D;
        double xx = ry * yz - rz * yy;
        double xy = rz * yx - rx * yz;
        double xz = rx * yy - ry * yx;
        double xl = Math.sqrt(xx * xx + xy * xy + xz * xz);
        xx /= xl;
        xy /= xl;
        xz /= xl;
        double zx = xy * yz - xz * yy;
        double zy = xz * yx - xx * yz;
        double zz = xx * yy - xy * yx;

        java.nio.FloatBuffer basis = org.lwjgl.BufferUtils.createFloatBuffer(16);
        basis.put(new float[] {
                (float) xx, (float) xy, (float) xz, 0F,
                (float) yx, (float) yy, (float) yz, 0F,
                (float) zx, (float) zy, (float) zz, 0F,
                0F, 0F, 0F, 1F});
        basis.rewind();

        GL11.glPushMatrix();
        GL11.glTranslated(from.xCoord - origin.xCoord, from.yCoord - origin.yCoord,
                from.zCoord - origin.zCoord);
        GL11.glMultMatrix(basis);
        // Thick as an arm, and exactly as long as the gap it has to cover.
        GL11.glScalef(THICKNESS * worldScale,
                (float) (span / (drawnLength * UNIT)),
                THICKNESS * worldScale);
        part.rotateAngleX = 0F;
        part.rotateAngleY = 0F;
        part.rotateAngleZ = 0F;
        part.render(UNIT);
        GL11.glPopMatrix();
    }
}
