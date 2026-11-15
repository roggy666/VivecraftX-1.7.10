package lol.gzmc.vivecraftx.compat;

import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;

/**
 * Optional second hand, through Backhand (modid {@code backhand}).
 *
 * <p>1.7.10 has no off-hand. Backhand gives it one -- a slot, a held item, and the networking to
 * use what is in it -- and that is exactly the piece a headset makes conspicuous by its absence:
 * you are holding two controllers and the game only believes in one of them.
 *
 * <p>A soft dependency, in the strict sense: without Backhand nothing here runs and nothing else
 * changes, and with it the second hand is live without the player configuring anything.
 *
 * <p>This class imports nothing from Backhand, so it is always safe to load. Everything that
 * touches Backhand lives in {@link BackhandBridge}, which is only ever reached once the mod has
 * been found -- a class that names a type the loader cannot resolve fails when it is loaded, not
 * when the method is called, so the two have to be kept apart.
 */
public final class BackhandCompat {

    private BackhandCompat() {}

    public static final String MODID = "backhand";

    /**
     * Marker class, present exactly when Backhand is on the classpath.
     *
     * <p>From the GTNewHorizons line specifically. The older build on Modrinth has a different
     * API under the same package, and naming a class only that line has is what tells them
     * apart before anything tries to call one.
     */
    private static final String MARKER = "xonin.backhand.api.core.IOffhandInventory";

    private static boolean resolved;
    private static boolean present;

    /** True when Backhand is installed and its API answered. Resolved once, then cached. */
    public static boolean isPresent() {
        if (!resolved) {
            resolved = true;
            try {
                present = Loader.isModLoaded(MODID) && Class.forName(MARKER) != null;
            } catch (Throwable ignored) {
                present = false;
            }
            ViveCraftX.LOG.info("Backhand {}", present ? "detected - the off hand is live"
                                                       : "absent - one hand holds things");
        }
        return present;
    }

    /**
     * What is in this player's off hand, or null for an empty hand or no Backhand.
     *
     * <p>Null rather than an exception, because every caller wants the same thing from a player
     * who is not holding anything in their left hand as from one playing without the mod.
     */
    public static ItemStack offhandItem(EntityPlayer player) {
        if (player == null || !isPresent()) {
            return null;
        }
        try {
            return BackhandBridge.offhandItem(player);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("Could not read the off-hand item", t);
            return null;
        }
    }

    /** True when this player has a working second hand to act with. */
    public static boolean hasOffhand(EntityPlayer player) {
        if (player == null || !isPresent()) {
            return false;
        }
        try {
            return BackhandBridge.usable(player);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Does something with the off-hand item rather than the held one.
     *
     * <p>Falls through to doing it with the held item when there is no second hand, which is
     * what every caller wants: without the mod the off hand is simply the hand.
     */
    public static boolean withOffhand(EntityPlayer player,
            java.util.function.BooleanSupplier action) {
        if (!hasOffhand(player)) {
            return action.getAsBoolean();
        }
        try {
            return BackhandBridge.withOffhand(player, action);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("Acting with the off hand failed", t);
            return false;
        }
    }

    /**
     * Whether hitting something with the off hand will do anything.
     *
     * <p>Backhand ships with off-hand attacking and off-hand mining turned off, and the server
     * drops the packet rather than refusing it, so the only sign of it is that nothing happens.
     * Said once in the log when it comes up, rather than left as a mystery.
     */
    public static boolean offhandAttackAllowed(EntityPlayer player) {
        if (!hasOffhand(player)) {
            return false;
        }
        try {
            if (BackhandBridge.attackAllowed()) {
                return true;
            }
        } catch (Throwable t) {
            return false;
        }
        warnOnce("attack", "Backhand is set not to let the off hand attack. Swinging it will do"
                + " nothing until \"OffhandAttack\" is turned on in config/backhand.cfg.");
        return false;
    }

    /** The same for breaking blocks with it. */
    public static boolean offhandMiningAllowed(EntityPlayer player) {
        if (!hasOffhand(player)) {
            return false;
        }
        try {
            if (BackhandBridge.miningAllowed()) {
                return true;
            }
        } catch (Throwable t) {
            return false;
        }
        warnOnce("mine", "Backhand is set not to let the off hand break blocks. Mining with it"
                + " will do nothing until \"OffhandBreakBlocks\" is turned on in"
                + " config/backhand.cfg.");
        return false;
    }

    private static final java.util.Set<String> SAID = new java.util.HashSet<String>();

    private static void warnOnce(String key, String message) {
        if (SAID.add(key)) {
            ViveCraftX.LOG.info(message);
        }
    }

    /**
     * Which hand the game's own swing is about to land on: zero for the main one, one for the
     * other.
     *
     * <p>Only ever one when the off-hand mod has put the off-hand item in the held slot, which
     * is exactly when it will also redirect the swing. Without the mod it is always zero, which
     * is the whole truth on a version that has one hand.
     */
    public static int actingHand(EntityPlayer player) {
        if (!hasOffhand(player)) {
            return 0;
        }
        try {
            return BackhandBridge.usingOffhand(player) ? 1 : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** How far through its swing the off arm is, or zero when there is no off arm. */
    public static float offhandSwing(EntityPlayer player, float partialTicks) {
        if (!hasOffhand(player)) {
            return 0F;
        }
        try {
            return BackhandBridge.offhandSwing(player, partialTicks);
        } catch (Throwable t) {
            return 0F;
        }
    }

    /** Swings the off arm, so other people see which hand did it. */
    public static void swingOffhand(EntityPlayer player) {
        if (!hasOffhand(player)) {
            return;
        }
        try {
            BackhandBridge.swingOffhand(player);
        } catch (Throwable t) {
            ViveCraftX.LOG.debug("Swinging the off hand failed", t);
        }
    }
}
