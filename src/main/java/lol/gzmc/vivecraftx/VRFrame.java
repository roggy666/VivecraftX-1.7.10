package lol.gzmc.vivecraftx;

import java.util.LinkedList;

import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.provider.OpenVRPlayer;

import lol.gzmc.vivecraftx.compat.AngelicaRenderCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;

/**
 * The once-per-frame VR work: ask the runtime for fresh poses, and keep the smoothed head
 * samples the comfort options are built on.
 *
 * <p>Vivecraft did this inline in {@code Minecraft.runGameLoop}, between drawing the previous
 * frame and rendering the next. We run it from the head of the same method, which is the same
 * point in a loop and needs no rewrite of the method body.
 *
 * <p>Does nothing at all until {@link VRState} says VR is running, so a machine without a
 * headset pays one boolean per frame.
 */
public final class VRFrame {

    private VRFrame() {}

    /** Submitted to the runtime so it can line its own timing up with ours. */
    private static long frameIndex = 0;

    private static final int HMD_AVG_LENGTH = 90;
    private static final LinkedList<Vec3> HMD_POS_SAMPLES = new LinkedList<Vec3>();
    private static final LinkedList<Float> HMD_YAW_SAMPLES = new LinkedList<Float>();

    private static float hmdYawTotal;
    private static float hmdYawLast;
    private static boolean sampling;

    public static long frameIndex() {
        return frameIndex;
    }

    /** Averaged head positions, for the options that need a stable reference rather than a pose. */
    public static LinkedList<Vec3> hmdPosSamples() {
        return HMD_POS_SAMPLES;
    }

    /**
     * The same window for yaw. It only collects while the running total and the average disagree,
     * so it can legitimately be empty; callers average it only when it is not.
     */
    public static LinkedList<Float> hmdYawSamples() {
        return HMD_YAW_SAMPLES;
    }

    /** Continuous yaw, unwrapped past 360 degrees, so turning does not jump. */
    public static float hmdYawTotal() {
        return hmdYawTotal;
    }

    /**
     * Works out where the player and the play area are at this exact point between ticks.
     *
     * <p>Everything the VR camera does is relative to the play area's origin in the world, so if
     * this is not run the origin stays at the world origin: the eye is then reported a couple of
     * metres from (0,0,0) while the player entity is wherever they actually are, and the camera's
     * compensating translation shoves the whole world hundreds of blocks away. Nothing renders but
     * the clouds, which are drawn relative to the camera by their own matrix.
     *
     * <p>Vivecraft did this inside {@code renderWorld}, which runs once per eye. Here it runs once
     * per frame before either eye, which is cheaper and, more to the point, guarantees both eyes
     * are told the same story -- two eyes interpolating separately would disagree by whatever time
     * passed between them.
     */
    public static void onRenderFrame(float partialTicks) {
        if (!VRState.running() || VR.player() == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase view = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (view == null) {
            return;
        }
        OpenVRPlayer player = VR.player();

        Vec3 pos = VRRenderState.interpolatedPlayerPos;
        pos.xCoord = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        pos.yCoord = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        pos.zCoord = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        // Before the origin is settled below, because this is the thing that may move it: a turn
        // taken since the last frame is answered here by swinging the play area about the player's
        // head, and the flag that leaves behind is what tells the interpolation to take the new
        // position whole instead of easing into it. In the other order -- which is how this stood
        // -- the frame that turned the world was drawn with the origin from before the turn, since
        // the renderer takes the new angle immediately but the compensating origin only reached
        // the screen a frame later. For that one frame the world turned about the middle of the
        // play area rather than about the head, and with smooth turning that frame is every frame.
        player.checkandUpdateRotateScale(true, partialTicks);

        Vec3 origin = VRRenderState.interPolatedRoomOrigin;
        if (VRRenderState.irpUpdatedThisFrame) {
            // the origin was already moved for this frame, so interpolating it again would
            // smear a deliberate jump over the next few frames
            origin.xCoord = player.roomOrigin.xCoord;
            origin.yCoord = player.roomOrigin.yCoord;
            origin.zCoord = player.roomOrigin.zCoord;
        } else {
            origin.xCoord = lerp(player.lastroomOrigin.xCoord, player.roomOrigin.xCoord,
                    partialTicks);
            origin.yCoord = lerp(player.lastroomOrigin.yCoord, player.roomOrigin.yCoord,
                    partialTicks);
            origin.zCoord = lerp(player.lastroomOrigin.zCoord, player.roomOrigin.zCoord,
                    partialTicks);
        }

        // The bow is the one tracker driven per frame rather than per tick: drawing it is a
        // continuous gesture, and sampling it at twenty hertz would make the draw feel notched.
        if (mc.thePlayer != null && VR.bowTracker() != null) {
            VR.bowTracker().doProcess(player, mc.thePlayer);
        }

        // Where a teleport would land, for the same reason: the arc is aimed by hand and is
        // being looked at while it is aimed, so it has to be recomputed as often as it is drawn.
        // This was written and never called, which is why teleporting did nothing at all -- the
        // destination stayed at the world origin and every check threw it out.
        if (mc.thePlayer != null && mc.renderViewEntity != null) {
            player.updateTeleportDestinations(mc, mc.renderViewEntity);
        }

        // How far the comfort aperture has closed, once for the frame rather than once per eye,
        // so both eyes are drawn with the same answer.
        lol.gzmc.vivecraftx.render.VRComfortVignette.update();

        // Lets the camera settle its far clip once for the frame instead of on each of the twenty
        // or so times something asks it for a projection.
        lol.gzmc.vivecraftx.render.VRCamera.beginFrame();

        // After the game has placed its own listener on the body, move it onto the head.
        VRListener.update();

        scheduleTerrainUpdateIfViewMoved();
    }

    private static double lerp(double from, double to, float t) {
        return from + (to - from) * t;
    }

    /** Where the head was pointing and standing when the visible chunks were last worked out. */
    private static Vec3 lastCullDir;
    private static Vec3 lastCullPos;

    /**
     * Asks the terrain renderer to recheck which chunks are visible, but only when the view has
     * actually moved.
     *
     * <p>The renderer decides this for itself from the player entity, which a head turn does not
     * move, so it has to be told. Telling it every frame -- which is what both this mod and modern
     * Vivecraft did first -- buys correctness with a full walk of the visibility graph per frame,
     * and that walk is most expensive exactly where it is most noticeable: somewhere high with a
     * long view and a great many sections to consider. Since the answer cannot change unless the
     * view did, the view is what gets checked.
     *
     * <p>The thresholds are deliberately small. A degree of rotation or a quarter of a block of
     * movement cannot bring a chunk into view that was not nearly in view already, and a headset
     * reports tiny movements constantly from the player simply breathing, which is what a bare
     * inequality would mistake for turning.
     */
    private static void scheduleTerrainUpdateIfViewMoved() {
        Vec3 dir = VR.roomScale().getHMDDir_World();
        Vec3 pos = VR.roomScale().getHMDPos_World();
        if (dir == null || pos == null) {
            return;
        }
        boolean moved = lastCullDir == null
                // a degree of turn, as the dot product of two unit vectors
                || lastCullDir.dotProduct(dir) < 0.99985D
                || lastCullPos.squareDistanceTo(pos) > 0.0625D;
        if (!moved) {
            return;
        }
        lastCullDir = dir;
        lastCullPos = pos;
        AngelicaRenderCompat.scheduleTerrainUpdate();
    }

    public static void onFrameStart() {
        if (!VRState.running()) {
            lastFrameNanos = 0L;
            return;
        }
        frameIndex++;
        measureFrameLength();
        try {
            MCOpenVR.poll(frameIndex);
            sampleHead();
        } catch (Throwable t) {
            VRState.failed("the runtime stopped answering", t);
        }
    }

    /**
     * Keeps a rolling window of head yaw and position.
     *
     * <p>The yaw is accumulated rather than stored raw, so a turn past north does not read as a
     * 360 degree jerk, and a disagreement of more than a degree between the running total and the
     * window average is taken as drift and corrected. Ported from Vivecraft unchanged, including
     * the hysteresis: sampling starts once the two differ by 20 degrees and stops below one.
     */
    private static void sampleHead() {
        if (VR.roomScale() == null) {
            return;
        }
        if (HMD_POS_SAMPLES.size() == HMD_AVG_LENGTH) {
            HMD_POS_SAMPLES.removeFirst();
        }
        if (HMD_YAW_SAMPLES.size() == HMD_AVG_LENGTH) {
            HMD_YAW_SAMPLES.removeFirst();
        }

        float yaw = VR.roomScale().getHMDYaw_World();
        if (yaw < 0) {
            yaw += 360;
        }
        hmdYawTotal += angleDiff(yaw, hmdYawLast);
        hmdYawLast = yaw;
        if (Math.abs(angleNormalize(hmdYawTotal) - hmdYawLast) > 1 || hmdYawTotal > 100000) {
            hmdYawTotal = hmdYawLast;
            ViveCraftX.LOG.debug("HMD yaw desync or overflow corrected");
        }

        // In play-area space, which is the space the one thing that reads these -- the parked
        // seated menu -- is positioned and pointed at in. World space would make the average
        // jump by the whole play area whenever the player walks or is teleported.
        HMD_POS_SAMPLES.add(VR.roomScale().getHMDPos_Room());

        float yawAvg = 0;
        if (!HMD_YAW_SAMPLES.isEmpty()) {
            for (float f : HMD_YAW_SAMPLES) {
                yawAvg += f;
            }
            yawAvg /= HMD_YAW_SAMPLES.size();
        }
        float drift = Math.abs(hmdYawTotal - yawAvg);
        if (drift > 20) {
            sampling = true;
        }
        if (drift < 1) {
            sampling = false;
        }
        if (sampling) {
            HMD_YAW_SAMPLES.add(hmdYawTotal);
        }
    }

    /** When the previous frame started, for working out how long it took. */
    private static long lastFrameNanos;

    /** Longest frame that is treated as a frame rather than as the game having stalled. */
    private static final float MAX_FRAME_SECONDS = 0.25F;

    /**
     * Records how long the last frame took.
     *
     * <p>Anything that moves smoothly rather than in steps is multiplied by this, so that it
     * moves at the same speed whatever the frame rate -- which at the moment means smooth turning
     * and nothing else. Nothing wrote it before, so the multiplication was by zero and smooth
     * turning simply did not turn; the setting was there and the stick did nothing.
     *
     * <p>A frame longer than a quarter of a second is a stall, not a frame, and is ignored:
     * otherwise loading a chunk would spin the world a quarter turn.
     */
    private static void measureFrameLength() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            float seconds = (now - lastFrameNanos) / 1.0E9F;
            VRRenderState.frameDelta = seconds > MAX_FRAME_SECONDS ? 0F : seconds;
        } else {
            VRRenderState.frameDelta = 0F;
        }
        lastFrameNanos = now;
    }

    /** Clears per-session state, so a second headset session does not inherit the first's drift. */
    public static void reset() {
        frameIndex = 0;
        lastFrameNanos = 0L;
        VRRenderState.frameDelta = 0f;
        lastCullDir = null;
        lastCullPos = null;
        HMD_POS_SAMPLES.clear();
        HMD_YAW_SAMPLES.clear();
        hmdYawTotal = 0f;
        hmdYawLast = 0f;
        sampling = false;
    }

    private static float angleNormalize(float angle) {
        angle %= 360;
        if (angle < 0) {
            angle += 360;
        }
        return angle;
    }

    private static float angleDiff(float a, float b) {
        float d = Math.abs(a - b) % 360;
        float r = d > 180 ? 360 - d : d;
        int sign = (a - b >= 0 && a - b <= 180) || (a - b <= -180 && a - b >= -360) ? 1 : -1;
        return r * sign;
    }
}
