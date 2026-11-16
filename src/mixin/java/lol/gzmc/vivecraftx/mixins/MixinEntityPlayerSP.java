package lol.gzmc.vivecraftx.mixins;

import java.util.Random;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRState;
import lol.gzmc.vivecraftx.ViveCraftX;
import lol.gzmc.vivecraftx.api.IPlayerVRExt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-player VR state, and the per-tick room-scale update.
 *
 * <p>The companion is {@code MixinEntityRoomScale}, which has to sit on {@code Entity} because
 * vanilla declares none of the position methods here. This one is for what {@code EntityPlayerSP}
 * does declare.
 */
@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP implements IPlayerVRExt {

    /**
     * Supplies the randomness the room-scale update wants.
     *
     * <p>Vivecraft handed it the entity's own {@code rand}, which is declared on {@code Entity}
     * and so cannot be shadowed from a mixin on {@code EntityPlayerSP}. Our own generator is
     * equivalent here: every use of it is the pitch wobble on a footstep or the scatter on a
     * particle, all client-side cosmetics with nothing synchronised or replayed depending on the
     * sequence.
     */
    @Unique
    private static final Random vivecraftx$RAND = new Random();

    /**
     * Room-scale movement, once per tick: the player's own feet moving them about the room, the
     * teleport arc, and climbing.
     *
     * <p>Placed straight after the player's own living update, where Vivecraft had it, because it
     * reads the position and motion that update just produced.
     *
     * <p>The call is named on {@code AbstractClientPlayer} rather than on {@code EntityPlayer},
     * which is where the method is actually written. A {@code super} call carries the direct
     * superclass as its owner whether or not that class declares the method, and an injection
     * point compares the owner exactly -- so naming the declaring class matched nothing at all,
     * silently, and every physical gesture in the mod was dead: walking about the room, jumping,
     * climbing, rowing, the backpack, raising food to your mouth. Verified by disassembling the
     * class rather than reasoned about, because this is precisely the kind of thing that reads
     * correctly and is not.
     */
    @Inject(
            method = "onLivingUpdate",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/entity/AbstractClientPlayer;onLivingUpdate()V",
                     shift = At.Shift.AFTER),
            require = 1)
    private void vivecraftx$roomScaleTick(CallbackInfo ci) {
        if (!VRState.running() || VR.player() == null) {
            return;
        }
        EntityPlayerSP self = (EntityPlayerSP) (Object) this;

        // Everything physical -- walking about the room, jumping, climbing, raising food to your
        // mouth -- waits for the server to say where the player is, because acting on a position
        // the server has not confirmed moves them somewhere the server will only move them back
        // from. That confirmation arrives as a position packet, and this is a second line in case
        // it is ever missed: a player who has been alive in a loaded world for a second is in a
        // known place whether or not we saw the packet that said so, and without this every
        // gesture in the mod stays silently dead for the rest of the session.
        if (!this.vivecraftx$initFromServer && self.ticksExisted > 20
                && Minecraft.getMinecraft().theWorld != null) {
            this.vivecraftx$initFromServer = true;
            VR.player().snapRoomOriginToPlayerEntity(self, true, false, 1);
            ViveCraftX.LOG.warn("No position packet was seen; starting room-scale anyway after"
                    + " {} ticks in the world", self.ticksExisted);
        }

        VR.player().runPhysicalTick(self, Minecraft.getMinecraft(), vivecraftx$RAND,
                "the player's living update", false);
    }

    @Unique
    private boolean vivecraftx$initFromServer = false;

    @Unique
    private int vivecraftx$movementTeleportTimer = 0;

    @Override
    public boolean vivecraftx$isInitFromServer() {
        return this.vivecraftx$initFromServer;
    }

    @Override
    public void vivecraftx$setInitFromServer(boolean value) {
        this.vivecraftx$initFromServer = value;
    }

    @Override
    public int vivecraftx$getMovementTeleportTimer() {
        return this.vivecraftx$movementTeleportTimer;
    }

    @Override
    public void vivecraftx$setMovementTeleportTimer(int value) {
        this.vivecraftx$movementTeleportTimer = value;
    }
}
