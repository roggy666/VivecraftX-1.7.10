package com.mtbs3d.minecrift.gameplay;

import com.mtbs3d.minecrift.provider.MCOpenVR;

import de.fruitfly.ovr.structs.Vector3f;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

/**
 * Eating by raising the food to your mouth.
 *
 * <p>In a flat game you hold a button; in VR you have a hand and a head, and the gesture that
 * means "eat this" is the one everybody already knows. Holding a button instead would be the one
 * place in the mod where having hands counted for nothing.
 *
 * <p>Originally by Hendrik, as part of minecrift.
 */
public class EatingTracker {

    /**
     * How far your mouth is below the point the headset reports as your eyes.
     *
     * <p>Zero, and modern Vivecraft has it as zero too. Faces differ by a couple of centimetres
     * and the test below allows a quarter of a metre, so the distinction never decides anything;
     * it stays as a name for what the test is measuring to.
     */
    private static final float MOUTH_BELOW_EYES = 0.0F;

    /** How close the food has to come. Generous, because nobody wants to aim at their own face. */
    private static final float THRESHOLD = 0.25F;

    /** Ahead of the controller, so the food leads the hand rather than the grip doing. */
    private static final float FOOD_AHEAD = 0.2F;

    /** How long one mouthful is allowed to take before the gesture has to be made again. */
    private static final long EAT_TIME = 2100L;

    /** How long to wait before offering the gesture again after the game declined it. */
    private static final long RETRY_AFTER = 500L;

    /** 1.7.10 has one hand that can hold things, so one of these. */
    private boolean eating;
    private long eatStart;
    private long declinedAt;
    private boolean wasInRange;
    /** So a full hunger bar is explained once, rather than every time food is raised. */
    private static boolean explainedRefusal;

    /** Whether food is being eaten right now, for anything that should not interrupt it. */
    public boolean isEating() {
        return this.eating;
    }

    /**
     * Whether the gesture is worth testing for at all.
     *
     * <p>A drawn bow is excluded because drawing one brings the hand to the face as well, and
     * eating the arrow instead of firing it would be a surprise.
     */
    public boolean isActive(EntityPlayerSP player) {
        if (VR.settings() == null || VR.settings().seated) {
            return false;
        }
        if (player == null || player.isDead || player.isPlayerSleeping()) {
            return false;
        }
        if (Minecraft.getMinecraft().playerController == null) {
            return false;
        }
        if (VR.bowTracker() != null && VR.bowTracker().isActive(player)) {
            return false;
        }
        return isEatable(player.getHeldItem());
    }

    private static boolean isEatable(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        EnumAction action = stack.getItemUseAction();
        return action == EnumAction.eat || action == EnumAction.drink;
    }

    public void doProcess(Minecraft minecraft, EntityPlayerSP player) {
        if (!isActive(player)) {
            this.eating = false;
            return;
        }

        // Both positions are taken raw, before the play area's own scale and rotation. The
        // distance from your hand to your mouth is a distance in the room, and it does not become
        // a different distance because the world around you was made larger or smaller.
        Vec3 mouth = MCOpenVR.getCenterEyePosition()
                .addVector(0, -MOUTH_BELOW_EYES, 0);
        Vec3 held = MCOpenVR.getSmoothedAimSource(0, 0.333D);
        if (mouth == null || held == null) {
            return;
        }
        Vector3f ahead = MCOpenVR.getAimRotation(0).transform(new Vector3f(0, 0, -FOOD_AHEAD));
        Vec3 food = held.addVector(ahead.x, ahead.y, ahead.z);

        double distance = mouth.distanceTo(food);
        if (distance >= THRESHOLD) {
            this.eating = false;
            this.wasInRange = false;
            return;
        }

        ItemStack stack = player.getHeldItem();
        int crunchiness;
        if (stack.getItemUseAction() == EnumAction.drink) {
            // A bottle has to be tipped up to pour. Holding it the other way round means the
            // player is only moving their hand past their face.
            Vector3f up = MCOpenVR.getAimRotation(0).transform(new Vector3f(0, 1, 0));
            if (up.y > 0) {
                this.eating = false;
                return;
            }
            crunchiness = 0;
        } else {
            crunchiness = 2;
        }

        if (!this.eating) {
            long now = Minecraft.getSystemTime();
            if (now - this.declinedAt < RETRY_AFTER) {
                // Already turned down a moment ago. Asking again every tick would be a packet to
                // the server twenty times a second for as long as the food is held up.
                return;
            }
            minecraft.playerController.sendUseItem(player, player.worldObj, stack);
            // Asking the player rather than trusting the return value: that method reports
            // whether the stack changed, and starting to eat does not change it -- the food is
            // only taken when the eating finishes.
            if (!player.isUsingItem()) {
                // Nothing in use, so the game declined: a full hunger bar is the usual reason,
                // and no gesture can talk it round.
                this.declinedAt = now;
                // Said once, because the usual reason is a full hunger bar and no gesture can
                // talk that round -- the same refusal a button would get.
                if (!explainedRefusal) {
                    explainedRefusal = true;
                    ViveCraftX.LOG.info("Food reached the mouth but the game declined to eat it;"
                            + " hunger is {}/20", player.getFoodStats().getFoodLevel());
                }
                return;
            }
            this.eating = true;
            this.wasInRange = true;
            this.eatStart = now;
        }

        // A nudge per bite, in time with the chewing the game is counting down.
        long remaining = player.getItemInUseCount();
        if (remaining > 0 && remaining % 5 <= crunchiness) {
            MCOpenVR.triggerHapticPulse(0, 700);
        }

        if (Minecraft.getSystemTime() - this.eatStart > EAT_TIME) {
            this.eating = false;
        }
    }
}
