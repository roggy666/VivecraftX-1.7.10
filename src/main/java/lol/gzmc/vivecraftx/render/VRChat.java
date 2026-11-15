package lol.gzmc.vivecraftx.render;

import com.mtbs3d.minecrift.settings.VRSettings;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;

/**
 * Where the incoming chat sits on the interface panel, and how long it stays there.
 *
 * <p>No version of Vivecraft ever gave chat a surface of its own: incoming messages are drawn by
 * vanilla {@code GuiNewChat.drawChat} into the same framebuffer as the rest of the interface, and
 * that framebuffer becomes the panel. That is the right design and this does not change it. What
 * it changes is a position vanilla chose for a monitor.
 *
 * <p>Flat, chat belongs hard against the bottom-left corner: out of the way of everything that
 * matters, and still inside the same rectangle as the player's eyes. On a panel strapped to an arm
 * that corner is not out of the way, it is out of sight. The panel is {@code QUAD_WIDTH} metres
 * across before its own scale, which in wrist mode is sixty centimetres, and it lies flat along
 * the forearm with its middle thirteen centimetres behind the controller -- so the arm itself only
 * covers the middle third of it. Vanilla's hotbar sits in that third, which is why reaching out
 * and touching the hotbar works at all. Chat starts two pixels from the left edge, which is forty
 * centimetres behind the controller: past the elbow, beside the player's own shoulder, lying flat
 * and facing the ceiling. Nothing is wrong with how it is drawn; it is drawn somewhere nobody can
 * look.
 *
 * <p>So in the two modes that hang the panel off a controller, the chat block is moved to start
 * where the hotbar starts. That number is vanilla's own -- ninety-one interface pixels left of the
 * middle -- which is what keeps the two in step and puts the chat directly above the one element
 * already known to land on the arm.
 *
 * <p>In the head-locked and seated modes the panel is a screen standing in front of the face and
 * the corner is exactly right, so nothing moves. Neither does anything move while a screen is
 * open: {@code MCOpenVR.onGuiScreenChanged} has already parked the panel within reading distance,
 * which is why chat has always been legible with the chat screen up and only then.
 */
public final class VRChat {

    private VRChat() {}

    /** Where vanilla starts the hotbar, measured from the middle of the interface. */
    private static final int HOTBAR_LEFT_FROM_MIDDLE = 91;

    /** The indent {@code drawChat} applies on top of whatever position it is given. */
    private static final int CHAT_INDENT = 2;

    /** The padding {@code drawChat} adds to the right of the widest line it draws. */
    private static final int CHAT_PADDING = 4;

    /**
     * Where the chat block should start, in interface pixels.
     *
     * @param scaledWidth the panel's width in interface pixels
     * @param flatLeft    where it would start flat, which is also whatever another mod may have
     *                    asked for, and what comes back unchanged whenever there is no reason to
     *                    move it
     */
    public static int blockLeft(int scaledWidth, int flatLeft) {
        if (!VRState.running()) {
            return flatLeft;
        }
        VRSettings settings = VR.settings();
        Minecraft mc = Minecraft.getMinecraft();
        if (settings == null || mc == null || mc.currentScreen != null || mc.ingameGUI == null) {
            return flatLeft;
        }
        // The same two modes placeHud attaches to a controller, named rather than inferred from
        // "not head", because every mode it does not recognise falls through to head-locked there
        // and the two have to keep agreeing about which panel is which.
        if (settings.seated || (settings.vrHudLockMode != VRSettings.HUD_LOCK_HAND
                && settings.vrHudLockMode != VRSettings.HUD_LOCK_WRIST)) {
            return flatLeft;
        }

        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        if (chat == null) {
            return flatLeft;
        }

        int left = scaledWidth / 2 - HOTBAR_LEFT_FROM_MIDDLE - CHAT_INDENT;
        // How wide the block will actually be, asked of the chat rather than assumed, so a player
        // who widens the chat does not push it off the far edge of the panel.
        int block = chat.func_146228_f() + CHAT_PADDING;
        left = Math.min(left, scaledWidth - block);
        // A chat wider than the panel ends up back where it started, which is at least no worse
        // than it was.
        return Math.max(flatLeft, left);
    }

    /**
     * The counter {@code drawChat} should age its lines against.
     *
     * <p>Vanilla fades a line out ten seconds after it arrives, which assumes the player was
     * looking at the screen when it did. In a headset they were probably looking at the world, and
     * a message that has already gone by the time they glance at their wrist may as well never
     * have been sent. So the player can ask for the latest lines to stay.
     *
     * <p>It is done by lying about the clock rather than by reimplementing the chat:
     * {@code drawChat} ages each line by subtracting the counter the line was stamped with from
     * the one it is handed, so handing it a counter that predates every line makes every line read
     * as having just arrived. How many lines then stay is the chat's own unfocused height, and
     * older ones leave the way they always did -- pushed off the end by newer ones.
     *
     * @param live the counter vanilla would have used
     */
    public static int fadeCounter(int live) {
        if (!VRState.running() || VR.settings() == null || VR.settings().chatFadeAway) {
            return live;
        }
        // Zero rather than any other small number because no line can carry a counter below it, so
        // this cannot accidentally age one. With the chat screen open vanilla forces every line to
        // full opacity and stops reading the counter at all, so this is a no-op there as well.
        return 0;
    }
}
