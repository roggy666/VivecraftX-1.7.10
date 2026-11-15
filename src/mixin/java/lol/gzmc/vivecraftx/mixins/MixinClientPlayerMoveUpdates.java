package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.util.VanillaExt;

import net.minecraft.client.entity.EntityClientPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds back the player's own movement report for the tick they were teleported.
 *
 * <p>A teleport on a server with this mod is carried out by the server, which then tells the
 * client where it put them. If the client also reported the move itself, the server would see a
 * jump of more than ten blocks and snap the player back -- that check exists for good reason and
 * cannot simply be disabled, so the client stays quiet for the one tick instead.
 *
 * <p>Exactly one tick. The next one reports from the new position, which the server already
 * agrees with.
 */
@Mixin(EntityClientPlayerMP.class)
public abstract class MixinClientPlayerMoveUpdates {

    @Inject(method = "sendMotionUpdates", at = @At("HEAD"), cancellable = true)
    private void vivecraftx$quietAfterTeleport(CallbackInfo ci) {
        if (VRState.running() && VanillaExt.takeSuppressMoveUpdate()) {
            ci.cancel();
        }
    }
}
