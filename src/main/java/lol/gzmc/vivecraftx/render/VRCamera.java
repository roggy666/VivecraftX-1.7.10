package lol.gzmc.vivecraftx.render;

import java.nio.FloatBuffer;

import de.fruitfly.ovr.structs.Matrix4f;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.api.RenderPass;
import lol.gzmc.vivecraftx.compat.AngelicaRenderCompat;
import lol.gzmc.vivecraftx.util.Vec3Util;

import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

/**
 * The per-eye camera: projection from the headset, view from the head pose.
 *
 * <p>Without this both eyes draw the same image and the headset shows a flat picture with no
 * parallax and no head tracking, which is where the mod stood before.
 *
 * <p>The projection comes from the runtime rather than from the field of view setting, because
 * only the runtime knows the lenses. It is cached per eye and rebuilt when the clip distances
 * change, since asking OpenVR every frame is wasted work.
 */
public final class VRCamera {

    private VRCamera() {}

    public static final float MIN_CLIP = 0.05F;

    private static final FloatBuffer[] EYE_PROJECTION = new FloatBuffer[2];

    /**
     * The storage the two entries above point at when they are valid.
     *
     * <p>Kept apart from them because null in {@code EYE_PROJECTION} is how "no projection yet"
     * is said, and these have to survive that being said. Direct buffers, so allocating a pair
     * per rebuild was a native allocation and a cleaner per rebuild; the matrix is copied into
     * the stack by {@code glLoadMatrix} before anything else can touch it.
     */
    private static final FloatBuffer[] SCRATCH = new FloatBuffer[2];

    private static FloatBuffer scratch(int index) {
        if (SCRATCH[index] == null) {
            SCRATCH[index] = org.lwjgl.BufferUtils.createFloatBuffer(16);
        }
        return SCRATCH[index];
    }
    private static float cachedNear = -1F;
    private static float cachedFar = -1F;

    /**
     * The widest each edge of vision reaches across both eyes, as a slope at unit distance.
     *
     * <p>The two eyes do not see the same shape. A headset lens is off-centre, so each eye's view
     * is wider on its outer side than on its inner one, and the two are mirror images. Deciding
     * which chunks exist from one eye's shape therefore gives a different answer from the other's,
     * and a chunk at the edge of vision can be drawn for one eye and not the other -- which is far
     * more noticeable than a chunk missing from both, because each eye then disagrees with the
     * other about what is there. Taking the outer edge of each pair gives one shape that contains
     * both, so both eyes are told the same story.
     */
    private static float cullLeft = -1F;
    private static float cullRight = 1F;
    private static float cullBottom = -1F;
    private static float cullTop = 1F;

    /** A little beyond what either eye can see, so rounding cannot clip an edge chunk. */
    private static final float CULL_MARGIN = 1.05F;

    /**
     * The vertical field of view the two eyes together cover, in degrees.
     *
     * <p>Worked out once with the projections and then reported on both passes, rather than each
     * eye reporting its own. Two reasons, and the second is the pressing one. A shader pack
     * reading the value as a uniform wants one number for the frame, not two that differ by a
     * fraction of a degree depending on which eye is being drawn. And Angelica re-walks its whole
     * visibility graph whenever the number changes, so a left and a right that disagree -- which
     * they do on any headset whose displays are canted rather than parallel -- buy a second full
     * walk on the second eye pass, every frame, for a difference nobody can see.
     */
    private static float bothEyesFovDegrees = 70.0F;

    /** Remembered so the panel and the crosshair are drawn through the same lens as the scene. */
    private static float lastFarPlane = 128F;
    private static float lastPartialTicks;
    private static int lastUpdateCount;

    /** So a headset that cannot answer is not reported once per eye per frame for ever. */
    private static boolean loggedNoProjection;

    /**
     * The far clip for this frame, worked out once.
     *
     * <p>Asking Angelica for it is not free -- it walks its cloud layers and allocates to do it
     * -- and the answer moves with the player's own altitude, so asking per call did not merely
     * cost the walk: it made the projection cache below miss several times a second and rebuild
     * two matrices from the headset each time. The far clip is a property of the frame, so it is
     * settled once for the frame.
     *
     * <p>Resolved lazily rather than in {@link #beginFrame()}, because it reads the render
     * distance that the first eye's {@code setup} is about to supply.
     */
    private static boolean farStale = true;
    private static float farThisFrame = 256F;

    /** Called once per frame, before either eye. */
    public static void beginFrame() {
        farStale = true;
    }

    private static float farClip() {
        if (farStale) {
            farThisFrame = AngelicaRenderCompat.farPlane(lastFarPlane * 2.0F);
            farStale = false;
        }
        return farThisFrame;
    }

    /** True when this pass is an actual eye, as opposed to the desktop mirror. */
    public static boolean isEyePass(RenderPass pass) {
        return pass == RenderPass.Left || pass == RenderPass.Right;
    }

    /**
     * Replaces vanilla's projection and camera for one eye.
     *
     * @return false when VR cannot supply a camera, so the caller should let vanilla run
     */
    public static boolean setup(float partialTicks, float farPlaneDistance,
            int rendererUpdateCount) {
        RenderPass pass = VR.currentPass();
        if (!VRState.running() || !isEyePass(pass) || VR.stereo() == null
                || VR.roomScale() == null) {
            return false;
        }
        lastFarPlane = farPlaneDistance;
        lastPartialTicks = partialTicks;
        lastUpdateCount = rendererUpdateCount;

        Minecraft mc = Minecraft.getMinecraft();

        FloatBuffer projection = currentProjection(pass);
        if (projection == null) {
            return false;
        }

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        projection.rewind();
        GL11.glMultMatrix(projection);

        AngelicaRenderCompat.reportFov(bothEyesFovDegrees);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        applyPortalWobble(mc, partialTicks, rendererUpdateCount);

        // orientation straight from the head pose, rather than the entity's yaw and pitch
        FloatBuffer headMatrix = VR.roomScale().getHMDMatrix_World();
        if (headMatrix != null) {
            headMatrix.rewind();
            GL11.glMultMatrix(headMatrix);
        }

        // Then move the world so the eye sits where the headset says it does.
        //
        // The player position has to be the interpolated one, because that is the point every
        // renderer in the game subtracts from its own coordinates. Taking the entity's exact
        // position instead leaves the camera up to one tick of movement away from where the world
        // around it was drawn, which reads as the world juddering while you walk.
        Vec3 eye = VRRenderState.getEyeRenderPos(pass);
        if (eye != null) {
            Vec3 player = VRRenderState.interpolatedPlayerPos;
            GL11.glTranslatef((float) (player.xCoord - eye.xCoord),
                    (float) (player.yCoord - eye.yCoord),
                    (float) (player.zCoord - eye.zCoord));
        }
        return true;
    }

    /** Kept because the nether portal distortion is a gameplay cue, not decoration. */
    private static void applyPortalWobble(Minecraft mc, float partialTicks,
            int rendererUpdateCount) {
        if (mc.thePlayer == null) {
            return;
        }
        float portal = mc.thePlayer.prevTimeInPortal
                + (mc.thePlayer.timeInPortal - mc.thePlayer.prevTimeInPortal) * partialTicks;
        if (portal <= 0.0F) {
            return;
        }
        byte speed = mc.thePlayer.isPotionActive(Potion.confusion) ? (byte) 7 : (byte) 20;
        float f = 5.0F / (portal * portal + 5.0F) - portal * 0.04F;
        f *= f;
        float angle = (rendererUpdateCount + partialTicks) * speed;
        GL11.glRotatef(angle, 0.0F, 1.0F, 1.0F);
        GL11.glScalef(1.0F / f, 1.0F, 1.0F);
        GL11.glRotatef(-angle, 0.0F, 1.0F, 1.0F);
    }

    /**
     * Loads the shape that contains both eyes' views, for deciding what is visible at all.
     *
     * <p>Only for that decision. Drawing still uses each eye's own projection, because that is
     * what makes the two images a stereo pair.
     */
    public static void applyCullingProjection() {
        float far = farClip();
        // Asked for in case nothing has yet, which is what fills in the four edges below.
        if (projectionFor(RenderPass.Left, far) == null) {
            return;
        }
        GL11.glLoadIdentity();
        GL11.glFrustum(cullLeft * MIN_CLIP, cullRight * MIN_CLIP, cullBottom * MIN_CLIP,
                cullTop * MIN_CLIP, MIN_CLIP, far);
    }

    private static FloatBuffer projectionFor(RenderPass pass, float farClip) {
        int index = pass == RenderPass.Right ? 1 : 0;
        if (EYE_PROJECTION[index] == null || cachedNear != MIN_CLIP || cachedFar != farClip) {
            try {
                float left = 0F;
                float right = 0F;
                float bottom = 0F;
                float top = 0F;
                for (int i = 0; i < 2; i++) {
                    Matrix4f m = VR.stereo().getProjectionMatrix(null, i, MIN_CLIP, farClip);
                    // The four edges this eye reaches, read back out of the projection: the first
                    // row carries the horizontal span and where it is centred, the second the
                    // vertical.
                    float l = (m.M[0][2] - 1F) / m.M[0][0];
                    float r = (m.M[0][2] + 1F) / m.M[0][0];
                    float b = (m.M[1][2] - 1F) / m.M[1][1];
                    float t = (m.M[1][2] + 1F) / m.M[1][1];
                    left = i == 0 ? l : Math.min(left, l);
                    right = i == 0 ? r : Math.max(right, r);
                    bottom = i == 0 ? b : Math.min(bottom, b);
                    top = i == 0 ? t : Math.max(top, t);
                    EYE_PROJECTION[i] = m.transposed().intoFloatBuffer(scratch(i));
                }
                cullLeft = left * CULL_MARGIN;
                cullRight = right * CULL_MARGIN;
                cullBottom = bottom * CULL_MARGIN;
                cullTop = top * CULL_MARGIN;
                // From the combined edges rather than the margined ones: the margin exists to
                // keep rounding from clipping an edge chunk, and reporting an inflated angle to a
                // shader pack would be a lie told for no reason.
                bothEyesFovDegrees = top > bottom
                        ? (float) Math.toDegrees(Math.atan(top) - Math.atan(bottom))
                        : 70.0F;
                cachedNear = MIN_CLIP;
                cachedFar = farClip;
            } catch (Throwable t) {
                if (!loggedNoProjection) {
                    ViveCraftX.LOG.error("Headset gave no projection matrix, eye render disabled",
                            t);
                    loggedNoProjection = true;
                }
                EYE_PROJECTION[0] = null;
                EYE_PROJECTION[1] = null;
                bothEyesFovDegrees = 70.0F;
                return null;
            }
            loggedNoProjection = false;
        }
        return EYE_PROJECTION[index];
    }

    /**
     * The projection for the pass being drawn, with the far clip Angelica would have asked for.
     *
     * <p>Angelica's NotFine raises that clip so distant clouds are not sliced off. We took over
     * the method it did that in, so we owe it the same adjustment.
     */
    private static FloatBuffer currentProjection(RenderPass pass) {
        return projectionFor(pass, farClip());
    }

    /**
     * Multiplies the current eye's projection into the matrix already selected, for drawing
     * something into the eye after the interface pass replaced the matrices with flat ones.
     *
     * <p>Only the projection. The interface panel works out its own view matrix, because it is
     * placed relative to the head and the play area rather than to the player entity.
     */
    /**
     * Re-establishes the whole eye camera, projection and view, from the values the scene used.
     *
     * <p>For drawing something into the world after the matrices have been changed -- the
     * crosshair, which is placed in the world but drawn once the world is finished.
     *
     * @return false when there is no eye camera to re-establish
     */
    public static boolean reapplyEyeCamera() {
        return setup(lastPartialTicks, lastFarPlane, lastUpdateCount);
    }

    public static void applyEyeProjection() {
        FloatBuffer projection = currentProjection(VR.currentPass());
        if (projection == null) {
            return;
        }
        projection.rewind();
        GL11.glMultMatrix(projection);
    }

    /** Drops the cached projections, so a clip distance change is picked up. */
    public static void invalidate() {
        EYE_PROJECTION[0] = null;
        EYE_PROJECTION[1] = null;
        cachedNear = -1F;
        cachedFar = -1F;
    }
}
