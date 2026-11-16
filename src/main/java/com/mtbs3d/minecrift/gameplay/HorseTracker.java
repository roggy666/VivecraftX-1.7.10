package com.mtbs3d.minecrift.gameplay;

import com.mtbs3d.minecrift.control.ControllerType;
import com.mtbs3d.minecrift.provider.MCOpenVR;

import lol.gzmc.vivecraftx.VR;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.Vec3;

/**
 * Riding a horse with your hands instead of a keyboard.
 *
 * <p>You hold reins. Flick them forward and the horse goes; raise a hand and hold it to gather a
 * jump, and the height you reach decides how far it clears. Both are things the game already
 * does -- it is the same jump charge the space bar builds -- reached the way a rider would.
 */
public class HorseTracker {

    /** Hand speed, in metres a second, at which a flick counts as asking for speed. */
    private static final double URGE_SPEED = 1.2D;

    /** How far above the head a hand has to be before it is gathering a jump. */
    private static final double RAISE = 0.18D;

    /** How far above that is a full-power jump. */
    private static final double RAISE_FULL = 0.55D;

    private boolean wasRaised;

    /**
     * The highest the hand got while it was up.
     *
     * <p>Remembered rather than read on release, because by the time the gesture ends the hand is
     * back down: measuring then gave a negative amount over the threshold every time, so every
     * jump was the fifteen per cent floor and the gesture had no range at all.
     */
    private double peakRaise;

    private int urgeCooldown;

    public boolean isActive(EntityPlayerSP player) {
        if (VR.settings() == null || VR.settings().seated) {
            return false;
        }
        return player != null && player.ridingEntity instanceof EntityHorse
                && ((EntityHorse) player.ridingEntity).isHorseSaddled();
    }

    public void doProcess(Minecraft minecraft, EntityPlayerSP player) {
        if (!isActive(player)) {
            this.wasRaised = false;
            this.peakRaise = 0D;
            this.urgeCooldown = 0;
            return;
        }
        if (this.urgeCooldown > 0) {
            this.urgeCooldown--;
        }

        // Gathering a jump: hold a hand up, and how high decides how far. Sent as the charge the
        // game already understands, so the horse jumps exactly as it would from the space bar.
        double raised = handAboveHead(player);
        if (raised > RAISE) {
            this.wasRaised = true;
            this.peakRaise = Math.max(this.peakRaise, raised);
        } else if (this.wasRaised) {
            this.wasRaised = false;
            double over = Math.min((this.peakRaise - RAISE) / (RAISE_FULL - RAISE), 1.0D);
            int power = (int) (Math.max(over, 0.15D) * 100);
            this.peakRaise = 0D;
            // Action six, which is the one the server reads a horse's jump charge from. Sent as
            // five it was read as "stop sprinting": the charge was discarded and the player was
            // un-sprinted server-side without their own client knowing, so every reins jump was
            // the weakest possible hop and left the two sides disagreeing about sprinting.
            sendAction(player, 6, power);
            MCOpenVR.triggerHapticPulse(ControllerType.RIGHT, 0.08F, 160F, 0.8F);
        }

        // Flicking the reins. The horse's own speed is the server's business, so this asks for
        // a sprint the same way holding the key would.
        if (this.urgeCooldown == 0 && flicked()) {
            this.urgeCooldown = 10;
            player.setSprinting(true);
            MCOpenVR.triggerHapticPulse(ControllerType.RIGHT, 0.05F, 160F, 0.6F);
            MCOpenVR.triggerHapticPulse(ControllerType.LEFT, 0.05F, 160F, 0.6F);
        }
    }

    /** How far the higher hand is above the head, in metres. */
    private static double handAboveHead(EntityPlayerSP player) {
        Vec3 head = MCOpenVR.getCenterEyePosition();
        double best = 0;
        for (int c = 0; c < 2; c++) {
            Vec3 hand = MCOpenVR.getSmoothedAimSource(c, 0.1D);
            if (hand == null) {
                continue;
            }
            double over = hand.yCoord - head.yCoord;
            if (over > best) {
                best = over;
            }
        }
        return best;
    }

    /** Both hands moving down and forward together, which is what shaking reins looks like. */
    private static boolean flicked() {
        for (int c = 0; c < 2; c++) {
            if (MCOpenVR.controllerHistory[c].averageSpeed(0.15D) < URGE_SPEED) {
                return false;
            }
            Vec3 moved = MCOpenVR.controllerHistory[c].netMovement(0.15D);
            if (moved == null || moved.yCoord > -0.05D) {
                return false;
            }
        }
        return true;
    }

    private static void sendAction(EntityPlayerSP player, int action, int power) {
        if (!(player instanceof EntityClientPlayerMP)) {
            return;
        }
        ((EntityClientPlayerMP) player).sendQueue.addToSendQueue(
                new C0BPacketEntityAction(player, action, power));
    }
}
