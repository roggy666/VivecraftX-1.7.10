package lol.gzmc.vivecraftx.net;

import java.io.File;

import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraftforge.common.config.Configuration;

/**
 * What a server lets VR players do.
 *
 * <p>Teleporting is movement the client decides on and the server is told about afterwards,
 * which makes it exactly the kind of thing an administrator may want a say in: it crosses ground
 * other players have to walk, and on a server with any competitive element that is not a small
 * difference. So it is a server setting, and the server's answer wins over the client's.
 *
 * <p>Everything defaults to permitting it, because a player who needs teleporting usually needs
 * it to be able to play at all rather than to gain anything.
 */
public final class VRServerConfig {

    private VRServerConfig() {}

    public static boolean teleportAllowed = true;
    public static boolean teleportCostsFood;
    public static boolean teleportLimitsForced;
    public static int teleportUpLimit = 1;
    public static int teleportDownLimit = 4;
    public static int teleportHorizLimit = 16;

    public static void load(File file) {
        try {
            Configuration config = new Configuration(file);
            config.load();
            final String teleport = "teleport";

            teleportAllowed = config.get(teleport, "allowed", true,
                    "Whether VR players may teleport at all. Players prone to VR sickness often\n"
                    + "cannot play without it, so turning this off will exclude some people.")
                    .getBoolean();
            teleportCostsFood = config.get(teleport, "costsFood", false,
                    "Whether teleporting upwards makes a player hungry, the way jumping does.")
                    .getBoolean();
            teleportLimitsForced = config.get(teleport, "forceLimits", false,
                    "Whether the distances below replace whatever the player has set.\n"
                    + "Off means the player's own limits apply, which they may set to anything.")
                    .getBoolean();
            teleportUpLimit = config.get(teleport, "upLimit", 1,
                    "How far up a teleport may carry a player, in blocks.").getInt();
            teleportDownLimit = config.get(teleport, "downLimit", 4,
                    "How far down a teleport may drop a player, in blocks.").getInt();
            teleportHorizLimit = config.get(teleport, "horizontalLimit", 16,
                    "How far along the ground a teleport may carry a player, in blocks.")
                    .getInt();

            if (config.hasChanged()) {
                config.save();
            }
        } catch (Throwable t) {
            // A server that cannot read its own config should still run, with the defaults,
            // rather than refuse to start over a VR setting.
            ViveCraftX.LOG.warn("Could not read the VR server config; using defaults", t);
        }
    }
}
