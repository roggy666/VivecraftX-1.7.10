package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.render.VRModelPose;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the stretch of drawing that produces the figure in the inventory.
 *
 * <p>There is no other way to tell. The figure is the player entity drawn through the ordinary
 * entity renderer, so by the time a model is being posed nothing distinguishes it from the same
 * player being drawn in the world -- where the arms are already handled, and handled differently.
 *
 * <p>MCP leaves this one obfuscated, so it has to be named by its SRG name. It is the static that
 * draws a living entity into a rectangle of the screen, and both the survival inventory and the
 * creative one call it.
 */
@Mixin(GuiInventory.class)
public class MixinGuiInventory {

    @Inject(method = "func_147046_a", at = @At("HEAD"))
    private static void vivecraftx$beginDoll(int x, int y, int scale, float mouseX, float mouseY,
            EntityLivingBase entity, CallbackInfo ci) {
        VRModelPose.beginInventory();
    }

    @Inject(method = "func_147046_a", at = @At("RETURN"))
    private static void vivecraftx$endDoll(int x, int y, int scale, float mouseX, float mouseY,
            EntityLivingBase entity, CallbackInfo ci) {
        VRModelPose.endInventory();
    }
}
