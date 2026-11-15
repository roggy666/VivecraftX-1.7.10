package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.render.VRArmBones;
import lol.gzmc.vivecraftx.render.VRModelPose;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a figure stand the way the person it belongs to is standing.
 *
 * <p>After vanilla has posed everything, so this only overrides the parts a headset actually
 * knows about. Every biped goes through here, including the armour layers -- which is what is
 * wanted, since a helmet has to turn with the head -- so the decision about whether any of it
 * applies is made in one place.
 *
 * <p>At every return rather than at the tail, which is not the same thing when somebody else
 * has been here first: Backhand replaces this method outright and returns from the top, leaving
 * the original body and its return unreachable. A tail injection would land on that unreachable
 * return and silently never run.
 */
// Applied after Backhand's, which moves the off arm to hold its item: at the same injection
// point the later mixin runs last, and the arm belongs wherever the controller is. Backhand
// then places the item by reading the bone we left, so the item comes along.
@Mixin(value = ModelBiped.class, priority = 1500)
public abstract class MixinModelBiped {

    @Inject(method = "setRotationAngles", at = @At("RETURN"))
    private void vivecraftx$poseFromVR(float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scale, Entity entity, CallbackInfo ci) {
        VRModelPose.pose((ModelBiped) (Object) this, entity);
    }

    /**
     * Draws the two-piece arms, when this model has a pair.
     *
     * <p>At the end, because they stand in for arms the model has already been told to hide, and
     * because everything they need -- the skin, the lighting, the matrix -- is whatever the
     * model's own parts were just drawn with.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void vivecraftx$drawVRArms(Entity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        VRArmBones bones = VRModelPose.bonesFor((ModelBiped) (Object) this);
        if (bones != null) {
            bones.render(scale);
        }
    }
}
