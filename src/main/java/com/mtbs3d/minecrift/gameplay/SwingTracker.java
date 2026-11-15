package com.mtbs3d.minecrift.gameplay;

import java.util.ArrayList;
import java.util.List;

import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.provider.OpenVRPlayer;
import com.mtbs3d.minecrift.settings.VRSettings;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.compat.BackhandCompat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockVine;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import net.minecraftforge.common.ForgeHooks;

/**
 * Hitting and mining things by swinging your arm at them.
 *
 * <p>The oldest idea in the mod and still the one that sells it: there is no attack button, there
 * is a stick in your hand and a cow in front of you.
 *
 * <p>Lived inside {@code OpenVRPlayer} as one long method, where minecrift put it. It is its own
 * tracker now because it had grown past the size where anyone could see what it did, and because
 * everything else physical in this mod is already a tracker.
 *
 * <p>Main hand only. The second hand needs a second held item to swing, which this version of the
 * game does not have; it arrives with the off-hand mod, and the shape here is ready for it.
 */
public class SwingTracker {

    /**
     * How fast the hand has to be moving to count as a swing, in metres a second.
     *
     * <p>Measured at the controller rather than at the end of the weapon, which is why it is
     * lower than the figure upstream uses: the tip of a sword travels a good deal faster than
     * the wrist holding it.
     */
    private static final double SPEED_THRESHOLD = 1.8D;

    /** Ticks after a teleport before a swing counts, so arriving somewhere is not an attack. */
    private static final int SETTLE_TICKS = 3;

    private int disableSwing = SETTLE_TICKS;

    private final Vec3[] lastWeaponEndAir = {
            Vec3.createVectorHelper(0, 0, 0), Vec3.createVectorHelper(0, 0, 0) };
    private final boolean[] lastWeaponSolid = new boolean[2];

    /** Where the ends of the weapon were last tick, so a fast swing sweeps rather than samples. */
    private final Vec3[] lastAttackPoint = new Vec3[2];
    private final Vec3[] lastWeaponTip = new Vec3[2];

    /**
     * Everything already struck by the swing in progress, per hand.
     *
     * <p>Without it a swing that passes through a cow hits it on every tick the hand is inside,
     * which is three or four times for one movement of the arm.
     */
    private final List<List<Entity>> hitThisSwing = new ArrayList<List<Entity>>();

    public float weaponSwingLength;
    public final Vec3[] weaponEnd = new Vec3[2];
    public boolean holdingTool;

    public SwingTracker() {
        this.hitThisSwing.add(new ArrayList<Entity>());
        this.hitThisSwing.add(new ArrayList<Entity>());
    }

    /** The bucket-dipping handshake, read by the aiming code. */
    public boolean shouldLookAtHand;
    public boolean amLookingAtHand;

    /** Called when the player is moved somewhere they did not walk to. */
    public void settle() {
        this.disableSwing = SETTLE_TICKS;
    }

    public boolean isActive(EntityPlayerSP player) {
        VRSettings settings = VR.settings();
        if (settings == null || !settings.weaponCollision || settings.seated) {
            return false;
        }
        if (player == null || player.isDead) {
            return false;
        }
        // Running on the spot swings both arms by definition, and every step would be an attack.
        if (settings.vrFreeMoveMode == VRSettings.FREEMOVE_RUNINPLACE && player.moveForward > 0) {
            return false;
        }
        // Upstream also stands down while the climbey boots are charging a jump. There are no
        // climbey boots on this version, so there is nothing to stand down for.
        return true;
    }

    public void doProcess(Minecraft mc, EntityClientPlayerMP player) {
        if (!isActive(player)) {
            return;
        }
        mc.mcProfiler.startSection("swing");
        try {
            swing(mc, player, 0, player.inventory.getCurrentItem());
            // And the other hand, when something has given this version of the game one. Its
            // actions go through the off-hand mod, which puts its item where the game expects a
            // held item to be for as long as the action takes.
            ItemStack offhand = BackhandCompat.offhandItem(player);
            if (offhand != null && MCOpenVR.isControllerTracking(1)) {
                swing(mc, player, 1, offhand);
            }
        } finally {
            mc.mcProfiler.endSection();
        }
    }

    private void swing(Minecraft mc, EntityClientPlayerMP player, int hand, ItemStack held) {
        OpenVRPlayer vr = VR.player();
        VRSettings settings = VR.settings();

        Vec3 handPos = vr.getControllerPos_World(hand);
        Vec3 handDirection = vr.getCustomHandVector(hand, Vec3.createVectorHelper(0, 0, -1));

        Item item = held != null ? held.getItem() : null;

        boolean sword = isSword(item);
        boolean tool = sword || isTool(item);

        // "Only swords" means exactly that: anything else in your hand passes through the world
        // untouched, which is what somebody who turned this on wanted.
        if (settings.onlySwordCollision && !sword) {
            return;
        }

        float weaponLength;
        float entityReachAdd;
        if (sword) {
            entityReachAdd = 2.5F;
            weaponLength = 0.3F;
        } else if (tool) {
            entityReachAdd = 1.8F;
            weaponLength = 0.3F;
        } else if (item != null) {
            weaponLength = 0.1F;
            entityReachAdd = 0.3F;
        } else {
            weaponLength = 0.0F;
            entityReachAdd = 0.3F;
        }

        this.holdingTool = tool;
        weaponLength *= vr.worldScale;
        this.weaponSwingLength = weaponLength;

        this.weaponEnd[hand] = along(handPos, handDirection, weaponLength);
        Vec3 weaponTip = along(handPos, handDirection, weaponLength + entityReachAdd);

        if (this.disableSwing > 0) {
            if (hand == 0) {
                this.disableSwing--;
            }
            forgetSwing(hand);
            this.lastAttackPoint[hand] = this.weaponEnd[hand];
            this.lastWeaponTip[hand] = weaponTip;
            return;
        }

        float speed = (float) MCOpenVR.controllerHistory[hand].averageSpeed(0.1);
        boolean fast = speed > SPEED_THRESHOLD;
        boolean canact = fast && !this.lastWeaponSolid[hand];

        // A swing is over once the arm slows down, and only then may the same cow be hit again.
        if (!fast) {
            forgetSwing(hand);
        }

        boolean inAnEntity = hitEntities(mc, player, hand, handPos, weaponTip, canact, tool,
                speed);
        boolean insolidBlock = false;

        if (!inAnEntity) {
            insolidBlock = hitBlock(mc, player, hand, held, item, sword, canact, speed);
        }

        if ((!inAnEntity && !insolidBlock) || this.lastWeaponEndAir[hand].lengthVector() == 0) {
            this.lastWeaponEndAir[hand] = copy(this.weaponEnd[hand]);
            this.lastWeaponSolid[hand] = false;
        }

        this.lastAttackPoint[hand] = this.weaponEnd[hand];
        this.lastWeaponTip[hand] = weaponTip;
    }

    /**
     * Everything the weapon passed through this tick.
     *
     * @return true if the weapon is inside something, so the block pass is skipped
     */
    private boolean hitEntities(Minecraft mc, EntityClientPlayerMP player, int hand,
            Vec3 handPos, Vec3 weaponTip, boolean canact, boolean tool, float speed) {
        VRSettings settings = VR.settings();

        // No reaching through walls. A hand can end up on the far side of one in room-scale by
        // simply being put there, and a hit landed that way is indistinguishable, from the
        // server's point of view, from a cheat.
        if (canact) {
            Vec3 head = VR.roomScale().getHMDPos_World();
            if (head != null && mc.theWorld.rayTraceBlocks(copy(head), copy(handPos), false) != null) {
                canact = false;
            }
        }

        // The swept volume, not a snapshot. At twenty ticks a second a fast arm moves most of a
        // metre between samples, and a thin target in the middle of that is missed entirely.
        AxisAlignedBB reach = boxBetween(handPos, weaponTip);
        if (this.lastWeaponTip[hand] != null) {
            reach = include(reach, this.lastWeaponTip[hand]);
        }
        AxisAlignedBB close = boxBetween(handPos, this.weaponEnd[hand]);
        if (this.lastAttackPoint[hand] != null) {
            close = include(close, this.lastAttackPoint[hand]);
        }

        boolean inAnEntity = false;
        @SuppressWarnings("unchecked")
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(
                mc.renderViewEntity, reach);

        for (Entity hitEntity : entities) {
            if (!hitEntity.canBeCollidedWith() || hitEntity == mc.renderViewEntity.ridingEntity) {
                continue;
            }
            // Another player is reached with the weapon itself rather than with its swing arc,
            // so that a wide gesture near somebody does not read as an attack on them.
            if (settings.reducedPlayerReach && hitEntity instanceof EntityPlayer
                    && !close.intersectsWith(hitEntity.boundingBox)) {
                continue;
            }
            inAnEntity = true;

            if (this.hitThisSwing.get(hand).contains(hitEntity)) {
                continue;
            }

            final Entity target = hitEntity;
            if (settings.animaltouching && hitEntity instanceof EntityAnimal && !tool
                    && !this.lastWeaponSolid[hand]) {
                withHand(player, hand, new java.util.function.BooleanSupplier() {
                    public boolean getAsBoolean() {
                        return mc.playerController.interactWithEntitySendPacket(player, target);
                    }
                });
                MCOpenVR.triggerHapticPulse(hand, 250);
                this.lastWeaponSolid[hand] = true;
                this.hitThisSwing.get(hand).add(hitEntity);
            } else if (canact && (hand == 0 || BackhandCompat.offhandAttackAllowed(player))) {
                withHand(player, hand, new java.util.function.BooleanSupplier() {
                    public boolean getAsBoolean() {
                        mc.playerController.attackEntity(player, target);
                        return true;
                    }
                });
                // Deliberately no swing animation. The arm has just physically swung; playing
                // the same motion on top of it is that motion twice, out of step with itself.
                // It is also why this goes to the controller layer rather than through the
                // game's own attack, which would start one. Modern Vivecraft is built the same
                // way, and the off hand must match the main one or only one of them twitches.
                MCOpenVR.triggerHapticPulse(hand, 1000);
                this.lastWeaponSolid[hand] = true;
                this.hitThisSwing.get(hand).add(hitEntity);
            }
        }

        // While the arm is still moving, everything the weapon is touching counts as already
        // struck, so following through does not land a second blow.
        if (speed > SPEED_THRESHOLD) {
            for (Entity entity : entities) {
                if (!this.hitThisSwing.get(hand).contains(entity)) {
                    this.hitThisSwing.get(hand).add(entity);
                }
            }
        }
        return inAnEntity;
    }

    /** @return true if the weapon ended up inside a solid block */
    private boolean hitBlock(Minecraft mc, final EntityClientPlayerMP player, final int hand,
            final ItemStack held, Item item, boolean sword, boolean canact, float speed) {
        VRSettings settings = VR.settings();

        final Vec3 end = this.weaponEnd[hand];
        final int bx = MathHelper.floor_double(end.xCoord);
        final int by = MathHelper.floor_double(end.yCoord);
        final int bz = MathHelper.floor_double(end.zCoord);

        Block block = mc.theWorld.getBlock(bx, by, bz);
        Material material = block.getMaterial();

        final MovingObjectPosition col = mc.theWorld.rayTraceBlocks(
                copy(this.lastWeaponEndAir[hand]), copy(end), true);
        boolean dipping = this.shouldLookAtHand;
        if (!dipping && (col == null
                || col.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)) {
            return false;
        }
        this.shouldLookAtHand = false;
        if (material == Material.air) {
            return false;
        }

        if (material.isLiquid()) {
            if (item == Items.bucket && hand == 0) {
                this.shouldLookAtHand = true;
                if (this.amLookingAtHand && mc.playerController.sendUseItem(player,
                        player.worldObj, held)) {
                    mc.entityRenderer.itemRenderer.resetEquippedProgress2();
                }
            }
            return true;
        }

        if (canact && settings.realisticOpening && OpenVRPlayer.isOpenable(block)) {
            // A door, a gate, a hatch, a lever, a button: in the room you shove it, which is
            // what it is there for.
            withHand(player, hand, new java.util.function.BooleanSupplier() {
                public boolean getAsBoolean() {
                    return mc.playerController.onPlayerRightClick(player, player.worldObj, held,
                            col.blockX, col.blockY, col.blockZ, col.sideHit, col.hitVec);
                }
            });
            MCOpenVR.triggerHapticPulse(hand, 800);
            this.lastWeaponSolid[hand] = true;
        } else if (canact && (hand == 0 || BackhandCompat.offhandMiningAllowed(player))
                && mayMine(player, held, sword, block, bx, by, bz)) {
            if (item instanceof ItemHoe) {
                withHand(player, hand, new java.util.function.BooleanSupplier() {
                    public boolean getAsBoolean() {
                        return mc.playerController.onPlayerRightClick(player, player.worldObj,
                                held, bx, by, bz, col.sideHit, col.hitVec);
                    }
                });
            } else {
                int hits = 3 + (int) ((speed - SPEED_THRESHOLD) / 2);
                for (int i = 0; i < hits; i++) {
                    VR.player().clearBlockHitDelay();
                    mc.playerController.clickBlock(col.blockX, col.blockY, col.blockZ, col.sideHit);
                    if (!VR.player().getIsHittingBlock()) {
                        break;
                    }
                    mc.playerController.onPlayerDamageBlock(col.blockX, col.blockY, col.blockZ,
                            col.sideHit);
                    if (!VR.player().getIsHittingBlock()) {
                        break;
                    }
                    mc.effectRenderer.addBlockHitEffects(col.blockX, col.blockY, col.blockZ,
                            col.sideHit);
                }
            }
            MCOpenVR.triggerHapticPulse(hand, 1000);
            this.lastWeaponSolid[hand] = true;
        }
        return true;
    }

    /**
     * Whether this block may be mined by swinging at it with this.
     *
     * <p>Two separate refusals. A ladder or a vine is what you are hanging from, and breaking it
     * by brushing past is how a climb ends badly. And a sword is not a mining tool: swinging one
     * at a wall hacks it down in the usual game and should not here, unless the player has said
     * otherwise -- or unless the sword is the right tool for it anyway, as it is for cobwebs.
     */
    private boolean mayMine(EntityPlayerSP player, ItemStack held, boolean sword, Block block,
            int x, int y, int z) {
        VRSettings settings = VR.settings();
        if (settings.realisticClimbEnabled
                && (block instanceof BlockLadder || block instanceof BlockVine)) {
            return false;
        }
        if (!sword) {
            return true;
        }
        if (!settings.swordBlockCollision) {
            return false;
        }
        boolean correctTool = held != null
                && ForgeHooks.canToolHarvestBlock(block, player.worldObj.getBlockMetadata(x, y, z),
                        held);
        boolean instant = block.getPlayerRelativeBlockHardness(player, player.worldObj, x, y, z)
                >= 1.0F;
        return correctTool || instant;
    }

    private void forgetSwing(int hand) {
        if (!this.hitThisSwing.get(hand).isEmpty()) {
            this.hitThisSwing.get(hand).clear();
        }
    }

    /**
     * Does something as the given hand.
     *
     * <p>The main hand is the hand the game already believes in, so that one is simply done.
     * The other has to be lent the game's one hand for the duration, which is what the off-hand
     * mod is for.
     */
    private static boolean withHand(EntityClientPlayerMP player, int hand,
            java.util.function.BooleanSupplier action) {
        if (hand == 0) {
            return action.getAsBoolean();
        }
        return BackhandCompat.withOffhand(player, action);
    }

    private static Vec3 along(Vec3 from, Vec3 direction, float distance) {
        return Vec3.createVectorHelper(
                from.xCoord + direction.xCoord * distance,
                from.yCoord + direction.yCoord * distance,
                from.zCoord + direction.zCoord * distance);
    }

    /** The world ray tracer consumes the vectors it is given, so it gets copies. */
    private static Vec3 copy(Vec3 v) {
        return Vec3.createVectorHelper(v.xCoord, v.yCoord, v.zCoord);
    }

    private static AxisAlignedBB boxBetween(Vec3 a, Vec3 b) {
        return AxisAlignedBB.getBoundingBox(
                Math.min(a.xCoord, b.xCoord), Math.min(a.yCoord, b.yCoord),
                Math.min(a.zCoord, b.zCoord), Math.max(a.xCoord, b.xCoord),
                Math.max(a.yCoord, b.yCoord), Math.max(a.zCoord, b.zCoord));
    }

    private static AxisAlignedBB include(AxisAlignedBB box, Vec3 point) {
        return AxisAlignedBB.getBoundingBox(
                Math.min(box.minX, point.xCoord), Math.min(box.minY, point.yCoord),
                Math.min(box.minZ, point.zCoord), Math.max(box.maxX, point.xCoord),
                Math.max(box.maxY, point.yCoord), Math.max(box.maxZ, point.zCoord));
    }

    private static boolean isSword(Item item) {
        if (item instanceof ItemSword) {
            return true;
        }
        if (item == null) {
            return false;
        }
        String parent = item.getClass().getSuperclass().getName().toLowerCase();
        return parent.contains("weapon") || parent.contains("sword");
    }

    private static boolean isTool(Item item) {
        if (item instanceof ItemTool || item instanceof ItemHoe) {
            return true;
        }
        if (item == null) {
            return false;
        }
        return item.getClass().getSuperclass().getName().toLowerCase().contains("tool");
    }
}
