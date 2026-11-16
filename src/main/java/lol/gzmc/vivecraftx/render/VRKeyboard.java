package lol.gzmc.vivecraftx.render;

import com.mtbs3d.minecrift.control.ControllerType;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.utils.InputInjector;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.util.VanillaExt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * A keyboard standing in the world, typed on with your fingertips.
 *
 * <p>The alternative is SteamVR's own keyboard, which is a dashboard overlay: it floats in front
 * of everything, dims the game behind it, cannot be moved, and is switched off entirely in
 * seated mode. It also requires the game window to have focus, which it often does not. This one
 * is part of the world, stays where you put it, and is poked rather than pointed at.
 *
 * <p>Keys are hit by the tips of the controllers -- the same tip pose the held item hangs off --
 * and a key only fires when a tip crosses its face from the front, so running a hand across the
 * board does not type a line of rubbish.
 */
public final class VRKeyboard {

    private VRKeyboard() {}

    /** Width and height of one key, in metres. */
    private static final float KEY = 0.045F;
    private static final float GAP = 0.006F;

    /** How close to the face of a key a fingertip must come to press it. */
    private static final float DEPTH = 0.035F;

    /**
     * How far through the board a key goes on holding a fingertip that has pushed past its face.
     *
     * <p>Nothing stops a hand at the face of a board that is standing in mid-air, so a poke
     * carries well past it and comes back out the same way. Without this the far side of the
     * press region would be a wall the fingertip leaves through on the way in and crosses again on
     * the way out, which reads as two pokes and types everything twice.
     */
    private static final float THROUGH = 0.15F;

    private static final String[] ROWS = {
            "1234567890",
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm,.",
    };

    private static boolean showing;
    private static boolean shift;

    /** Where the board was put when it appeared, so it does not chase the player about. */
    private static Vec3 centre;
    private static Vec3 right;
    private static Vec3 up;

    /** Which key each tip is currently inside, so a key fires once per poke. */
    private static final int[] inside = { -1, -1 };

    /** Set while the board is up because a screen asked for it, not because the player did. */
    private static boolean openedForScreen;

    public static boolean isShowing() {
        return showing;
    }

    /**
     * Brings a keyboard up when a screen that wants typing opens, and takes it away again.
     *
     * <p>Without this the only way to type is to find the binding for it first, which is a
     * strange thing to ask of somebody who has just pressed "chat" and is looking at a cursor
     * blinking at them. Upstream opens one on the same screens.
     *
     * <p>Only closes what it opened. A player who summoned the board themselves keeps it.
     */
    public static void onScreenChanged(GuiScreen opening) {
        if (!VRState.running() || VR.settings() == null) {
            return;
        }
        if (wantsTyping(opening)) {
            if (!isAnyKeyboardUp()) {
                openedForScreen = true;
                show(true);
            }
        } else if (openedForScreen) {
            openedForScreen = false;
            show(false);
        }
    }

    /**
     * Which of the two keyboards, by the same rule the binding uses: the one standing in the
     * world unless the player asked for SteamVR's, and always SteamVR's when seated, because the
     * other one needs hands to poke it with.
     */
    private static void show(boolean visible) {
        if (VR.settings().vrPhysicalKeyboard && !VR.settings().seated) {
            setShowing(visible);
        } else {
            MCOpenVR.setKeyboardOverlayShowing(visible, null);
        }
    }

    private static boolean isAnyKeyboardUp() {
        return showing || MCOpenVR.keyboardShowing;
    }

    /**
     * Whether this screen is one somebody types into.
     *
     * <p>Named outright rather than looked for. A text field in this version does not announce
     * itself, and the alternative -- reflecting over every screen for a focused one -- guesses
     * wrong on mod screens in both directions.
     */
    private static boolean wantsTyping(GuiScreen screen) {
        return screen instanceof net.minecraft.client.gui.GuiChat
                || screen instanceof net.minecraft.client.gui.GuiScreenBook
                || screen instanceof net.minecraft.client.gui.inventory.GuiEditSign
                || screen instanceof net.minecraft.client.gui.GuiRepair
                || screen instanceof net.minecraft.client.gui.GuiCommandBlock;
    }

    /** Shows or hides the board, placing it in front of the player when it appears. */
    public static void setShowing(boolean show) {
        if (show == showing) {
            return;
        }
        if (!show) {
            openedForScreen = false;
        }
        showing = show;
        shift = false;
        inside[0] = -1;
        inside[1] = -1;
        if (show) {
            place();
        }
    }

    private static void place() {
        Vec3 eye = VR.roomScale().getHMDPos_World();
        Vec3 look = VR.roomScale().getHMDDir_World();
        if (eye == null || look == null) {
            showing = false;
            return;
        }
        double flat = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        if (flat < 1.0E-4D) {
            showing = false;
            return;
        }
        double fx = look.xCoord / flat;
        double fz = look.zCoord / flat;

        // Within reach and below the line of sight, where a keyboard belongs: you look down at
        // one, you do not hold it up in front of your face.
        centre = Vec3.createVectorHelper(eye.xCoord + fx * 0.45D, eye.yCoord - 0.35D,
                eye.zCoord + fz * 0.45D);
        // The player's own right, which is the flat look direction crossed with world up. It
        // had been the opposite of that, and the two sign errors that follow from it are the
        // whole bug: a board whose face points away from the reader and whose writing runs the
        // wrong way across it. The keys are placed from this basis and so is the quad, so both
        // were wrong together and neither looked obviously mirrored -- only backwards.
        right = Vec3.createVectorHelper(-fz, 0, fx);
        // Tilted the way a keyboard on a desk is: the far edge raised, so the face looks up and
        // back toward whoever is typing on it.
        double tilt = Math.toRadians(45);
        up = Vec3.createVectorHelper(fx * Math.sin(tilt), Math.cos(tilt), fz * Math.sin(tilt));
    }

    /**
     * Looks for fingertips in keys. Called once a frame with the rest of the input, not once a
     * tick, which is what gives a quick poke enough samples to be seen at all.
     */
    public static void process() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!showing || !VRState.running() || centre == null) {
            return;
        }
        if (mc.currentScreen == null) {
            setShowing(false);
            return;
        }
        for (int hand = 0; hand < 2; hand++) {
            // In world space, because that is the space the board was placed in. The smoothed aim
            // source on its own is a room position, and measuring it against a board standing in
            // the world compared a hand a metre from the play area with a board a few hundred
            // blocks from the world origin: the depth came out in the hundreds and no key could
            // ever be reached. The board looked right because only the drawing was ever in world
            // space.
            Vec3 tip = VR.player().getSmoothedControllerPos_World(hand, 0.03D);
            if (tip == null) {
                continue;
            }
            int key = keyUnder(tip, inside[hand]);
            if (key != inside[hand]) {
                if (key >= 0) {
                    press(mc, key);
                    MCOpenVR.triggerHapticPulse(
                            hand == 0 ? ControllerType.RIGHT : ControllerType.LEFT,
                            0.03F, 160F, 0.7F);
                }
                inside[hand] = key;
            }
        }
    }

    /**
     * Which key this fingertip is in, or -1. The tip must be in world space, as the board is.
     *
     * <p>Only counts while the tip is at the face of the board, so a hand resting below the board
     * or waving past it presses nothing.
     *
     * @param held which key this same tip was in a moment ago, which reaches further back
     */
    private static int keyUnder(Vec3 tip, int held) {
        double dx = tip.xCoord - centre.xCoord;
        double dy = tip.yCoord - centre.yCoord;
        double dz = tip.zCoord - centre.zCoord;

        Vec3 normal = cross(right, up);
        double depth = dx * normal.xCoord + dy * normal.yCoord + dz * normal.zCoord;
        // The side the face looks out of is the side the typist is on, so a finger about to
        // press a key is in front of the board, not behind it. This read the other way round
        // while the board itself faced the other way.
        if (depth > DEPTH) {
            return -1;
        }
        double u = dx * right.xCoord + dy * right.yCoord + dz * right.zCoord;
        double v = dx * up.xCoord + dy * up.yCoord + dz * up.zCoord;

        int key = keyAt(u, v);
        // A key the tip is already holding keeps it however far past the face the poke carries;
        // any other key is the thin face only, so that a hand behind the board types nothing and
        // sliding sideways while deep behind it lets go rather than running along the row.
        double back = key >= 0 && key == held ? -THROUGH : 0.0D;
        return depth < back ? -1 : key;
    }

    /** Which key covers this point on the face of the board, in metres from its centre. */
    private static int keyAt(double u, double v) {
        int index = 0;
        for (int row = 0; row < ROWS.length; row++) {
            String keys = ROWS[row];
            float rowWidth = keys.length() * (KEY + GAP) - GAP;
            float top = (ROWS.length / 2F - row) * (KEY + GAP);
            for (int column = 0; column < keys.length(); column++) {
                float left = -rowWidth / 2F + column * (KEY + GAP);
                if (u >= left && u <= left + KEY && v <= top && v >= top - KEY) {
                    return index;
                }
                index++;
            }
        }
        // The bottom row: shift, space, backspace, enter.
        float width = 4 * (KEY + GAP) * 1.5F;
        float top = (ROWS.length / 2F - ROWS.length) * (KEY + GAP);
        for (int i = 0; i < 4; i++) {
            float left = -width / 2F + i * (KEY + GAP) * 1.5F;
            if (u >= left && u <= left + KEY * 1.5F && v <= top && v >= top - KEY) {
                return index + i;
            }
        }
        return -1;
    }

    private static void press(Minecraft mc, int key) {
        int plain = 0;
        for (String row : ROWS) {
            plain += row.length();
        }
        if (key < plain) {
            char c = charAt(key);
            type(mc, c, Keyboard.getKeyIndex(String.valueOf(Character.toUpperCase(c))));
            return;
        }
        switch (key - plain) {
            case 0:
                shift = !shift;
                break;
            case 1:
                type(mc, ' ', Keyboard.KEY_SPACE);
                break;
            case 2:
                type(mc, '\u0000', Keyboard.KEY_BACK);
                break;
            default:
                type(mc, '\r', Keyboard.KEY_RETURN);
                break;
        }
    }

    private static char charAt(int index) {
        int seen = 0;
        for (String row : ROWS) {
            if (index < seen + row.length()) {
                char c = row.charAt(index - seen);
                return shift ? Character.toUpperCase(c) : c;
            }
            seen += row.length();
        }
        return ' ';
    }

    /**
     * Hands the character to whatever screen is open.
     *
     * <p>Straight to the screen rather than through the operating system, which is how the
     * overlay keyboard has to do it: that way it works whether or not the game window has
     * focus, which with a headset on it frequently does not.
     */
    private static void type(Minecraft mc, char c, int code) {
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            return;
        }
        try {
            VanillaExt.keyTyped(screen, c, code);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("A keyboard press did not reach the screen", t);
        }
    }

    public static void render(float partialTicks) {
        if (!showing || centre == null || !VRCamera.isEyePass(VR.currentPass())) {
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
            ViveCraftX.LOG.debug("The keyboard failed to draw", t);
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

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int index = 0;
            for (int row = 0; row < ROWS.length; row++) {
                String keys = ROWS[row];
                float rowWidth = keys.length() * (KEY + GAP) - GAP;
                float top = (ROWS.length / 2F - row) * (KEY + GAP);
                for (int column = 0; column < keys.length(); column++) {
                    float left = -rowWidth / 2F + column * (KEY + GAP);
                    boolean lit = inside[0] == index || inside[1] == index;
                    cap(mc, left, top, KEY, lit,
                            String.valueOf(shift ? Character.toUpperCase(keys.charAt(column))
                                                 : keys.charAt(column)));
                    index++;
                }
            }
            float width = 4 * (KEY + GAP) * 1.5F;
            float top = (ROWS.length / 2F - ROWS.length) * (KEY + GAP);
            String[] wide = { shift ? "SHIFT*" : "shift", "space", "back", "enter" };
            for (int i = 0; i < 4; i++) {
                float left = -width / 2F + i * (KEY + GAP) * 1.5F;
                boolean lit = inside[0] == index + i || inside[1] == index + i;
                cap(mc, left, top, KEY * 1.5F, lit, wide[i]);
            }

            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void cap(Minecraft mc, float left, float top, float width, boolean lit,
            String label) {
        Tessellator tes = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        tes.startDrawingQuads();
        if (lit) {
            tes.setColorRGBA_F(0.45F, 0.72F, 1.0F, 0.95F);
        } else {
            tes.setColorRGBA_F(0.08F, 0.08F, 0.1F, 0.85F);
        }
        tes.addVertex(left, top, 0);
        tes.addVertex(left + width, top, 0);
        tes.addVertex(left + width, top - KEY, 0);
        tes.addVertex(left, top - KEY, 0);
        tes.draw();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPushMatrix();
        GL11.glTranslatef(left + width / 2F, top - KEY / 2F, 0.001F);
        final float scale = 0.0016F;
        GL11.glScalef(scale, -scale, scale);
        int w = mc.fontRenderer.getStringWidth(label);
        mc.fontRenderer.drawString(label, -w / 2, -4, 0xFFFFFFFF);
        GL11.glPopMatrix();
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return Vec3.createVectorHelper(
                a.yCoord * b.zCoord - a.zCoord * b.yCoord,
                a.zCoord * b.xCoord - a.xCoord * b.zCoord,
                a.xCoord * b.yCoord - a.yCoord * b.xCoord);
    }

    /**
     * The keyboard's orientation, as a matrix the graphics library can be given.
     *
     * <p>The buffer is kept and refilled rather than allocated here. It has to be a direct one,
     * and a direct buffer costs a native allocation and a cleaner the garbage collector must walk
     * with the world stopped -- a poor thing to spend once an eye for every frame the keyboard is
     * up. The one caller hands it straight to the matrix stack, so nothing is relying on what was
     * in it before.
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
