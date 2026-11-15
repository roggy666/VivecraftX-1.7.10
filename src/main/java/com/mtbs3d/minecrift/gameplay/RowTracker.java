package com.mtbs3d.minecrift.gameplay;

import com.mtbs3d.minecrift.control.ControllerType;
import com.mtbs3d.minecrift.provider.MCOpenVR;

import lol.gzmc.vivecraftx.VR;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.Vec3;

/**
 * Rowing a boat with your arms.
 *
 * <p>A boat is the one vehicle in the game you are supposed to be working rather than riding, and
 * pushing a stick to make it go is the least interesting thing hands can do. Pulling both arms
 * back together drives it forward; pulling one drives it round.
 *
 * <p>There are no oars to see -- this version has no paddle animation and nothing to hang one on
 * -- so what the player gets is the motion and the result, which is the part they feel anyway.
 */
public class RowTracker {

    /** Hand speed, in metres a second, below which this is just somebody gesturing. */
    private static final double STROKE_SPEED = 0.6D;

    /** How much of a stroke reaches the boat. Tuned so a steady pace is a little under sprint. */
    private static final double DRIVE = 0.009D;

    /** How much harder one arm has to pull than the other before it counts as steering. */
    private static final double TURN = 0.004D;

    private boolean strokingLeft;
    private boolean strokingRight;

    public boolean isActive(EntityPlayerSP player) {
        if (VR.settings() == null || VR.settings().seated
                || !VR.settings().realisticRowEnabled) {
            return false;
        }
        return player != null && player.ridingEntity instanceof EntityBoat;
    }

    public void doProcess(Minecraft minecraft, EntityPlayerSP player) {
        if (!isActive(player)) {
            this.strokingLeft = false;
            this.strokingRight = false;
            return;
        }
        EntityBoat boat = (EntityBoat) player.ridingEntity;

        double right = pull(0);
        double left = pull(1);
        if (right <= 0 && left <= 0) {
            this.strokingLeft = false;
            this.strokingRight = false;
            return;
        }

        // Backwards along the boat's own heading, because that is the direction an oar pushes
        // the water, and so the direction the boat goes is the one it is pointing.
        double yaw = Math.toRadians(boat.rotationYaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        double drive = (right + left) * DRIVE;
        boat.motionX += forwardX * drive;
        boat.motionZ += forwardZ * drive;

        // One arm harder than the other turns the boat, the way it would.
        boat.rotationYaw += (float) ((right - left) * TURN * 90D);

        // A knock at the start of each stroke, so the effort has something to push against.
        if (right > 0 && !this.strokingRight) {
            MCOpenVR.triggerHapticPulse(ControllerType.RIGHT, 0.04F, 160F, 0.5F);
        }
        if (left > 0 && !this.strokingLeft) {
            MCOpenVR.triggerHapticPulse(ControllerType.LEFT, 0.04F, 160F, 0.5F);
        }
        this.strokingRight = right > 0;
        this.strokingLeft = left > 0;
    }

    /**
     * How hard this hand is pulling backwards, or zero if it is not.
     *
     * <p>Only the backward half of the motion counts. Pushing forward again is the recovery
     * stroke, when an oar is out of the water, and making that drive the boat too would turn
     * rowing into shaking.
     */
    private static double pull(int controller) {
        Vec3 moved = MCOpenVR.controllerHistory[controller].netMovement(0.2D);
        double speed = MCOpenVR.controllerHistory[controller].averageSpeed(0.2D);
        if (moved == null || speed < STROKE_SPEED) {
            return 0D;
        }
        // Backwards in the player's own frame: the direction the hand went, against the way the
        // head is facing.
        Vec3 look = VR.roomScale().getHMDDir_World();
        double along = moved.xCoord * look.xCoord + moved.zCoord * look.zCoord;
        return along < 0 ? -along : 0D;
    }
}
