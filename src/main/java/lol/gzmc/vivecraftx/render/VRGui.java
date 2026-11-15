package lol.gzmc.vivecraftx.render;

import java.nio.FloatBuffer;
import java.util.LinkedList;

import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.VRSettings;

import de.fruitfly.ovr.structs.Matrix4f;
import de.fruitfly.ovr.structs.Quatf;
import de.fruitfly.ovr.structs.Vector3f;

import jopenvr.OpenVRUtil;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRFrame;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.api.IMinecraftExt;
import lol.gzmc.vivecraftx.api.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Vec3;

import net.minecraftforge.client.GuiIngameForge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The interface, drawn as a panel standing in the world.
 *
 * <p>Drawn flat, the interface lands in a corner of the eye texture, because vanilla sizes it from
 * the window while the eye texture is several times larger. Stretching it across the eye instead
 * would nail it to the face, which reads badly and is the same comfort problem as camera bobbing.
 * So it goes where Vivecraft puts it: onto a quad in the world.
 *
 * <p>The interface renders into its own framebuffer at exactly the window size, so the player's
 * interface scale, text size and mouse mapping all stay as they are flat. Only the final placement
 * differs.
 *
 * <p>Where the panel goes is not decided here. {@code MCOpenVR.onGuiScreenChanged} already works
 * that out when a screen opens -- a chest hangs over the block, the chat sits off to one side, the
 * main menu stands in the middle of the play area -- and for the in-game interface, with no screen
 * open, {@link #applyGuiModelView} follows whichever lock mode the player chose. A second opinion
 * about placement living here would be a second thing to keep in step, so there isn't one.
 */
public final class VRGui {

    private VRGui() {}

    /**
     * The panel's width before {@code guiScale}, in metres.
     *
     * <p>Vivecraft's default interface scale, which it passed as a literal to its quad and never
     * made adjustable. It is a constant here rather than a literal because the controller ray
     * needs the same number to find the same plane; see {@link #rayScale()}.
     */
    public static final float QUAD_WIDTH = 1.5F;

    /**
     * The factor the controller ray folds into its coordinate mapping, which has to invert the
     * panel's size for the plane it intersects to be the plane we drew.
     *
     * <p>Vivecraft had {@code 0.68f} here with a note from its author wondering where the number
     * came from, and a guess underneath that it was one over the interface scale. The guess is
     * right: one over {@link #QUAD_WIDTH} is {@code 0.6667}, and with it the ray reaches exactly
     * the edge of the quad instead of stopping two percent short. Deriving it from the same
     * constant the quad uses is what makes the two provably the same plane rather than two numbers
     * that happen to be close.
     */
    public static float rayScale() {
        return 1.0F / QUAD_WIDTH;
    }

    private static VRFramebuffer buffer;
    private static int bufferWidth;
    private static int bufferHeight;

    /** True between the start of the interface phase and the panel being composited. */
    private static boolean capturing;

    /** What {@code framebufferMc} pointed at before we borrowed it for the interface. */
    private static Framebuffer borrowedFrom;

    /** The interface as it was drawn, for the mirror mode that shows only that. */
    public static VRFramebuffer panelBuffer() {
        return buffer;
    }

    public static boolean hasBuffer() {
        return buffer != null;
    }

    private static boolean active() {
        return VRState.running() && VRRenderer.isReady()
                && VRCamera.isEyePass(VR.currentPass());
    }

    /**
     * Whether there is any interface to draw at all.
     *
     * <p>Vivecraft's own condition. Worth keeping rather than always capturing: with the interface
     * hidden and no screen open there is nothing to put on the panel, and capturing anyway would
     * cost a full-window clear and a textured quad per eye to draw nothing.
     */
    private static boolean willDrawSomething(Minecraft mc) {
        if (mc.currentScreen != null) {
            return true;
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        if (mc.gameSettings.hideGUI || VR.settings() == null || VR.settings().hideGui) {
            return false;
        }
        return mc.thePlayer.getSleepTimer() == 0;
    }

    private static void ensureBuffer(Minecraft mc) {
        int w = Math.max(1, mc.displayWidth);
        int h = Math.max(1, mc.displayHeight);
        if (buffer != null && bufferWidth == w && bufferHeight == h) {
            return;
        }
        if (buffer != null) {
            buffer.deleteFramebuffer();
        }
        // The same size as the window, so ScaledResolution, interface scale and the mouse mapping
        // are all untouched: the interface is drawn exactly as it would be flat.
        buffer = new VRFramebuffer("GUI", w, h, true);
        buffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        bufferWidth = w;
        bufferHeight = h;
        ViveCraftX.LOG.info("VR interface panel: {}x{}", w, h);
    }

    /**
     * Starts the interface phase: everything drawn from here lands on the panel's texture rather
     * than in the eye.
     *
     * <p>{@code framebufferMc} is pointed at the panel's buffer for the duration, which is how
     * Vivecraft did it and is what makes this work with mods that composite the interface
     * themselves. Angelica's interface caching draws into a buffer of its own and then blits the
     * result into whatever {@code Minecraft.getFramebuffer()} returns, so pointing that at the
     * panel is all it takes for a cached interface to land on the panel too.
     */
    public static void beginGuiPhase() {
        if (!active() || capturing) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!willDrawSomething(mc)) {
            return;
        }
        ensureBuffer(mc);
        if (buffer == null) {
            return;
        }
        IMinecraftExt ext = (IMinecraftExt) mc;
        borrowedFrom = ext.vivecraftx$getFramebufferMc();
        ext.vivecraftx$setFramebufferMc(buffer);

        buffer.bindFramebuffer(true);
        // cleared transparent, so everything the interface did not draw lets the world through
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        suppressFlatOverlays();
        capturing = true;
    }

    /**
     * Keeps off the panel the few interface elements that only make sense stuck to a screen.
     *
     * <p>The crosshair is drawn in the world instead, at the point being aimed at; on the panel it
     * would show where the panel is rather than where the hand points. The vignette, the helmet
     * overlay and the portal haze are full-screen tints meant to surround you -- on a panel
     * floating in front of you they become a tinted rectangle hanging in the air, which looks like
     * a bug and hides the world behind it.
     *
     * <p>Set every frame rather than once, because another mod may turn them back on, and because
     * they have to come back when VR is off.
     */
    private static void suppressFlatOverlays() {
        GuiIngameForge.renderCrosshairs = false;
        GuiIngameForge.renderHelmet = false;
        GuiIngameForge.renderPortal = false;
    }

    /** Hands the flat interface back everything {@link #suppressFlatOverlays} took. */
    public static void restoreFlatOverlays() {
        GuiIngameForge.renderCrosshairs = true;
        GuiIngameForge.renderHelmet = true;
        GuiIngameForge.renderPortal = true;
    }

    /**
     * Ends the interface phase and draws the panel into the eye.
     *
     * @param sceneBuffer the eye's scene buffer, which has to be bound again first
     */
    public static void compositeInto(VRFramebuffer sceneBuffer) {
        if (!capturing) {
            return;
        }
        capturing = false;

        Minecraft mc = Minecraft.getMinecraft();
        ((IMinecraftExt) mc).vivecraftx$setFramebufferMc(borrowedFrom);
        borrowedFrom = null;

        sceneBuffer.bindFramebuffer(true);
        if (VR.settings() == null || VR.player() == null || VR.roomScale() == null) {
            return;
        }
        try {
            renderGuiLayer(mc);
        } catch (Throwable t) {
            ViveCraftX.LOG.error("Drawing the VR interface panel failed", t);
        }
    }

    /**
     * Puts the panel, and the menu room behind it when there is no world, into the eye.
     *
     * <p>Ported from Vivecraft's {@code renderGuiLayer}. The projection is the eye's, not an
     * orthographic one: the panel is an object in the world and has to be seen through the same
     * lens as everything else, or it will not sit at a believable distance.
     */
    private static void renderGuiLayer(Minecraft mc) {
        boolean inMenuRoom = mc.theWorld == null || mc.currentScreen instanceof GuiWinGame;

        // Pushed before being overwritten, both of them. Loading identity first and pushing
        // afterwards would save the identity and hand the caller that back instead of what it had.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        VRCamera.applyEyeProjection();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            if (inMenuRoom) {
                applyRoomModelView();
                VRMenuRoom.render();
                GL11.glLoadIdentity();
            }
            applyGuiModelView(mc);
            drawPanel(mc, inMenuRoom);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    /** Puts the modelview into room space, which is what the menu room is built in. */
    private static void applyRoomModelView() {
        RenderPass pass = VR.currentPass();
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 eye = mc.theWorld == null ? VR.roomScale().getEyePos_Room(pass)
                                       : VRRenderState.getEyeRenderPos(pass);

        countHeadRotation();
        GL11.glTranslatef(
                (float) (VRRenderState.interPolatedRoomOrigin.xCoord - eye.xCoord),
                (float) (VRRenderState.interPolatedRoomOrigin.yCoord - eye.yCoord),
                (float) (VRRenderState.interPolatedRoomOrigin.zCoord - eye.zCoord));
    }

    /**
     * Puts the modelview where the panel is, facing the way it faces.
     *
     * <p>Ported from Vivecraft's {@code applyGUIModelView}. Two cases: with a screen open,
     * {@code MCOpenVR.onGuiScreenChanged} already chose a pose when the screen opened and the
     * panel stays there, which is what makes a menu readable instead of swimming with the head.
     * With no screen the in-game interface follows the lock mode, recomputed every frame because
     * it is attached to something that moves.
     *
     * <p>The mixed-reality third-person pass is not handled: that camera belongs with the desktop
     * mirror, which is not ported, and the interface phase only ever runs on an eye pass.
     */
    private static void applyGuiModelView(Minecraft mc) {
        RenderPass pass = VR.currentPass();
        VRSettings settings = VR.settings();

        if (mc.theWorld == null) {
            VR.player().worldRotationRadians = 0;
        }

        Vec3 eye = mc.theWorld == null ? VR.roomScale().getEyePos_Room(pass)
                                       : VRRenderState.getEyeRenderPos(pass);

        Vector3f panelPos = MCOpenVR.guiPos_Room;
        Matrix4f panelRot = MCOpenVR.guiRotationPose;
        Vec3 localOffset = Vec3.createVectorHelper(0, 0, 0);
        boolean inMenuRoom = mc.theWorld == null || mc.currentScreen instanceof GuiWinGame;

        if (panelPos == null) {
            panelPos = new Vector3f();
            panelRot = new Matrix4f();
            if (mc.theWorld != null && (mc.currentScreen == null || !settings.floatInventory)) {
                localOffset = placeHud(mc, settings, pass, panelPos, panelRot);
                panelRot = lastHudRotation;
            }
        } else if (!inMenuRoom) {
            // The stored pose is relative to the play area, which has since moved with the player
            // and may have been turned and resized under them as well. All three have to be
            // undone or a screen opened before a snap turn stays facing the way the player was
            // standing then, rather than the way they are standing now.
            panelPos = roomToWorld(panelPos, settings);
            panelRot = Matrix4f.multiply(
                    Matrix4f.rotationY((float) Math.toRadians(settings.vrWorldRotation)), panelRot);
        }

        if (settings.seated && inMenuRoom) {
            // Seated players have no room to turn round in, so the menu is parked on the average
            // of where they have been looking rather than on the current pose, which would let a
            // glance away drag it out of reach.
            Vector3f parked = parkedMenuPose(settings);
            if (parked != null) {
                // Kept in play-area space, which is what the field means and what the controller
                // ray adds the origin back to when it works out where on the panel you pointed.
                // Storing a world position here moved the cursor by the whole room origin.
                MCOpenVR.guiPos_Room = parked;
                MCOpenVR.guiRotationPose = lastHudRotation;
                panelPos = roomToWorld(parked, settings);
                panelRot = Matrix4f.multiply(
                        Matrix4f.rotationY((float) Math.toRadians(settings.vrWorldRotation)),
                        lastHudRotation);
            }
        }

        countHeadRotation();
        GL11.glTranslatef((float) (panelPos.x - eye.xCoord),
                (float) (panelPos.y - eye.yCoord),
                (float) (panelPos.z - eye.zCoord));
        GL11.glMultMatrix(panelRot.transposed().toFloatBuffer());
        GL11.glTranslatef((float) localOffset.xCoord, (float) localOffset.yCoord,
                (float) localOffset.zCoord);
    }

    /**
     * Undoes the head rotation the eye camera applied, so the panel's own orientation is the only
     * one acting on it.
     */
    private static void countHeadRotation() {
        FloatBuffer head = VR.roomScale().getHMDMatrix_World();
        if (head != null) {
            head.rewind();
            GL11.glMultMatrix(head);
        }
    }

    /**
     * Where {@link #placeHud} and {@link #parkedMenuPose} leave the orientation they worked out.
     *
     * <p>A field because both have to return a position and a rotation, and Java will not return
     * two things. Single threaded and read immediately by the only caller.
     */
    private static Matrix4f lastHudRotation = new Matrix4f();

    /**
     * The panel's placement relative to the controller, as the last frame worked it out.
     *
     * <p>Kept so that touching the hotbar can be tested against where the hotbar actually is. The
     * alternative is a second copy of these figures written out by hand somewhere else, which is
     * how they came to disagree with the panel in the first place: the panel was moved and the
     * figures were not. Here there is only one set, and it is the one the panel was drawn from.
     *
     * <p>Relative to the controller and with the world scale divided out, because that is the
     * space the aim poses are in.
     */
    private static Matrix4f panelLocalRotation;
    private static Vec3 panelLocalOffset;
    private static float panelLocalScale;

    /**
     * Places the in-game interface according to the lock mode, and returns the offset to apply in
     * the panel's own space afterwards.
     */
    private static Vec3 placeHud(Minecraft mc, VRSettings settings, RenderPass pass,
            Vector3f panelPos, Matrix4f panelRot) {
        Vec3 localOffset = Vec3.createVectorHelper(0, 0, 0);
        int hand = settings.vrReverseHands ? -1 : 1;
        float worldScale = VR.player().worldScale;
        Matrix4f worldRot = Matrix4f.rotationY((float) Math.toRadians(settings.vrWorldRotation));
        float scale;

        // Every position below is a render-time one. The tick-time equivalents are anchored to
        // the raw play area origin, which only moves once a tick, while the camera this panel is
        // measured against uses the interpolated one -- so those look right until you move.
        //
        // The rotations come from the aim pose rather than the grip pose. That is deliberate and
        // is what these offsets are calibrated against: the aim pose is the grip composed with
        // the driver's own description of where the controller's tip is, so it is the one frame
        // that means the same thing on every controller model.

        if (settings.seated || settings.vrHudLockMode == VRSettings.HUD_LOCK_HEAD) {
            return placeHeadHud(settings, panelPos, worldScale);
        }

        if (settings.vrHudLockMode == VRSettings.HUD_LOCK_HAND) {
            Matrix4f local = Matrix4f.multiply(
                    OpenVRUtil.rotationXMatrix((float) Math.PI * -0.2F),
                    Matrix4f.rotationY((float) Math.PI * 0.1F * hand));
            Matrix4f rot = Matrix4f.multiply(
                    Matrix4f.multiply(worldRot, MCOpenVR.getAimRotation(1)), local);
            panelLocalRotation = local;
            lastHudRotation = rot;

            // Held above the hand rather than in it, so it does not sit inside whatever the hand
            // is holding.
            localOffset.yCoord = 0.32D * worldScale;
            Vec3 at = VRRenderState.getControllerRenderPos(1);
            panelPos.x = (float) at.xCoord;
            panelPos.y = (float) at.yCoord;
            panelPos.z = (float) at.zCoord;

            // An honest scale, rather than hiding the shrink in the matrix's homogeneous term,
            // which is what the older version did and what made this panel behave unlike the
            // others.
            scale = 1F / 1.7F;
            MCOpenVR.guiScale = scale;
            MCOpenVR.hudPopup = true;
            recordPanelGeometry(localOffset, scale, worldScale);
            return localOffset;
        }

        if (settings.vrHudLockMode == VRSettings.HUD_LOCK_WRIST) {
            // The roll is what lays the panel along the forearm. Without it the panel stands
            // across the arm instead of on it, which reads as floating beside the hand. The
            // quarter turn after it faces the panel out of the back of the wrist, like a watch.
            Matrix4f local = Matrix4f.multiply(
                    OpenVRUtil.rotationZMatrix((float) Math.PI * 0.5F * hand),
                    Matrix4f.rotationY((float) Math.PI * 0.5F * hand));
            Matrix4f rot = Matrix4f.multiply(
                    Matrix4f.multiply(worldRot, MCOpenVR.getAimRotation(1)), local);
            panelLocalRotation = local;
            lastHudRotation = rot;

            Vec3 at = VRRenderState.getControllerRenderPos(1);
            panelPos.x = (float) at.xCoord;
            panelPos.y = (float) at.yCoord;
            panelPos.z = (float) at.zCoord;

            // Onto the back of the wrist. The vertical figure is measured out of the arm model
            // rather than picked: the arm is drawn at a fraction of its own size, and the panel
            // has to rise off its surface by whatever that fraction leaves. Modern Vivecraft has
            // a second pair of figures for slim skins, which this version has no notion of.
            final double armScale = 0.42D;
            localOffset.xCoord = hand * -0.136D * worldScale;
            localOffset.yCoord = (0.055D + 0.1125D * (1.0D - armScale)) * worldScale;
            // How far along the forearm, which is the player's to choose -- an arm is not the
            // same length on everybody and the panel should land where they can read it.
            localOffset.zCoord = (0.12D + 0.02D * settings.vrHudWristOffset) * armScale
                    * worldScale;

            scale = 0.4F;
            MCOpenVR.guiScale = scale;

            // Always readable rather than popping up when looked at. Vivecraft tried a
            // look-at test with hysteresis and commented it out: a panel that appears and
            // disappears as the wrist turns is harder to use than one that is simply there.
            MCOpenVR.hudPopup = true;
            recordPanelGeometry(localOffset, scale, worldScale);
            return localOffset;
        }

        // Any mode this does not know about, including the body-locked one Vivecraft defines but
        // never lets the options screen select. Falling through with the position untouched would
        // leave the panel at the world origin -- buried in the terrain at spawn, or nowhere at
        // all -- so it is treated as head-locked, which is the mode that is always readable.
        return placeHeadHud(settings, panelPos, worldScale);
    }

    /**
     * Keeps the offset and scale the panel was placed with, in the controller's own space.
     *
     * <p>The world scale is divided back out because the aim poses this is later compared against
     * do not carry it.
     */
    private static void recordPanelGeometry(Vec3 localOffset, float scale, float worldScale) {
        panelLocalOffset = Vec3.createVectorHelper(localOffset.xCoord / worldScale,
                localOffset.yCoord / worldScale, localOffset.zCoord / worldScale);
        panelLocalScale = scale;
    }

    /**
     * Where the two ends of the hotbar are, relative to the off-hand controller.
     *
     * <p>For deciding whether the other hand is touching it. Worked out from the panel's own
     * placement and from where the hotbar sits on the interface, rather than from a separate set
     * of figures: the hotbar is a strip of pixels on a quad whose size and position are both
     * known, so its ends can be said exactly, and they stay right if the panel is ever moved.
     *
     * @return the two ends, or null when the panel is not on a hand this frame
     */
    public static Vec3[] hotbarEnds() {
        if (panelLocalRotation == null || panelLocalOffset == null || buffer == null) {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc, bufferWidth, bufferHeight);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }

        // The quad is QUAD_WIDTH across however many interface pixels wide the panel is, and the
        // same number of blocks per pixel vertically, so one factor converts both.
        float perPixel = QUAD_WIDTH / width * panelLocalScale;
        // Vanilla draws the hotbar centred, ninety-one pixels either side of the middle, in a
        // strip twenty-two pixels tall standing on the bottom edge -- so the middle of its row of
        // slots is eleven pixels up. Interface coordinates count down the screen and the panel's
        // count up it, which is where the subtraction comes from.
        float halfBar = 90F * perPixel;
        float up = (height / 2F - (height - 11F)) * perPixel;

        return new Vec3[] {
                hotbarEnd(-halfBar, up),
                hotbarEnd(halfBar, up),
        };
    }

    /** One end of the hotbar strip, turned into the controller's frame. */
    private static Vec3 hotbarEnd(float x, float y) {
        Vector3f local = new Vector3f((float) panelLocalOffset.xCoord + x,
                (float) panelLocalOffset.yCoord + y, (float) panelLocalOffset.zCoord);
        Vector3f turned = panelLocalRotation.transform(local);
        return Vec3.createVectorHelper(turned.x, turned.y, turned.z);
    }

    /**
     * In front of the face, at the distance the player chose.
     *
     * <p>Its own method because it is both a mode in its own right and the fallback for any mode
     * this does not recognise, and a panel in front of your face is always findable.
     */
    private static Vec3 placeHeadHud(VRSettings settings, Vector3f panelPos, float worldScale) {
        Matrix4f worldRot = Matrix4f.rotationY((float) Math.toRadians(settings.vrWorldRotation));
        Matrix4f orientation = Matrix4f.multiply(worldRot, MCOpenVR.hmdRotation);
        Vec3 from = VRRenderState.getEyeRenderPos(RenderPass.Center);
        Vec3 dir = VR.player().getHMDDir_World();

        if (settings.seated && settings.seatedHudAltMode) {
            // Seated with the alternate mode: the interface follows the pointing hand, since a
            // seated player turns their head far less than they turn a controller.
            dir = VR.roomScale().getControllerDir_World(0);
            orientation = Matrix4f.multiply(worldRot, MCOpenVR.getAimRotation(0));
        }

        double distance = worldScale * settings.hudDistance;
        panelPos.x = (float) (from.xCoord + dir.xCoord * distance);
        panelPos.y = (float) (from.yCoord + dir.yCoord * distance);
        panelPos.z = (float) (from.zCoord + dir.zCoord * distance);

        // Through a quaternion and back, which drops any scale the matrix multiply left in it.
        // The hand modes do not do this because they want the scale handled separately.
        lastHudRotation = new Matrix4f(OpenVRUtil.convertMatrix4ftoRotationQuat(orientation));
        MCOpenVR.guiScale = settings.hudScale;
        return Vec3.createVectorHelper(0, 0, 0);
    }

    /**
     * Turns a position in the play area into one in the world.
     *
     * <p>The play area sits somewhere in the world, can be turned under the player by a snap
     * turn, and can be scaled. All three have to be applied, in that order, and the scale is
     * already carried by the room-space positions this takes.
     */
    private static Vector3f roomToWorld(Vector3f room, VRSettings settings) {
        Vec3 turned = Vec3.createVectorHelper(room.x, room.y, room.z);
        turned.rotateAroundY((float) Math.toRadians(settings.vrWorldRotation));
        Vec3 origin = VRRenderState.interPolatedRoomOrigin;
        return new Vector3f((float) (turned.xCoord + origin.xCoord),
                (float) (turned.yCoord + origin.yCoord),
                (float) (turned.zCoord + origin.zCoord));
    }

    /**
     * The menu's parked position for a seated player: two and a half metres along the average of
     * where they have been looking.
     *
     * <p>In play-area space, because that is the space the panel's pose is stored and pointed at
     * in. The caller turns it into a world position to draw it.
     *
     * @return null while there are not enough samples yet, so the caller keeps the live pose
     */
    private static Vector3f parkedMenuPose(VRSettings settings) {
        LinkedList<Vec3> positions = VRFrame.hmdPosSamples();
        LinkedList<Float> yaws = VRFrame.hmdYawSamples();
        if (positions.isEmpty() || yaws.isEmpty()) {
            return null;
        }

        double x = 0, y = 0, z = 0;
        for (Vec3 v : positions) {
            x += v.xCoord;
            y += v.yCoord;
            z += v.zCoord;
        }
        x /= positions.size();
        y /= positions.size();
        z /= positions.size();

        float yawAvg = 0;
        for (float f : yaws) {
            yawAvg += f;
        }
        yawAvg /= yaws.size();
        // The samples are taken facing the world; the play area may have been turned under the
        // player since, and that turn is applied again when this is drawn, so it comes off here.
        float yaw = (float) Math.toRadians(yawAvg - settings.vrWorldRotation);

        double distance = 2.5D * VR.player().worldScale;
        lastHudRotation = Matrix4f.rotationY((float) Math.PI - yaw);
        return new Vector3f(
                (float) (x - Math.sin(yaw) * distance),
                (float) y,
                (float) (z + Math.cos(yaw) * distance));
    }

    /**
     * The panel itself.
     *
     * <p>Drawn with alpha testing rather than blending, because the interface was rendered over a
     * black background and blending it would wash the whole panel grey. The cutoff is low enough
     * to keep anti-aliased text edges.
     */
    private static void drawPanel(Minecraft mc, boolean inMenuRoom) {
        buffer.bindFramebufferTexture();

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);

        // Clamped, or the filter reaches past the edge of the texture and draws a dark border
        // round the whole panel.
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        buffer.applyPanelFilter();

        // A menu is there to be read, so it is drawn solid; the in-game panel honours the
        // opacity setting. Either way the cutout is the alpha test below, which throws away the
        // pixels the interface never touched and so lets the world through round the edges.
        GL11.glColor4f(1, 1, 1, inMenuRoom ? 1F : VR.settings().hudOpacity);
        GL11.glEnable(GL11.GL_ALPHA_TEST);

        // The menu always wins over the world: being unable to reach the menu because a block is
        // in the way is not a trade worth making.
        if (!VR.settings().hudOcclusion || inMenuRoom) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
        GL11.glDepthMask(true);
        // Blending as well as the alpha test: the test decides what is drawn at all, blending
        // decides how solidly. Without it the opacity setting could only ever mean "all or
        // nothing", which is what it used to mean.
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Low enough to keep the softened edges of text, high enough to drop what was cleared.
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.01F);

        // The panel's own scale, times the world scale, so a shrunken or enlarged world keeps the
        // interface the same size relative to the hand it is attached to. The controller ray is
        // told the panel's own scale alone, which is the figure its coordinate mapping inverts.
        float scale = MCOpenVR.guiScale * (VR.player() != null ? VR.player().worldScale : 1F);
        GL11.glScalef(scale, scale, scale);

        ScaledResolution resolution = new ScaledResolution(mc, bufferWidth, bufferHeight);
        drawQuad(resolution.getScaledWidth(), resolution.getScaledHeight());

        // Put back everything this method changed. The next thing to draw is the other eye's
        // world, so anything left behind here would show up in one eye and not the other.
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        buffer.unbindFramebufferTexture();
    }

    /**
     * A quad {@link #QUAD_WIDTH} wide, as tall as the interface's aspect ratio makes it.
     *
     * <p>The texture's first row is the bottom of the image and the interface's first row is its
     * top, and the two cancel out: the interface was drawn through an orthographic projection with
     * its top edge at the far clip, which lands that top edge in the texture's last row. So the
     * panel's top vertex takes {@code v = 1} and the image comes out upright. The controller ray
     * agrees with this, because what it produces is a cursor position for LWJGL, whose origin is
     * also at the bottom.
     */
    private static void drawQuad(float width, float height) {
        float aspect = height / width;
        float halfW = QUAD_WIDTH / 2F;
        float halfH = QUAD_WIDTH * aspect / 2F;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex3f(-halfW, -halfH, 0.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex3f(halfW, -halfH, 0.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex3f(halfW, halfH, 0.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex3f(-halfW, halfH, 0.0F);
        GL11.glEnd();
    }

    public static void shutdown() {
        // Give the game its render target back before freeing ours. VR can be switched off in
        // the middle of a frame, by a failure or by the headset going to sleep, and if that
        // happens while the interface has the target borrowed then everything drawn afterwards
        // goes into a buffer that no longer exists.
        if (borrowedFrom != null) {
            ((IMinecraftExt) Minecraft.getMinecraft()).vivecraftx$setFramebufferMc(borrowedFrom);
            borrowedFrom = null;
        }
        if (buffer != null) {
            buffer.deleteFramebuffer();
            buffer = null;
        }
        VRMenuRoom.shutdown();
        capturing = false;
    }
}
