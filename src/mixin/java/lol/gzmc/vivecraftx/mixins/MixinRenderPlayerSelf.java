package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.render.VRModelPose;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns a player's body the way their shoulders are turned, and keeps your own hands empty.
 *
 * <p>The renderer turns the whole model by the player's yaw before the model poses itself. In a
 * headset that field holds where the controller is pointing, which has nothing to do with which
 * way the chest faces -- so the body would face one way while the arms were placed as though it
 * faced another, and the arms would orbit it. Both are given the same number here instead, and
 * put back afterwards so nothing else sees the change.
 *
 * <p>For everyone in a headset, not only for oneself. This began as a fix for one's own body and
 * the reason it gives applies just as much to the people you can see: theirs is in fact worse,
 * because the body angle only eases towards the look angle while they are walking, so somebody
 * standing still and turning on the spot stays wrong indefinitely rather than catching up.
 *
 * <p>Your own hands are emptied because what they hold is already drawn at the controller, which
 * is where it really is. Drawn twice, the second copy is always the wrong one. Nobody else's are:
 * what they hold is drawn in the hand of the arm this has just placed, which is where it belongs.
 */
@Mixin(RenderPlayer.class)
public class MixinRenderPlayerSelf {

    @Unique
    private float vivecraftx$yaw;
    @Unique
    private float vivecraftx$prevYaw;
    @Unique
    private boolean vivecraftx$turned;

    // The overload that is actually called. Of the three, the two that take a wider type are
    // bridges the compiler generated, and both cast straight down to this one rather than to each
    // other -- so a hook on either of them is accepted without complaint and never runs.
    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V",
            at = @At("HEAD"))
    private void vivecraftx$faceTheRightWay(AbstractClientPlayer entity, double x, double y, double z,
            float yaw, float partialTicks, CallbackInfo ci) {
        this.vivecraftx$turned = false;
        Float body = VRModelPose.bodyYawFor(entity);
        if (body == null) {
            return;
        }
        this.vivecraftx$yaw = entity.renderYawOffset;
        this.vivecraftx$prevYaw = entity.prevRenderYawOffset;
        this.vivecraftx$turned = true;
        // Both, and the same, so that nothing is interpolated between two different bodies.
        entity.renderYawOffset = entity.prevRenderYawOffset = body.floatValue();
    }

    @Inject(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V",
            at = @At("RETURN"))
    private void vivecraftx$faceBack(AbstractClientPlayer entity, double x, double y, double z,
            float yaw, float partialTicks, CallbackInfo ci) {
        if (this.vivecraftx$turned) {
            entity.renderYawOffset = this.vivecraftx$yaw;
            entity.prevRenderYawOffset = this.vivecraftx$prevYaw;
            this.vivecraftx$turned = false;
        }
    }

    @Inject(method = "renderEquippedItems", at = @At("HEAD"), cancellable = true)
    private void vivecraftx$emptyOwnHands(AbstractClientPlayer player, float partialTicks,
            CallbackInfo ci) {
        if (player == Minecraft.getMinecraft().thePlayer && VRModelPose.inWorld()) {
            ci.cancel();
        }
    }
}
