package lol.gzmc.vivecraftx.net;

import lol.gzmc.vivecraftx.VR;

/**
 * What the server this client is on allows, as the server last said.
 *
 * <p>Until a server says otherwise, nothing is restricted and nothing is assumed to be helping:
 * most servers do not have this mod, and a client that waited for permission would simply never
 * be able to teleport anywhere.
 */
public final class VRServerRules {

    private VRServerRules() {}

    /** True once a server with this mod has introduced itself. */
    private static boolean serverHasMod;

    private static boolean teleportAllowed = true;
    private static boolean limitsForced;
    private static int upLimit = 1;
    private static int downLimit = 4;
    private static int horizLimit = 16;

    /** Said once, so a player who cannot teleport finds out why rather than wondering. */
    private static boolean toldAboutTeleport;
    private static boolean toldAboutRange;

    public static void clear() {
        serverHasMod = false;
        teleportAllowed = true;
        limitsForced = false;
        toldAboutTeleport = false;
        toldAboutRange = false;
    }

    static void accept(boolean teleport, boolean forced, int up, int down, int horiz) {
        serverHasMod = true;
        teleportAllowed = teleport;
        limitsForced = forced;
        upLimit = up;
        downLimit = down;
        horizLimit = horiz;
    }

    /**
     * Whether the server will move the player itself when asked.
     *
     * <p>This decides whether a teleport is sent as a request or performed locally, and the
     * difference is not cosmetic: a player who moves more than ten blocks in one tick on their
     * own is snapped back by any vanilla server, because that is what moving ten blocks in a
     * tick usually means.
     */
    public static boolean serverMovesUs() {
        return serverHasMod && teleportAllowed;
    }

    public static boolean teleportAllowed() {
        return teleportAllowed;
    }

    /** Said once per server, in the chat, because silence here looks like a broken mod. */
    public static boolean shouldExplainRefusal() {
        if (teleportAllowed || toldAboutTeleport) {
            return false;
        }
        toldAboutTeleport = true;
        return true;
    }

    /**
     * Whether the player should be told that teleports are being kept short here.
     *
     * <p>Only when it is true, and only once: on a server without this mod a teleport further
     * than nine blocks is undone by the server's own cheat check, so the range is held below
     * that, and a range that feels short for no stated reason reads as a broken mod.
     */
    public static boolean shouldWarnAboutRange() {
        if (serverMovesUs() || isSinglePlayer() || toldAboutRange) {
            return false;
        }
        toldAboutRange = true;
        return true;
    }

    public static int upLimit() {
        return limitsForced ? upLimit : VR.settings().vrTeleportUpLimit;
    }

    public static int downLimit() {
        return limitsForced ? downLimit : VR.settings().vrTeleportDownLimit;
    }

    /**
     * How far along a teleport may go.
     *
     * <p>On a server that is not moving us, this is also capped at something a vanilla server
     * will accept. Nine blocks rather than the ten it actually checks, because the check is
     * against the whole movement for the tick and the player may also have been walking.
     */
    public static int horizLimit() {
        int limit = limitsForced ? horizLimit : VR.settings().vrTeleportHorizLimit;
        if (!serverMovesUs() && !isSinglePlayer()) {
            limit = Math.min(limit, 9);
        }
        return limit;
    }

    private static boolean isSinglePlayer() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        return mc.isSingleplayer();
    }
}
