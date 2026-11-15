package lol.gzmc.vivecraftx.api;

import net.minecraft.item.ItemStack;

/**
 * Write access to the player's item-in-use state, which the bow and eating trackers drive.
 *
 * <p>{@code itemInUse} and {@code itemInUseCount} are private on {@code EntityPlayer}. Vivecraft's
 * patch reached them with cached {@link java.lang.reflect.Field} handles and silently did nothing
 * when the lookup failed; a mixin gets there directly and cannot fail at runtime.
 */
public interface IPlayerItemInUseExt {

    void vivecraftx$setItemInUse(ItemStack stack);

    void vivecraftx$setItemInUseCount(int count);
}
