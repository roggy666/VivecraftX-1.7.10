package lol.gzmc.vivecraftx.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import xonin.backhand.api.core.BackhandUtils;
import xonin.backhand.api.core.IBackhandPlayer;
import xonin.backhand.api.core.IOffhandInventory;

/**
 * The only place in this mod that names a Backhand type.
 *
 * <p>Kept separate from {@link BackhandCompat} on purpose: the class loader resolves the types a
 * class mentions when it loads the class, not when a method runs, so one import of a missing type
 * is enough to take down everything in the file. Nothing here is reached before Backhand is found.
 *
 * <p>Compiled against Backhand but not shipped with it -- the dependency is {@code compileOnly},
 * so the jar carries none of it and works without it.
 */
final class BackhandBridge {

    private BackhandBridge() {}

    /**
     * Whether this player really has a second hand.
     *
     * <p>Backhand adds its interfaces by rewriting vanilla classes, and a rewrite that did not
     * happen leaves the mod loaded and the hand missing. Asking the inventory whether it grew the
     * slot is the question that actually matters.
     */
    static boolean usable(EntityPlayer player) {
        if (player == null || !(player instanceof IBackhandPlayer)
                || !(player.inventory instanceof IOffhandInventory)) {
            return false;
        }
        // And that the slot is really there. Every call into Backhand casts without checking,
        // so an inventory that grew the interface but not the array throws from inside it.
        int slot = ((IOffhandInventory) player.inventory).backhand$getOffhandSlot();
        return slot > 0 && slot < player.inventory.mainInventory.length;
    }

    /**
     * Whether the server lets the off hand attack at all.
     *
     * <p>Off by default in Backhand, and enforced on the server rather than merely suggested:
     * the packet arrives and is dropped. Worth knowing before swinging, because otherwise the
     * arm moves, the controller buzzes, and nothing happens.
     */
    static boolean attackAllowed() {
        return xonin.backhand.utils.BackhandConfig.OffhandAttack;
    }

    /** The same for breaking blocks with the off hand, and off by default for the same reason. */
    static boolean miningAllowed() {
        return xonin.backhand.utils.BackhandConfig.OffhandBreakBlocks;
    }

    static ItemStack offhandItem(EntityPlayer player) {
        if (!usable(player)) {
            return null;
        }
        ItemStack stack = BackhandUtils.getOffhandItem(player);
        return stack != null && stack.getItem() != null ? stack : null;
    }

    /**
     * Runs something as though the off-hand item were the held one.
     *
     * <p>Backhand's own doing, and the reason this integration is short: it puts the off-hand
     * stack in the main slot, runs the action, puts everything back, and tells the server which
     * hand it was. Every piece of vanilla that only knows about one hand -- attacking, mining,
     * right-clicking -- therefore works on the off hand without knowing it.
     */
    static boolean withOffhand(EntityPlayer player, java.util.function.BooleanSupplier action) {
        return BackhandUtils.useOffhandItem(player, action);
    }

    /**
     * Whether the off-hand item is standing in for the held one at this moment.
     *
     * <p>While it is, the game's own swing lands on the off arm instead -- Backhand redirects it
     * there. So this is the question "which arm is about to move", asked the same way Backhand
     * answers it, rather than guessed from which key was pressed.
     */
    static boolean usingOffhand(EntityPlayer player) {
        return BackhandUtils.isUsingOffhand(player);
    }

    /** The off-hand swing animation, for the arm the player actually swung. */
    static void swingOffhand(EntityPlayer player) {
        ((IBackhandPlayer) player).swingOffItem();
    }

    /**
     * How far through its swing the off arm is, between frames.
     *
     * <p>The exact counterpart of the game's own {@code getSwingProgress}, interpolated the same
     * way, so a swing drawn from it is as smooth as the one the main hand gets.
     *
     * <p>Worth knowing: Backhand also makes the game's own {@code getSwingProgress} answer for
     * the off hand while the off-hand item is the held one. That is only true inside the scope
     * that puts it there, and drawing is not inside it -- but it is why these two must never be
     * called from within {@link #withOffhand}.
     */
    static float offhandSwing(EntityPlayer player, float partialTicks) {
        return ((IBackhandPlayer) player).getOffSwingProgress(partialTicks);
    }
}
