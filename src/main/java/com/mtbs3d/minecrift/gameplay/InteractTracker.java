package com.mtbs3d.minecrift.gameplay;

import com.mtbs3d.minecrift.control.ControllerType;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.VRSettings;

import lol.gzmc.vivecraftx.VR;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.List;

/**
 * Putting your hand on something and pressing, instead of pointing at it from across the room.
 *
 * <p>Pulling a lever you are standing next to, opening the chest at your feet, patting the dog:
 * all of these are things you do with a hand, and aiming a ray at them from the shoulder is a
 * flat game's answer to a problem a headset does not have.
 *
 * <p>Two halves, and the split matters. Every tick this works out whether either hand is resting
 * on something worth pressing, and tells the input layer to switch the interact binding on or off
 * for that hand -- which is what lets the same physical button mean "interact" when your hand is
 * on a lever and nothing at all when it is in the air, without the player binding it twice. The
 * press itself is consumed separately, from the input poll, because that is where button presses
 * are read.
 */
public class InteractTracker {

    /** How far past the controller the fingers are taken to reach. */
    private static final double FINGER_REACH = 0.1D;

    /** What each hand is resting on, or null. Index is the controller. */
    private final MovingObjectPosition[] block = new MovingObjectPosition[2];
    private final Entity[] entity = new Entity[2];

    /** So the buzz happens when something comes into reach, not every tick it stays there. */
    private final boolean[] wasActive = new boolean[2];

    public boolean isActive(EntityPlayerSP player) {
        VRSettings settings = VR.settings();
        if (settings == null || settings.seated) {
            return false;
        }
        if (player == null || player.isDead) {
            return false;
        }
        if (!settings.realisticBlockInteractEnabled && !settings.realisticEntityInteractEnabled) {
            return false;
        }
        return Minecraft.getMinecraft().currentScreen == null;
    }

    /**
     * Works out what each hand could press, once a tick.
     *
     * <p>Also turns the binding off for a hand with nothing under it. That is not decoration: a
     * trigger bound to both attacking and interacting has to be free to do the other thing when
     * there is nothing to interact with.
     */
    public void doProcess(Minecraft mc, EntityPlayerSP player) {
        if (!isActive(player)) {
            for (int c = 0; c < 2; c++) {
                clear(c);
            }
            return;
        }
        VRSettings settings = VR.settings();

        for (int c = 0; c < 2; c++) {
            Vec3 handPos = VR.roomScale().getControllerPos_World(c);
            Vec3 handDir = VR.roomScale().getControllerDir_World(c);
            if (handPos == null || handDir == null) {
                clear(c);
                continue;
            }
            Vec3 fingers = Vec3.createVectorHelper(
                    handPos.xCoord + handDir.xCoord * FINGER_REACH,
                    handPos.yCoord + handDir.yCoord * FINGER_REACH,
                    handPos.zCoord + handDir.zCoord * FINGER_REACH);

            this.block[c] = null;
            this.entity[c] = null;

            if (settings.realisticEntityInteractEnabled) {
                this.entity[c] = entityAt(mc, handPos, fingers);
            }
            if (this.entity[c] == null && settings.realisticBlockInteractEnabled) {
                this.block[c] = blockAt(mc, player, handPos, fingers);
            }

            boolean active = this.entity[c] != null || this.block[c] != null;
            if (active && !this.wasActive[c]) {
                // A touch, so you can feel that there is something there without looking at it.
                MCOpenVR.triggerHapticPulse(c, 250);
            }
            this.wasActive[c] = active;
            MCOpenVR.setInteractEnabled(ControllerType.values()[c], active);
        }
    }

    /**
     * Carries out whatever the hand is on. Called from the input poll, where presses are read.
     */
    public void processBindings(final Minecraft mc, final EntityClientPlayerMP player) {
        for (int c = 0; c < 2; c++) {
            if (!MCOpenVR.keyVRInteract.isPressed(ControllerType.values()[c])) {
                continue;
            }
            final int hand = c;
            boolean done = false;
            if (this.entity[c] != null) {
                final Entity target = this.entity[c];
                done = withHand(player, hand, new java.util.function.BooleanSupplier() {
                    public boolean getAsBoolean() {
                        return mc.playerController.interactWithEntitySendPacket(player, target);
                    }
                });
            } else if (this.block[c] != null) {
                final MovingObjectPosition hit = this.block[c];
                done = withHand(player, hand, new java.util.function.BooleanSupplier() {
                    public boolean getAsBoolean() {
                        // Whatever the acting hand is holding, which the off-hand mod has put
                        // in the held slot for the length of this call.
                        ItemStack held = player.inventory.getCurrentItem();
                        return mc.playerController.onPlayerRightClick(player, player.worldObj,
                                held, hit.blockX, hit.blockY, hit.blockZ, hit.sideHit,
                                hit.hitVec);
                    }
                });
            }
            if (done) {
                // A press, not a blow: the wrist rolls rather than the arm chopping.
                lol.gzmc.vivecraftx.VRSwing.began(hand, lol.gzmc.vivecraftx.VRSwing.Kind.INTERACT);
                if (hand == 1) {
                    lol.gzmc.vivecraftx.compat.BackhandCompat.swingOffhand(player);
                } else {
                    player.swingItem();
                }
                MCOpenVR.triggerHapticPulse(hand, 750);
            }
        }
    }

    /** The main hand acts as itself; the other borrows the game's one hand for the call. */
    private static boolean withHand(EntityClientPlayerMP player, int hand,
            java.util.function.BooleanSupplier action) {
        if (hand == 0) {
            return action.getAsBoolean();
        }
        return lol.gzmc.vivecraftx.compat.BackhandCompat.withOffhand(player, action);
    }

    /**
     * The thing the hand is inside, if anything.
     *
     * <p>Traced from the head rather than tested as a box, so that a hand which has ended up
     * through a wall -- which room-scale makes easy -- cannot reach what is on the other side.
     */
    private Entity entityAt(Minecraft mc, Vec3 handPos, Vec3 fingers) {
        if (reachesThroughWall(mc, handPos)) {
            return null;
        }
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
                Math.min(handPos.xCoord, fingers.xCoord), Math.min(handPos.yCoord, fingers.yCoord),
                Math.min(handPos.zCoord, fingers.zCoord), Math.max(handPos.xCoord, fingers.xCoord),
                Math.max(handPos.yCoord, fingers.yCoord), Math.max(handPos.zCoord, fingers.zCoord));

        @SuppressWarnings("unchecked")
        List<Entity> found = mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.renderViewEntity,
                box);
        for (Entity candidate : found) {
            if (candidate.canBeCollidedWith() && candidate != mc.renderViewEntity.ridingEntity) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The block face the hand is touching, if the block is one that does anything when pressed.
     *
     * <p>Anything is too generous -- every stone wall would buzz at you -- so this asks the block
     * whether it has its own answer to being clicked. A block that does not override that
     * behaviour inherits the base implementation, and the base implementation does nothing.
     */
    private MovingObjectPosition blockAt(Minecraft mc, EntityPlayerSP player, Vec3 handPos,
            Vec3 fingers) {
        if (reachesThroughWall(mc, handPos)) {
            return null;
        }
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(
                Vec3.createVectorHelper(handPos.xCoord, handPos.yCoord, handPos.zCoord),
                Vec3.createVectorHelper(fingers.xCoord, fingers.yCoord, fingers.zCoord), false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            // Not against a face, but the fingers may be inside the block itself.
            int x = MathHelper.floor_double(fingers.xCoord);
            int y = MathHelper.floor_double(fingers.yCoord);
            int z = MathHelper.floor_double(fingers.zCoord);
            Block inside = mc.theWorld.getBlock(x, y, z);
            if (inside.getMaterial() == Material.air || !hasUse(inside)) {
                return null;
            }
            return new MovingObjectPosition(x, y, z, 1, Vec3.createVectorHelper(
                    fingers.xCoord, fingers.yCoord, fingers.zCoord));
        }
        Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
        return hasUse(block) ? hit : null;
    }

    /** Whether this block has anything to say when it is clicked. */
    private static boolean hasUse(Block block) {
        if (block == null) {
            return false;
        }
        try {
            return !block.getClass()
                    .getMethod("onBlockActivated", net.minecraft.world.World.class, int.class,
                            int.class, int.class, net.minecraft.entity.player.EntityPlayer.class,
                            int.class, float.class, float.class, float.class)
                    .getDeclaringClass().equals(Block.class);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean reachesThroughWall(Minecraft mc, Vec3 handPos) {
        Vec3 head = VR.roomScale().getHMDPos_World();
        if (head == null) {
            return false;
        }
        return mc.theWorld.rayTraceBlocks(
                Vec3.createVectorHelper(head.xCoord, head.yCoord, head.zCoord),
                Vec3.createVectorHelper(handPos.xCoord, handPos.yCoord, handPos.zCoord),
                false) != null;
    }

    private void clear(int c) {
        this.block[c] = null;
        this.entity[c] = null;
        if (this.wasActive[c]) {
            this.wasActive[c] = false;
            MCOpenVR.setInteractEnabled(ControllerType.values()[c], false);
        }
    }
}
