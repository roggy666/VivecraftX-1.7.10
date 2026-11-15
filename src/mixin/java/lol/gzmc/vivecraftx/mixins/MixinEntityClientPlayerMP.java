package lol.gzmc.vivecraftx.mixins;

import java.util.Random;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The second way into the per-tick physical update.
 *
 * <p>The first is on {@code EntityPlayerSP}, straight after the player's own living update, where
 * Vivecraft put it and where it belongs: it reads the position and motion that update has just
 * produced. In this pack that hook never fires -- the injection applies without complaint and the
 * method it sits in is never reached -- and everything physical hangs off it: walking about the
 * room, jumping, climbing, swimming, the backpack, and raising food to your mouth. All of it was
 * silently dead, with nothing said.
 *
 * <p>So this hooks the class the player actually is, rather than the one it inherits from, at the
 * end of its own update. Whichever of the two reaches a given tick first does the work and the
 * other stands down, so having both changes nothing when the first one works.
 *
 * <p>A tick late relative to the intended point in the worst case, which for a gesture measured
 * against where your hands are is not a difference anyone can see.
 */
@Mixin(EntityClientPlayerMP.class)
public abstract class MixinEntityClientPlayerMP {

    @Unique
    private static final Random vivecraftx$RAND = new Random();

    @Inject(method = "onUpdate", at = @At("TAIL"))
    private void vivecraftx$physicalTick(CallbackInfo ci) {
        if (!VRState.running() || VR.player() == null) {
            return;
        }
        Object self = this;
        if (self != Minecraft.getMinecraft().thePlayer) {
            return;
        }
        VR.player().runPhysicalTick((EntityPlayerSP) self, Minecraft.getMinecraft(),
                vivecraftx$RAND, "the client player's own update", true);
    }
}
