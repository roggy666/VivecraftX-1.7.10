package lol.gzmc.vivecraftx;

import java.util.List;

import lol.gzmc.vivecraftx.api.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * What the player is pointing at.
 *
 * <p>Vanilla works this out from where the player entity is facing, which in VR is where the body
 * is pointed, not where the hand is. Without this the player mines the block in front of their
 * chest while aiming at something else entirely, which is the single thing that makes VR feel
 * broken rather than merely rough.
 *
 * <p>The replacement traces from the controller along its own direction. It also keeps the point
 * the trace ended at, hit or miss, because the crosshair is drawn in the world at that point
 * rather than on the interface -- a crosshair fixed to a panel tells you nothing about where you
 * are aiming.
 */
public final class VRAim {

    private VRAim() {}

    /** How far the crosshair is tracked when nothing was hit, in blocks. */
    public static final float MAX_CROSSHAIR_DISTANCE = 64F;

    /** Where the aim ray ended this frame: the thing hit, or the far end of the trace. */
    public static Vec3 crossVec;

    /**
     * Replaces vanilla's pick with one from the controller.
     *
     * <p>Reach is measured from the controller rather than the head, so leaning in with the hand
     * extends it as you would expect. Entities are checked after blocks and only within whatever
     * distance the block trace reached, so you cannot hit a mob through a wall.
     */
    public static void getPointedBlock(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderViewEntity == null || mc.theWorld == null || VR.roomScale() == null
                || VR.player() == null) {
            return;
        }

        mc.pointedEntity = null;
        double blockReach = VR.player().worldScale * mc.playerController.getBlockReachDistance();
        double entityReach = mc.playerController.getBlockReachDistance();

        Vec3 aimSource = VR.roomScale().getControllerPos_World(0);
        Vec3 aim = VR.roomScale().getControllerMainDir_World();
        if (aimSource == null || aim == null) {
            return;
        }
        // Vec3 in this version is mutable and several of its methods return a new one, so the
        // origin is copied before anything is allowed to touch it.
        Vec3 origin = Vec3.createVectorHelper(aimSource.xCoord, aimSource.yCoord, aimSource.zCoord);

        Vec3 endPos = origin.addVector(aim.xCoord * blockReach, aim.yCoord * blockReach,
                aim.zCoord * blockReach);
        crossVec = endPos;
        // The five-argument trace: MCP 12 leaves this one obfuscated, and the three-argument
        // form will not do, because the last flag is what makes the trace report the last block
        // it passed through rather than nothing at all. Without it the crosshair has nowhere to
        // land when you aim at a non-solid block.
        mc.objectMouseOver = mc.theWorld.func_147447_a(
                Vec3.createVectorHelper(origin.xCoord, origin.yCoord, origin.zCoord), endPos,
                false, false, true);

        double maxReach;
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
            // Nothing in reach, but the crosshair still has to land somewhere, so the trace is
            // repeated out to the crosshair's own range. Without this the crosshair would sit at
            // arm's length in mid-air whenever you are not pointing at anything.
            Vec3 farEnd = origin.addVector(aim.xCoord * MAX_CROSSHAIR_DISTANCE,
                    aim.yCoord * MAX_CROSSHAIR_DISTANCE, aim.zCoord * MAX_CROSSHAIR_DISTANCE);
            MovingObjectPosition far = mc.theWorld.func_147447_a(
                    Vec3.createVectorHelper(origin.xCoord, origin.yCoord, origin.zCoord), farEnd,
                    false, false, true);
            crossVec = far != null ? far.hitVec
                    : Vec3.createVectorHelper(farEnd.xCoord, farEnd.yCoord, farEnd.zCoord);
            maxReach = crossVec.distanceTo(origin);
            mc.objectMouseOver = null;
        } else {
            maxReach = mc.objectMouseOver.hitVec.distanceTo(origin);
            crossVec = mc.objectMouseOver.hitVec;
        }

        pickEntity(mc, origin, maxReach, entityReach);
    }

    /**
     * Looks for an entity in front of the hand, nearer than whatever the block trace stopped at.
     *
     * <p>Bounded by the block hit rather than by reach alone, so a mob behind a wall is not
     * pickable through it.
     */
    private static void pickEntity(Minecraft mc, Vec3 origin, double maxReach,
            double entityReach) {
        Entity pointed = null;
        Vec3 hitLocation = null;
        double reach = maxReach;
        double dist = 0;

        AxisAlignedBB search = AxisAlignedBB
                .getBoundingBox(-maxReach, -maxReach, -maxReach, maxReach, maxReach, maxReach)
                .offset(origin.xCoord, origin.yCoord, origin.zCoord)
                .expand(1, 1, 1);

        List<?> candidates =
                mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.renderViewEntity, search);

        for (Object candidate : candidates) {
            Entity entity = (Entity) candidate;
            if (!entity.canBeCollidedWith()) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.boundingBox.expand(border, border, border);
            MovingObjectPosition collision = box.calculateIntercept(origin, crossVec);

            if (box.isVecInside(origin)) {
                // standing inside the thing: nothing can be nearer than that
                if (reach >= 0.0D) {
                    pointed = entity;
                    hitLocation = collision == null ? origin : collision.hitVec;
                    reach = 0.0D;
                }
            } else if (collision != null) {
                dist = origin.distanceTo(collision.hitVec);
                if (dist < maxReach || reach == 0.0D) {
                    // The thing you are riding is not a target, or you could not interact with
                    // anything past it; Forge lets an entity opt back in.
                    boolean riding = entity == mc.renderViewEntity.ridingEntity;
                    if (riding && !entity.canRiderInteract()) {
                        if (reach == 0.0D) {
                            pointed = entity;
                            hitLocation = collision.hitVec;
                        }
                    } else {
                        pointed = entity;
                        hitLocation = collision.hitVec;
                    }
                }
            }
        }

        if (pointed != null && hitLocation != null) {
            crossVec = hitLocation;
            if (dist < entityReach) {
                mc.objectMouseOver = new MovingObjectPosition(pointed, hitLocation);
                crossVec = mc.objectMouseOver.hitVec;
                // only these two get the name plate and the interaction prompt
                if (pointed instanceof EntityLivingBase || pointed instanceof EntityItemFrame) {
                    mc.pointedEntity = pointed;
                }
            }
        }
    }

    /**
     * Turns the player entity to face where the hand is aiming.
     *
     * <p>Far more than cosmetic. The entity's facing is what the rest of the game answers "which
     * way is the player looking" with, and two of those answers are drawn every frame: particles
     * and dropped items are flat sprites turned to face the camera, and the turn is computed from
     * the entity's yaw and pitch rather than from the camera. Leave the entity facing wherever the
     * mouse last left it and those sprites are seen edge-on, which is particles vanishing
     * completely and dropped items looking like flat cut-outs from a much older game.
     *
     * <p>It is also what the server is told, so it decides which way arrows fly and what other
     * players see you looking at.
     *
     * <p>Normally the entity looks at whatever the crosshair is on, which keeps the hand and the
     * body agreeing. A few things override that because they are aimed rather than pointed at: a
     * drawn bow follows the arrow, a thrown item follows the hand that throws it, and a mounted
     * player steers with the off hand.
     */
    public static void updatePlayerOrientation() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || VR.roomScale() == null) {
            return;
        }

        if (crossVec != null) {
            // Vec3.subtract in this version returns its argument minus the receiver, so this
            // reads as the vector from the player to the crosshair, which is what it is.
            Vec3 toCrosshair = VRRenderState.interpolatedPlayerPos.subtract(crossVec);
            lookAlong(player, toCrosshair);
        }

        if (player.isRiding()) {
            // Steering is done with the off hand, and the server needs to be told which way.
            player.rotationYaw = player.rotationYawHead =
                    VR.roomScale().getControllerOffhandYaw_World();
            player.rotationPitch = -VR.roomScale().getControllerOffhandPitch_World();
            return;
        }

        ItemStack held = player.inventory.getCurrentItem();
        if (held == null || held.getItem() == null) {
            return;
        }
        Item item = held.getItem();

        if (item instanceof ItemBow) {
            Vec3 aim = VR.bowTracker() != null ? VR.bowTracker().getAimVector() : null;
            if (aim != null && aim.lengthVector() > 0) {
                lookAlong(player, aim);
            }
        } else if (item == Items.snowball || item == Items.egg || item == Items.spawn_egg
                || item == Items.potionitem) {
            // Thrown from the hand, so the throw follows the hand rather than the crosshair.
            player.rotationYaw = player.rotationYawHead =
                    VR.roomScale().getControllerMainYaw_World();
            player.rotationPitch = -VR.roomScale().getControllerMainPitch_World();
        } else if (VR.swingTracker().shouldLookAtHand) {
            // Dipping a bucket: the hand asks to be looked at, and the look has to be reported
            // back, because the server fills the bucket from where the player is facing. Nothing
            // reported it before, so the branch that fills the bucket never ran.
            Vec3 toHand = VRRenderState.interpolatedPlayerPos
                    .subtract(VR.roomScale().getControllerPos_World(0));
            lookAlong(player, toHand);
            VR.swingTracker().amLookingAtHand = true;
        } else {
            VR.swingTracker().amLookingAtHand = false;
        }
    }

    /** Points the entity down a direction, as yaw and pitch in degrees. */
    private static void lookAlong(EntityPlayerSP player, Vec3 direction) {
        double length = direction.lengthVector();
        if (length < 1.0E-5D) {
            return;
        }
        double sin = direction.yCoord / length;
        if (sin > 1) {
            sin = 1;
        } else if (sin < -1) {
            sin = -1;
        }
        player.rotationYawHead = player.rotationYaw =
                (float) Math.toDegrees(Math.atan2(-direction.xCoord, direction.zCoord));
        player.rotationPitch = (float) -Math.toDegrees(Math.asin(sin));
    }

    /** Where the block the eye is inside would be, for the overlay that draws it. */
    public static Vec3 getEyeBlock(RenderPass pass) {
        Vec3 cam = VR.roomScale().getEyePos_World(pass);
        return Vec3.createVectorHelper(Math.floor(cam.xCoord), Math.floor(cam.yCoord),
                Math.floor(cam.zCoord));
    }
}
