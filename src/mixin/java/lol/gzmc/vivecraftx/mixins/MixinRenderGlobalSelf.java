package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.render.VRModelPose;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets you see your own body.
 *
 * <p>The game draws every entity except the one the camera is inside, which on a monitor is right
 * -- the camera is in the head, and a head seen from inside is a wall. In a headset it is the
 * single largest thing missing: you look down and there is nothing there, and the two hands float
 * because they are attached to nobody.
 *
 * <p>The skip is one condition with three terms, and the third is "unless they are asleep". That
 * term is the way in: answering yes to it draws the body without touching the other two, which is
 * how Vivecraft has done it since 1.12. The head is hidden separately, where the model is posed.
 */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobalSelf {

    @Redirect(
            method = "renderEntities",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/entity/EntityLivingBase;isPlayerSleeping()Z"))
    private boolean vivecraftx$drawOwnBody(EntityLivingBase view) {
        if (view.isPlayerSleeping()) {
            // Actually asleep. Then the camera is not where the body is, and drawing it would be
            // an out-of-body experience rather than a pair of legs.
            return true;
        }
        return VRModelPose.inWorld();
    }
}
