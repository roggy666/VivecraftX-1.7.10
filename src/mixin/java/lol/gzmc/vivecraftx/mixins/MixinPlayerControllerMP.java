package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.VRSwing;
import lol.gzmc.vivecraftx.compat.BackhandCompat;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes that the hand is being used on something, so that the swing that follows looks like a
 * push rather than a chop.
 *
 * <p>This is the one place worth asking, rather than the obvious one. In this version a
 * right-click only ever swings the arm in a single case -- a successful click on a block -- and
 * that case is exactly this call returning true. Using something in the air or on a creature does
 * not swing at all, so there is nothing to label.
 *
 * <p>The obvious place, the method that reads the button, cannot be used: the off-hand mod
 * replaces that method whole, and nothing may be hooked inside a body another mod has written.
 * This call survives that replacement because the replacement makes it too -- and it is the
 * better place anyway, since the off-hand mod has by this point already put the off hand
 * forward, so the right hand gets credited for the swing.
 */
@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMP {

    @Inject(method = "onPlayerRightClick", at = @At("HEAD"))
    private void vivecraftx$usingSomething(EntityPlayer player, World world, ItemStack held,
            int x, int y, int z, int side, net.minecraft.util.Vec3 hit,
            CallbackInfoReturnable<Boolean> cir) {
        VRSwing.began(BackhandCompat.actingHand(player), VRSwing.Kind.USE);
    }
}
