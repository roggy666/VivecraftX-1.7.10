package lol.gzmc.vivecraftx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

/**
 * Chat output for VR notices.
 *
 * <p>Replaces {@code Minecraft.printChatMessage(String)}, which the old patches added to vanilla.
 * Same behaviour: silently does nothing when no world is loaded.
 */
public final class ChatUtil {

    private ChatUtil() {}

    public static void print(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.theWorld != null && mc.ingameGUI != null) {
            mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(msg));
        }
    }
}
