package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.api.IPlayerItemInUseExt;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Opens up the player's item-in-use state for the VR trackers.
 *
 * <p>Targets {@code EntityPlayer} rather than {@code EntityPlayerSP}, because that is where the
 * two fields are declared, and a mixin cannot shadow a superclass's private members.
 */
@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer implements IPlayerItemInUseExt {

    @Shadow
    private ItemStack itemInUse;

    @Shadow
    private int itemInUseCount;

    @Override
    public void vivecraftx$setItemInUse(ItemStack stack) {
        this.itemInUse = stack;
    }

    @Override
    public void vivecraftx$setItemInUseCount(int count) {
        this.itemInUseCount = count;
    }
}
