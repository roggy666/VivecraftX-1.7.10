package lol.gzmc.vivecraftx.util;

import lol.gzmc.vivecraftx.api.IPlayerItemInUseExt;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Replaces the {@code setItemInUseClient} / {@code setItemInUseCountClient} helpers Vivecraft's
 * patch added to {@code EntityPlayerSP}. The cast stays here instead of at every call site.
 */
public final class PlayerUtil {

    private PlayerUtil() {}

    public static void setItemInUse(EntityPlayer player, ItemStack stack) {
        if (player != null) {
            ((IPlayerItemInUseExt) player).vivecraftx$setItemInUse(stack);
        }
    }

    public static void setItemInUseCount(EntityPlayer player, int count) {
        if (player != null) {
            ((IPlayerItemInUseExt) player).vivecraftx$setItemInUseCount(count);
        }
    }
}
