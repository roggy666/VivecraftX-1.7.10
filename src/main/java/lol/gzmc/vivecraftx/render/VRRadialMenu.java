package lol.gzmc.vivecraftx.render;

import com.mtbs3d.minecrift.control.ControllerType;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.VRSettings;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.util.KeyBindingUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

/**
 * A ring of actions you can reach without a keyboard.
 *
 * <p>A controller has about six buttons and VR wants thirty: drop, chat, pick block, inventory,
 * a torch, the player list, turning. Binding them all means either a controller nobody can
 * remember or an arm's reach to a keyboard that is not there. Hold one button, point, let go.
 *
 * <p>The arrangement is modern Vivecraft's, and why it is that shape is worth saying, because
 * the obvious shape does not work. Names written outward along the spokes of a wheel have to be
 * squeezed into the gap between spokes, and "Switch Walk and Teleport" does not fit in any gap
 * worth having -- which is exactly how the first attempt at this ended up with labels lying on
 * top of each other. So each entry is a flat rectangle, as wide as its own name needs to be,
 * and the rectangles are stacked at even vertical spacing with their distance from the middle
 * taken from the equation of an ellipse: two columns bulging outwards. Nothing is squeezed,
 * nothing is turned on its side, and nothing can collide however long the names get.
 */
public final class VRRadialMenu {

    private VRRadialMenu() {}

    /** Eight is as many as can be picked by pointing without care. */
    public static final int SLOTS = 8;

    /** Metres per interface pixel. Everything below is laid out in pixels, as the game is. */
    private static final float SCALE = 0.0027F;

    /** No entry is narrower than this, so a short name still gives a comfortable target. */
    private static final int MIN_WIDTH = 120;

    private static final int HEIGHT = 20;

    /** How far out in front of the hand the ring stands, in metres. */
    private static final double DISTANCE = 0.6D;

    private static boolean open;
    private static int selected = -1;
    private static int lastSelected = -1;

    /** Where it was put when it opened, so it does not drift about while you are choosing. */
    private static Vec3 centre;
    private static Vec3 right;
    private static Vec3 up;

    public static boolean isOpen() {
        return open;
    }

    /**
     * Opens, tracks and closes the ring. Called once a tick while the button is read.
     *
     * @param held whether the player is holding it open this tick
     */
    public static void process(boolean held) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!VRState.running() || mc.thePlayer == null || mc.currentScreen != null) {
            close(false);
            return;
        }
        if (!held) {
            close(true);
            return;
        }
        if (!open) {
            open = true;
            selected = -1;
            lastSelected = -1;
            place();
        }
        selected = pointedAt(mc);
        if (selected != lastSelected) {
            lastSelected = selected;
            if (selected >= 0) {
                MCOpenVR.triggerHapticPulse(ControllerType.RIGHT, 0.02F, 160F, 0.5F);
            }
        }
    }

    /**
     * Puts the ring out in front of the hand holding it open, facing back at that hand.
     *
     * <p>Out along where the controller points rather than at the controller itself, so it
     * appears in the direction the hand is already aimed. It tilts to face the hand, which is
     * what keeps it readable when the hand is held low: a panel that insisted on staying
     * upright would be seen edge-on.
     */
    private static void place() {
        Vec3 from = VR.roomScale().getControllerPos_World(1);
        Vec3 dir = VR.roomScale().getControllerDir_World(1);
        if (from == null || dir == null) {
            open = false;
            return;
        }
        double length = Math.sqrt(dir.xCoord * dir.xCoord + dir.yCoord * dir.yCoord
                + dir.zCoord * dir.zCoord);
        if (length < 1.0E-4D) {
            open = false;
            return;
        }
        double dx = dir.xCoord / length;
        double dy = dir.yCoord / length;
        double dz = dir.zCoord / length;

        centre = Vec3.createVectorHelper(from.xCoord + dx * DISTANCE,
                from.yCoord + dy * DISTANCE, from.zCoord + dz * DISTANCE);

        // The face of the panel looks back down the arm, at the person reading it. Getting this
        // the wrong way round is not subtly wrong: you are then reading the back of the panel,
        // which mirrors every word.
        Vec3 facing = Vec3.createVectorHelper(-dx, -dy, -dz);

        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.0E-4D) {
            // Pointing straight up or down: there is no sideways to speak of, so pick one.
            right = Vec3.createVectorHelper(1, 0, 0);
        } else {
            // Sideways is level with the ground, so the words stay horizontal however the hand
            // is tilted.
            right = normalise(cross(Vec3.createVectorHelper(0, 1, 0), facing));
        }
        // And up completes the set, which is what tilts the panel back to meet the hand.
        up = normalise(cross(facing, right));
    }

    private static Vec3 normalise(Vec3 v) {
        double length = Math.sqrt(v.xCoord * v.xCoord + v.yCoord * v.yCoord + v.zCoord * v.zCoord);
        if (length < 1.0E-6D) {
            return Vec3.createVectorHelper(1, 0, 0);
        }
        return Vec3.createVectorHelper(v.xCoord / length, v.yCoord / length, v.zCoord / length);
    }

    /**
     * Where one entry sits and how big it is, in interface pixels.
     *
     * <p>Even vertical spacing, horizontal distance from the equation of an ellipse, pulled
     * inward near the top and bottom, never allowed across the middle, and the second half
     * mirrored to the left. The width comes from the name, which is the whole point: the entry
     * grows for a long name rather than the name shrinking for the entry.
     *
     * @return the centre's x and y, then the width and height
     */
    private static float[] layout(Minecraft mc, int slot) {
        String label = labelFor(slot);
        int width = Math.max(MIN_WIDTH,
                (label == null ? 0 : mc.fontRenderer.getStringWidth(label)) + 4);

        float dist = SLOTS * 5.5F;
        float distX = SLOTS * 4F + width * 0.5F;

        float along = (slot < SLOTS / 2 ? slot : SLOTS - slot) / (float) (SLOTS / 2);
        float y = 2F * dist * along - dist;

        double inside = 1F - (y * y) / (dist * dist);
        float x = (float) (distX * Math.sqrt(inside < 0 ? 0 : inside));
        if (Math.abs(y) > 20F) {
            x *= 0.87F;
        }
        if (slot != 0 && slot != SLOTS / 2) {
            x = Math.max(width / 2F, x);
        }
        if (slot > SLOTS / 2) {
            x = -x;
        }
        return new float[] { x, y, width, HEIGHT };
    }

    /**
     * Which entry the main hand is pointing at, or -1.
     *
     * <p>The ray from the pointing hand is crossed with the plane the ring lies in, and the
     * crossing tested against each rectangle. Against the rectangles rather than by angle,
     * because the entries are not spread evenly around a circle.
     */
    private static int pointedAt(Minecraft mc) {
        if (centre == null) {
            return -1;
        }
        Vec3 from = VR.roomScale().getControllerPos_World(0);
        Vec3 dir = VR.roomScale().getControllerDir_World(0);
        if (from == null || dir == null) {
            return -1;
        }
        Vec3 normal = cross(right, up);
        double denominator = normal.xCoord * dir.xCoord + normal.yCoord * dir.yCoord
                + normal.zCoord * dir.zCoord;
        if (Math.abs(denominator) < 1.0E-5D) {
            return -1;
        }
        double t = (normal.xCoord * (centre.xCoord - from.xCoord)
                + normal.yCoord * (centre.yCoord - from.yCoord)
                + normal.zCoord * (centre.zCoord - from.zCoord)) / denominator;
        if (t < 0) {
            return -1;
        }
        double px = from.xCoord + dir.xCoord * t - centre.xCoord;
        double py = from.yCoord + dir.yCoord * t - centre.yCoord;
        double pz = from.zCoord + dir.zCoord * t - centre.zCoord;

        // Back into the pixels everything is laid out in. The vertical axis runs down the
        // screen, as it does everywhere else in the interface, so it flips on the way in.
        float u = (float) ((px * right.xCoord + py * right.yCoord + pz * right.zCoord) / SCALE);
        float v = (float) -((px * up.xCoord + py * up.yCoord + pz * up.zCoord) / SCALE);

        for (int slot = 0; slot < SLOTS; slot++) {
            if (labelFor(slot) == null) {
                continue;
            }
            float[] box = layout(mc, slot);
            if (Math.abs(u - box[0]) <= box[2] / 2F && Math.abs(v - box[1]) <= box[3] / 2F) {
                return slot;
            }
        }
        return -1;
    }

    private static void close(boolean fire) {
        if (open && fire && selected >= 0) {
            activate(selected);
        }
        open = false;
        selected = -1;
        lastSelected = -1;
    }

    /** Presses whatever this entry is bound to, for one tick, through the usual path. */
    private static void activate(int slot) {
        KeyBinding binding = bindingFor(slot);
        if (binding == null) {
            return;
        }
        KeyBindingUtil.press(binding);
        MCOpenVR.triggerHapticPulse(ControllerType.RIGHT, 0.06F, 160F, 0.9F);
    }

    /** This entry's name as the player reads it, or null if nothing is bound here. */
    private static String labelFor(int slot) {
        KeyBinding binding = bindingFor(slot);
        return binding == null ? null : I18n.format(binding.getKeyDescription());
    }

    private static KeyBinding bindingFor(int slot) {
        VRSettings settings = VR.settings();
        if (settings == null || slot < 0 || slot >= settings.vrRadialActions.length) {
            return null;
        }
        String name = settings.vrRadialActions[slot];
        if (name == null || name.isEmpty() || "none".equals(name)) {
            return null;
        }
        for (KeyBinding binding : Minecraft.getMinecraft().gameSettings.keyBindings) {
            if (binding.getKeyDescription().equals(name)) {
                return binding;
            }
        }
        for (KeyBinding binding : MCOpenVR.getKeyBindings()) {
            if (binding.getKeyDescription().equals(name)) {
                return binding;
            }
        }
        return null;
    }

    public static void render(float partialTicks) {
        if (!open || centre == null) {
            return;
        }
        if (!VRCamera.isEyePass(VR.currentPass())) {
            return;
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            if (!VRCamera.reapplyEyeCamera()) {
                return;
            }
            draw();
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("The radial menu failed to draw", t);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    private static void draw() {
        // Everything this turns off, it turns off for the rest of the eye unless it is put
        // back. Lighting is the one that shows: a later item drawn correctly comes out
        // unshaded, and reads as flat. Handing the whole block of state to the driver to
        // remember is both shorter than undoing each piece and proof against an exception
        // part way through, which is how it escaped in the first place.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        try {
            Minecraft mc = Minecraft.getMinecraft();
            Vec3 origin = VRRenderState.interpolatedPlayerPos;
            if (origin == null) {
                return;
            }

            GL11.glTranslated(centre.xCoord - origin.xCoord, centre.yCoord - origin.yCoord,
                    centre.zCoord - origin.zCoord);
            GL11.glMultMatrix(frame());
            // Into interface pixels, with the vertical axis running down as it does everywhere else
            // in the interface, so that the layout and the font agree without either being a
            // special case.
            GL11.glScalef(SCALE, -SCALE, SCALE);

            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            for (int slot = 0; slot < SLOTS; slot++) {
                String label = labelFor(slot);
                if (label == null) {
                    continue;
                }
                entry(mc, label, layout(mc, slot), slot == selected);
            }

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** One rectangle with its name across it, horizontally and at its own size. */
    private static void entry(Minecraft mc, String label, float[] box, boolean lit) {
        float left = box[0] - box[2] / 2F;
        float top = box[1] - box[3] / 2F;
        float edge = left + box[2];
        float bottom = top + box[3];

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        if (lit) {
            tes.setColorRGBA_F(0.45F, 0.72F, 1.0F, 0.95F);
        } else {
            tes.setColorRGBA_F(0.06F, 0.06F, 0.08F, 0.8F);
        }
        tes.addVertex(left, bottom, 0);
        tes.addVertex(edge, bottom, 0);
        tes.addVertex(edge, top, 0);
        tes.addVertex(left, top, 0);
        tes.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        int width = mc.fontRenderer.getStringWidth(label);
        mc.fontRenderer.drawString(label, (int) (box[0] - width / 2F), (int) (box[1] - 4F),
                0xFFFFFFFF);
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return Vec3.createVectorHelper(
                a.yCoord * b.zCoord - a.zCoord * b.yCoord,
                a.zCoord * b.xCoord - a.xCoord * b.zCoord,
                a.xCoord * b.yCoord - a.yCoord * b.xCoord);
    }

    /**
     * The ring's orientation, as a matrix GL can be given.
     *
     * <p>The buffer is kept and refilled rather than allocated here, for the same reason as
     * everywhere else on the render path: it has to be a direct one, and a direct buffer costs a
     * native allocation and a cleaner the garbage collector must walk with the world stopped. The
     * one caller hands it straight to the matrix stack.
     */
    private static final java.nio.FloatBuffer FRAME =
            org.lwjgl.BufferUtils.createFloatBuffer(16);

    private static java.nio.FloatBuffer frame() {
        java.nio.FloatBuffer buffer = FRAME;
        buffer.clear();
        Vec3 normal = cross(right, up);
        buffer.put((float) right.xCoord).put((float) right.yCoord).put((float) right.zCoord)
                .put(0F);
        buffer.put((float) up.xCoord).put((float) up.yCoord).put((float) up.zCoord).put(0F);
        buffer.put((float) normal.xCoord).put((float) normal.yCoord).put((float) normal.zCoord)
                .put(0F);
        buffer.put(0F).put(0F).put(0F).put(1F);
        buffer.flip();
        return buffer;
    }
}
