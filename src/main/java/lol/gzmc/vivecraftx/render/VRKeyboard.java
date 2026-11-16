package lol.gzmc.vivecraftx.render;

import java.util.ArrayList;
import java.util.List;

import com.mtbs3d.minecrift.control.ControllerType;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.VRSettings;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.util.VanillaExt;
import lol.gzmc.vivecraftx.util.Vec3Util;

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
 * board does not type a line of rubbish. While the board is up, the arms are replaced by a single
 * pointing finger per hand whose far end is exactly the point tested against the keys: that is
 * what turns typing from guesswork into aiming, and it is why the two must be computed from the
 * same expression rather than from two that ought to agree.
 *
 * <p>A key carries a <i>character</i>, not a key code. Upstream made that choice and it is the
 * only way a non-Latin board can work at all, since there is no key code for Cyrillic to send.
 * Codes are still filled in where one exists, because the control keys are recognised by code --
 * backspace, enter, escape -- and because a screen that watches for a shortcut should see what a
 * real keyboard would have sent.
 */
public final class VRKeyboard {

    private VRKeyboard() {}

    /** Width and height of one key, in metres. Upstream's figures. */
    private static final float KEY = 0.04F;
    private static final float GAP = 0.0064F;

    /** The keys in the columns either side of the grid -- shift, enter, escape -- are wider. */
    private static final float WIDE = KEY * 2 + GAP;

    /**
     * How thick a key is.
     *
     * <p>The whole of it presses, and its face is drawn at the near end rather than the far one,
     * so the surface the player aims at is the surface that fires. It had been the other way
     * round: the caps were drawn on the plane of the board with the press region in front of
     * them, so a key went off three centimetres before the finger reached the cap it belonged to,
     * which is precisely the "typing is awkward" the board was reported for.
     */
    private static final float DEPTH = 0.028F;

    /** How far a pressed cap drops, so a press is visible as well as felt. */
    private static final float SINK = 0.012F;

    /**
     * How much further a key reaches once a fingertip is in it.
     *
     * <p>Nothing stops a hand at the face of a board that is standing in mid-air, so a poke
     * carries well past it and comes back out the same way. Without this the far side of the
     * press region would be a wall the fingertip leaves through on the way in and crosses again
     * on the way out, which reads as two pokes and types everything twice. Both ways, as
     * upstream does it: the near side matters too, because a hand that trembles back a few
     * millimetres should not let go of the key it is holding.
     */
    private static final float THROUGH = 0.08F;

    private static final int COLUMNS = VRKeyboardLayout.COLUMNS;

    /** The shortest board the keys down either side will fit on. */
    private static final int ROWS = 4;

    /**
     * The shortest time between one key and the next.
     *
     * <p>Upstream's figure. Two keys are four centimetres apart and a hand crossing the board
     * passes over several of them, so without a floor on the rate a sweep types a word nobody
     * asked for. It is long enough to be impossible to beat deliberately and short enough that
     * two hands typing alternately never notice it, because the clock is kept per hand.
     */
    private static final long DEBOUNCE_MS = 150L;

    /** Twice in quick succession on shift leaves it on, rather than for one character. */
    private static final long STICKY_MS = 400L;

    /**
     * The pointing finger, from upstream: a bar reaching back from the point that is tested
     * against the keys, hanging below the controller's own axis so that it does not stand between
     * the eye and the key being pressed.
     */
    private static final float FINGER_LENGTH = 0.18F;
    private static final float FINGER_HALF_WIDTH = 0.02F;
    private static final float FINGER_DROP = 0.025F;

    /**
     * How long a window the fingertip is averaged over.
     *
     * <p>Upstream does not smooth at all and leans on the press rate above to absorb the jitter.
     * Both are kept here, and the smoothing is the reason the drawn finger is worth looking at:
     * a tracker that wanders a couple of millimetres puts the tip over the edge of a key and back
     * several times a second, which an undrawn finger hides and a drawn one does not. Thirty
     * milliseconds is a third of a frame at headset rate -- far too short to feel as lag, long
     * enough to settle the tip.
     */
    private static final double SMOOTHING = 0.03D;

    private static final Vec3 UP = Vec3.createVectorHelper(0, 1, 0);

    /** What a key does when it fires. */
    private static final int TYPES = 0;
    private static final int SHIFTS = 1;
    private static final int SWITCHES_LAYOUT = 2;

    private static boolean showing;
    private static boolean shift;
    private static boolean shiftSticky;
    private static long shiftPressedAt;

    /** Where the board was put when it appeared, so it does not chase the player about. */
    private static Vec3 centre;
    private static Vec3 right;
    private static Vec3 up;

    private static Key[] keys;

    /** What the current key list was built from, so a layout or shift change is noticed. */
    private static String builtFrom;
    private static String builtFromShift;
    private static boolean builtShifted;
    private static boolean builtSticky;

    /** Which key each tip is currently inside, so a key fires once per poke. */
    private static final Key[] held = new Key[2];
    private static final long[] settledAt = new long[2];

    /** Set while the board is up because a screen asked for it, not because the player did. */
    private static boolean openedForScreen;

    /**
     * One key: where it is on the face of the board, what it says, and what it sends.
     *
     * <p>The identity is the number, not the object. Changing shift or layout rebuilds the whole
     * list, and a finger resting on a key across that rebuild has to come out holding the same
     * key or the rebuild reads as a release and a fresh press -- which, on the shift key that
     * caused it, means shift turns itself straight back off again.
     */
    private static final class Key {

        final int id;
        final float left;
        final float top;
        final float width;
        final String label;
        final char typed;
        final int code;
        final int does;

        /** True for the keys that come out of the layout grid, which shift applies to. */
        final boolean fromGrid;

        Key(int id, float left, float top, float width, String label, char typed, int code,
                int does, boolean fromGrid) {
            this.id = id;
            this.left = left;
            this.top = top;
            this.width = width;
            this.label = label;
            this.typed = typed;
            this.code = code;
            this.does = does;
            this.fromGrid = fromGrid;
        }
    }

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
            if (!isAnyShowing()) {
                openedForScreen = true;
                requestShow(true);
            }
        } else if (openedForScreen) {
            openedForScreen = false;
            requestShow(false);
        }
    }

    /**
     * Which of the two keyboards, by the same rule the binding uses: the one standing in the
     * world unless the player asked for SteamVR's, and always SteamVR's when seated, because the
     * other one needs hands to poke it with.
     *
     * <p>Public because a text field taking focus goes through here too, and it has no business
     * knowing which of the two the player has chosen.
     */
    public static void requestShow(boolean visible) {
        if (!VRState.running() || VR.settings() == null) {
            return;
        }
        if (VR.settings().vrPhysicalKeyboard && !VR.settings().seated) {
            setShowing(visible);
        } else {
            MCOpenVR.setKeyboardOverlayShowing(visible, null);
        }
    }

    /** Either keyboard, for anything that only wants to know whether one is already in the way. */
    public static boolean isAnyShowing() {
        return showing || MCOpenVR.keyboardShowing;
    }

    /**
     * Whether this screen is one somebody types into.
     *
     * <p>Named outright rather than looked for. A text field in this version does not announce
     * itself, and the alternative -- reflecting over every screen for a focused one -- guesses
     * wrong on mod screens in both directions. A field that does announce itself, by taking
     * focus, goes through {@link #requestShow} instead and needs no entry here.
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
        if (!shiftSticky) {
            shift = false;
        }
        held[0] = null;
        held[1] = null;
        keys = null;
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
        if (!showing || !VRState.running() || centre == null || VR.settings() == null) {
            return;
        }
        if (mc.currentScreen == null) {
            setShowing(false);
            return;
        }
        layout();
        long now = Minecraft.getSystemTime();
        for (int hand = 0; hand < 2; hand++) {
            Vec3 tip = tip(hand);
            if (tip == null) {
                continue;
            }
            Key key = keyUnder(tip, held[hand]);
            if (key == null) {
                if (held[hand] != null) {
                    held[hand] = null;
                    settledAt[hand] = now;
                }
            } else if (key != held[hand] && now - settledAt[hand] >= DEBOUNCE_MS) {
                held[hand] = key;
                settledAt[hand] = now;
                MCOpenVR.triggerHapticPulse(
                        hand == 0 ? ControllerType.RIGHT : ControllerType.LEFT,
                        0.03F, 160F, 0.7F);
                press(mc, key);
            }
        }
    }

    /**
     * Where this hand's finger ends, which is the only point the board is ever asked about.
     *
     * <p>In world space, because that is the space the board was placed in. The smoothed aim
     * source on its own is a room position, and measuring it against a board standing in the
     * world compared a hand a metre from the play area with a board a few hundred blocks from
     * the world origin: the depth came out in the hundreds and no key could ever be reached. The
     * board looked right because only the drawing was ever in world space.
     */
    private static Vec3 tip(int hand) {
        return VR.player() == null ? null : VR.player().getSmoothedControllerPos_World(hand, SMOOTHING);
    }

    /**
     * Which key this fingertip is in, or null. The tip must be in world space, as the board is.
     *
     * @param holding which key this same tip was in a moment ago, which reaches further
     */
    private static Key keyUnder(Vec3 tip, Key holding) {
        double dx = tip.xCoord - centre.xCoord;
        double dy = tip.yCoord - centre.yCoord;
        double dz = tip.zCoord - centre.zCoord;

        Vec3 normal = cross(right, up);
        double depth = dx * normal.xCoord + dy * normal.yCoord + dz * normal.zCoord;
        double u = dx * right.xCoord + dy * right.yCoord + dz * right.zCoord;
        double v = dx * up.xCoord + dy * up.yCoord + dz * up.zCoord;

        for (Key key : keys) {
            if (u < key.left || u > key.left + key.width || v > key.top || v < key.top - KEY) {
                continue;
            }
            // The side the face looks out of is the side the typist is on, so a finger about to
            // press a key is in front of the board, not behind it. This read the other way
            // round while the board itself faced the other way.
            double near = key == holding ? DEPTH + THROUGH : DEPTH;
            double far = key == holding ? -THROUGH : 0.0D;
            return depth <= near && depth >= far ? key : null;
        }
        return null;
    }

    private static void press(Minecraft mc, Key key) {
        long now = Minecraft.getSystemTime();
        switch (key.does) {
            case SHIFTS:
                if (shift && !shiftSticky && now - shiftPressedAt < STICKY_MS) {
                    setShift(true, true);
                } else {
                    setShift(!shift, false);
                }
                shiftPressedAt = now;
                break;
            case SWITCHES_LAYOUT:
                nextLayout();
                break;
            default:
                type(mc, key.typed, key.code);
                // Shift is for one character unless it was tapped twice, the way it is on a
                // telephone. Nothing else clears it, so the two ends of a row stay usable as
                // somewhere to park a thumb.
                if (key.fromGrid && !shiftSticky) {
                    setShift(false, false);
                }
                break;
        }
    }

    private static void setShift(boolean on, boolean sticky) {
        if (on == shift && sticky == shiftSticky) {
            return;
        }
        shift = on;
        shiftSticky = on && sticky;
    }

    /**
     * Moves to the next layout and keeps it.
     *
     * <p>Saved here rather than left for whenever the settings next happen to be written: the
     * player who went looking for the Cyrillic board wants it to still be Cyrillic tomorrow, and
     * nothing else in a session is going to save the settings for us.
     */
    private static void nextLayout() {
        VRSettings settings = VR.settings();
        String[] next = VRKeyboardLayout.next(settings.keyboardKeys, settings.keyboardKeysShift);
        settings.keyboardKeys = next[0];
        settings.keyboardKeysShift = next[1];
        settings.saveOptions();
    }

    // --- the keys ---------------------------------------------------------------------------

    /** Rebuilds the key list if the layout, or shift, has moved on since it was built. */
    private static void layout() {
        VRSettings settings = VR.settings();
        String grid = settings.keyboardKeys;
        String gridShift = settings.keyboardKeysShift;
        if (keys != null && builtShifted == shift && builtSticky == shiftSticky
                && grid.equals(builtFrom) && gridShift.equals(builtFromShift)) {
            return;
        }
        build(grid, gridShift);
    }

    private static void build(String grid, String gridShift) {
        String chars = shift ? gridShift : grid;
        // Never fewer than four, however short the grid is. The control keys down either side
        // need four rows to sit in, and a board that shrank under them would stack Escape, Tab
        // and Shift on the same square -- which is what upstream does, since it places them at
        // fixed rows and lets the grid decide the height on its own.
        int rows = Math.max(ROWS, (chars.length() + COLUMNS - 1) / COLUMNS);

        // Centred on itself, so that where the board hangs is decided in one place.
        float step = KEY + GAP;
        float boardWidth = COLUMNS * step - GAP + 2 * (WIDE + GAP);
        float boardHeight = (rows + 1) * step - GAP;
        float leftColumn = -boardWidth / 2F;
        float gridLeft = leftColumn + WIDE + GAP;
        float rightColumn = gridLeft + COLUMNS * step;
        float firstRow = boardHeight / 2F;
        float lastRow = firstRow - (rows - 1) * step;
        float bottomRow = firstRow - rows * step;

        List<Key> out = new ArrayList<Key>(rows * COLUMNS + 10);
        for (int row = 0; row < rows; row++) {
            float top = firstRow - row * step;
            for (int column = 0; column < COLUMNS; column++) {
                int index = row * COLUMNS + column;
                if (index >= chars.length()) {
                    break;
                }
                char c = chars.charAt(index);
                // A grid that does not fill its last row is padded with blanks. A key that looks
                // empty and types a space is a trap, so there simply is not one.
                if (c == ' ') {
                    continue;
                }
                out.add(new Key(index, gridLeft + column * step, top, KEY, String.valueOf(c),
                        c, codeFor(c), TYPES, true));
            }
        }

        // The two columns of control keys, in upstream's places.
        out.add(new Key(1000, leftColumn, firstRow, WIDE, "Esc",
                (char) 27, Keyboard.KEY_ESCAPE, TYPES, false));
        out.add(new Key(1001, leftColumn, firstRow - step, WIDE, "Tab",
                (char) 9, Keyboard.KEY_TAB, TYPES, false));
        out.add(new Key(1002, rightColumn, firstRow, WIDE, "Bksp",
                (char) 8, Keyboard.KEY_BACK, TYPES, false));
        out.add(new Key(1003, rightColumn, firstRow - 2 * step, WIDE, "Enter",
                (char) 13, Keyboard.KEY_RETURN, TYPES, false));

        // Shift at both ends of the last row of letters, so neither hand has to reach across.
        String shiftLabel = shiftSticky ? "SHIFT" : shift ? "Shift*" : "Shift";
        out.add(new Key(1004, leftColumn, lastRow, WIDE, shiftLabel,
                (char) 0, 0, SHIFTS, false));
        out.add(new Key(1005, rightColumn, lastRow, WIDE, shiftLabel,
                (char) 0, 0, SHIFTS, false));

        // The bottom row: which layout is in use, the space bar, and the two arrows that move
        // the cursor. Those two go through the screen like everything else here, which is why
        // they work with the game window unfocused -- upstream's send them through the operating
        // system and quietly do nothing when it is not.
        out.add(new Key(1006, leftColumn, bottomRow, WIDE,
                VRKeyboardLayout.label(grid, gridShift), (char) 0, 0, SWITCHES_LAYOUT, false));
        out.add(new Key(1007, gridLeft + ((COLUMNS - 5) / 2F) * step, bottomRow,
                5 * step - GAP, "Space", ' ', Keyboard.KEY_SPACE, TYPES, false));
        out.add(new Key(1008, gridLeft + (COLUMNS - 2) * step, bottomRow, KEY, "←",
                (char) 0, Keyboard.KEY_LEFT, TYPES, false));
        out.add(new Key(1009, gridLeft + (COLUMNS - 1) * step, bottomRow, KEY, "→",
                (char) 0, Keyboard.KEY_RIGHT, TYPES, false));

        keys = out.toArray(new Key[out.size()]);
        builtFrom = grid;
        builtFromShift = gridShift;
        builtShifted = shift;
        builtSticky = shiftSticky;

        // The keys a finger is resting in are objects from the list that has just been thrown
        // away. Found again by number rather than dropped, or the rebuild would read as a
        // release followed by a fresh press of whatever is now under the finger.
        for (int hand = 0; hand < 2; hand++) {
            if (held[hand] == null) {
                continue;
            }
            Key again = null;
            for (Key key : keys) {
                if (key.id == held[hand].id) {
                    again = key;
                    break;
                }
            }
            held[hand] = again;
        }
    }

    /**
     * The key code this character would have arrived with from a real keyboard, or none.
     *
     * <p>There is no code for Cyrillic, and that is the point of sending characters: nothing
     * here depends on finding one. Zero is what an unknown name resolves to and is safe to
     * deliver -- every screen that switches on a code falls through to the character for it.
     */
    private static int codeFor(char c) {
        return Keyboard.getKeyIndex(String.valueOf(Character.toUpperCase(c)));
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

    // --- drawing ----------------------------------------------------------------------------

    public static void render(float partialTicks) {
        inEyeCamera(false);
    }

    /**
     * The pointing fingers, which take the place of the arms while the board is up.
     *
     * <p>Called from the hands pass rather than from here, so that the two are alternatives and
     * not an arm with a finger sticking out of it.
     */
    public static void renderFingers(float partialTicks) {
        inEyeCamera(true);
    }

    /**
     * Restores the eye's own camera and draws one of the two passes under it.
     *
     * <p>Both passes are reached from a point in the frame where the projection has been
     * replaced, so neither can simply draw: the matrices have to be saved, the eye's camera put
     * back, and the matrices restored on every way out, exception included.
     */
    private static void inEyeCamera(boolean fingers) {
        if (!showing || centre == null || VR.settings() == null
                || !VRCamera.isEyePass(VR.currentPass())) {
            return;
        }
        // The board is placed when it appears but its keys are laid out on demand, and the first
        // eye of the first frame can reach here before the input pass has asked for them.
        layout();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            if (!VRCamera.reapplyEyeCamera()) {
                return;
            }
            if (fingers) {
                drawFingers();
            } else {
                drawBoard();
            }
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

    private static void drawBoard() {
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

            // The whole board in one batch. Every quad on it is untextured and carries its own
            // colour, so there is nothing to break it up for, and sixty keys is sixty draw calls
            // the other way round.
            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            backplate(tes);
            for (Key key : keys) {
                cap(tes, key, key == held[0] || key == held[1]);
            }
            tes.draw();

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            for (Key key : keys) {
                label(mc, key, key == held[0] || key == held[1]);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * The plate the keys stand on.
     *
     * <p>It closes the open back of every key box, and it is what tells the player where the
     * board is when every key on it happens to be dark.
     */
    private static void backplate(Tessellator tes) {
        float minU = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (Key key : keys) {
            minU = Math.min(minU, key.left - GAP);
            maxU = Math.max(maxU, key.left + key.width + GAP);
            minV = Math.min(minV, key.top - KEY - GAP);
            maxV = Math.max(maxV, key.top + GAP);
        }
        tes.setColorRGBA_F(0.04F, 0.04F, 0.05F, 0.80F);
        tes.addVertex(minU, maxV, 0);
        tes.addVertex(maxU, maxV, 0);
        tes.addVertex(maxU, minV, 0);
        tes.addVertex(minU, minV, 0);
    }

    /**
     * One key, as a box standing out of the plate toward the typist.
     *
     * <p>Five faces rather than six: the back of the box is against the plate and can never be
     * seen, and leaving it out keeps a translucent box from being blended against itself.
     */
    private static void cap(Tessellator tes, Key key, boolean lit) {
        float face = lit ? DEPTH - SINK : DEPTH;
        float l = key.left;
        float r = key.left + key.width;
        float t = key.top;
        float b = key.top - KEY;

        if (lit) {
            tes.setColorRGBA_F(0.45F, 0.72F, 1.0F, 0.95F);
        } else {
            tes.setColorRGBA_F(0.22F, 0.22F, 0.26F, 0.90F);
        }
        tes.addVertex(l, t, face);
        tes.addVertex(r, t, face);
        tes.addVertex(r, b, face);
        tes.addVertex(l, b, face);

        // The sides, darker, which is the whole of the depth cue: a flat cap drawn on the plate
        // gives the player no way to see how far there is left to push.
        if (lit) {
            tes.setColorRGBA_F(0.22F, 0.38F, 0.55F, 0.95F);
        } else {
            tes.setColorRGBA_F(0.11F, 0.11F, 0.14F, 0.90F);
        }
        tes.addVertex(l, t, 0);
        tes.addVertex(r, t, 0);
        tes.addVertex(r, t, face);
        tes.addVertex(l, t, face);

        tes.addVertex(l, b, face);
        tes.addVertex(r, b, face);
        tes.addVertex(r, b, 0);
        tes.addVertex(l, b, 0);

        tes.addVertex(l, t, face);
        tes.addVertex(l, t, 0);
        tes.addVertex(l, b, 0);
        tes.addVertex(l, b, face);

        tes.addVertex(r, t, 0);
        tes.addVertex(r, t, face);
        tes.addVertex(r, b, face);
        tes.addVertex(r, b, 0);
    }

    private static void label(Minecraft mc, Key key, boolean lit) {
        if (key.label.isEmpty()) {
            return;
        }
        float face = lit ? DEPTH - SINK : DEPTH;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(key.left + key.width / 2F, key.top - KEY / 2F, face + 0.0005F);
            final float scale = 0.0016F;
            GL11.glScalef(scale, -scale, scale);
            int w = mc.fontRenderer.getStringWidth(key.label);
            mc.fontRenderer.drawString(key.label, -w / 2, -4, 0xFFFFFFFF);
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * One bar per tracked hand, reaching back from the point the keys are tested against.
     *
     * <p>Upstream's figures and upstream's arrangement: eighteen centimetres long, four wide,
     * two and a half deep and hanging entirely below the controller's axis, white for the main
     * hand and grey for the other. The end that matters is the near one, and it is placed at
     * {@link #tip} -- the same call {@link #keyUnder} is given -- so the two cannot drift apart,
     * however the room is scaled or rotated.
     *
     * <p>Not scaled by the world scale, unlike upstream's, because this board is not: its keys
     * are a fixed size in world units, so a finger that grew with the world would stop being one
     * key wide.
     */
    private static void drawFingers() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        try {
            Vec3 origin = VRRenderState.interpolatedPlayerPos;
            if (origin == null) {
                return;
            }
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);

            Tessellator tes = Tessellator.instance;
            tes.startDrawingQuads();
            for (int hand = 0; hand < 2; hand++) {
                if (!MCOpenVR.isControllerTracking(hand)) {
                    continue;
                }
                Vec3 tip = tip(hand);
                Vec3 dir = VR.roomScale().getControllerDir_World(hand);
                Vec3 hint = VR.roomScale().getCustomControllerVector(hand, UP);
                if (tip == null || dir == null || hint == null) {
                    continue;
                }
                // White for the main hand and grey for the other, which is the only thing
                // telling the player which of the two bars they are watching.
                float shade = hand == 0 ? 1.0F : 0.5F;
                tes.setColorRGBA_F(shade, shade, shade, 1.0F);
                finger(tes, Vec3Util.subtract(tip, origin), dir, hint);
            }
            tes.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void finger(Tessellator tes, Vec3 at, Vec3 dir, Vec3 hint) {
        Vec3 forward = dir.normalize();
        Vec3 side = cross(forward, hint).normalize();
        Vec3 lift = cross(side, forward);

        Vec3 toRight = Vec3Util.scale(side, FINGER_HALF_WIDTH);
        Vec3 toLeft = Vec3Util.scale(side, -FINGER_HALF_WIDTH);
        Vec3 down = Vec3Util.scale(lift, -FINGER_DROP);
        Vec3 back = Vec3Util.add(at, Vec3Util.scale(forward, -FINGER_LENGTH));

        Vec3 tipRightBottom = Vec3Util.add(at, Vec3Util.add(toRight, down));
        Vec3 tipRightTop = Vec3Util.add(at, toRight);
        Vec3 tipLeftBottom = Vec3Util.add(at, Vec3Util.add(toLeft, down));
        Vec3 tipLeftTop = Vec3Util.add(at, toLeft);
        Vec3 endRightBottom = Vec3Util.add(back, Vec3Util.add(toRight, down));
        Vec3 endRightTop = Vec3Util.add(back, toRight);
        Vec3 endLeftBottom = Vec3Util.add(back, Vec3Util.add(toLeft, down));
        Vec3 endLeftTop = Vec3Util.add(back, toLeft);

        quad(tes, tipRightBottom, tipRightTop, tipLeftTop, tipLeftBottom);
        quad(tes, endLeftBottom, endLeftTop, endRightTop, endRightBottom);
        quad(tes, endRightBottom, endRightTop, tipRightTop, tipRightBottom);
        quad(tes, tipLeftBottom, tipLeftTop, endLeftTop, endLeftBottom);
        quad(tes, tipLeftTop, tipRightTop, endRightTop, endLeftTop);
        quad(tes, endLeftBottom, endRightBottom, tipRightBottom, tipLeftBottom);
    }

    private static void quad(Tessellator tes, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        tes.addVertex(a.xCoord, a.yCoord, a.zCoord);
        tes.addVertex(b.xCoord, b.yCoord, b.zCoord);
        tes.addVertex(c.xCoord, c.yCoord, c.zCoord);
        tes.addVertex(d.xCoord, d.yCoord, d.zCoord);
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
