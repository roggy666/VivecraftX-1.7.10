package com.mtbs3d.minecrift.gameplay;

import com.mtbs3d.minecrift.control.ControllerType;
import com.mtbs3d.minecrift.provider.MCOpenVR;

import lol.gzmc.vivecraftx.VR;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.potion.Potion;

/**
 * What the world is doing to you, felt in the hands.
 *
 * <p>Everything the controllers buzzed for until now was something the player did -- swinging,
 * drawing a bow, grabbing a ladder. Nothing that happened *to* them registered at all, and in a
 * headset that matters more than it does flat: the screen edge cannot flash red in your
 * peripheral vision when your whole field of view is the world, and a hit from behind had no
 * signal whatsoever.
 *
 * <p>Deliberately restrained. A controller that is always buzzing is a controller nobody feels,
 * so each of these is either brief or slow, and the only one that is strong is being hurt.
 */
public class HapticTracker {

    /** Below this fraction of full health, the slow pulse starts. */
    private static final float LOW_HEALTH = 0.3F;

    private float lastHealth = -1F;
    private int tick;

    public boolean isActive(EntityPlayerSP player) {
        if (VR.settings() == null || !VR.settings().vrWorldHaptics) {
            return false;
        }
        return player != null && !player.isDead && !player.isPlayerSleeping();
    }

    public void doProcess(Minecraft minecraft, EntityPlayerSP player) {
        if (!isActive(player)) {
            this.lastHealth = -1F;
            return;
        }
        this.tick++;

        float health = player.getHealth();
        if (this.lastHealth < 0) {
            this.lastHealth = health;
        }

        // Being hurt. Scaled by how much it hurt, and capped, because a fall from height should
        // be felt as a thump and not as the controller trying to leave your hand.
        float lost = this.lastHealth - health;
        if (lost > 0.01F) {
            float amount = Math.min(lost / 10F, 1F);
            pulse(ControllerType.RIGHT, 0.09F + 0.16F * amount, 1F);
            pulse(ControllerType.LEFT, 0.09F + 0.16F * amount, 1F);
        }
        this.lastHealth = health;

        // A heartbeat while badly hurt, slow enough to read as a warning rather than as noise.
        float max = player.getMaxHealth();
        if (max > 0 && health > 0 && health / max < LOW_HEALTH && this.tick % 20 == 0) {
            pulse(ControllerType.RIGHT, 0.05F, 0.45F);
            pulse(ControllerType.LEFT, 0.05F, 0.45F);
        }

        // On fire, or standing in something that burns: a fast flicker, which is what it is.
        if (player.isBurning() && this.tick % 4 == 0) {
            pulse(ControllerType.RIGHT, 0.02F, 0.3F);
            pulse(ControllerType.LEFT, 0.02F, 0.3F);
        }

        // Poison and wither take health away a point at a time, which the hit rule above would
        // report as a series of jabs. One short tick each, instead.
        if ((player.isPotionActive(Potion.poison) || player.isPotionActive(Potion.wither))
                && this.tick % 10 == 0) {
            pulse(ControllerType.RIGHT, 0.02F, 0.25F);
        }

        // Rain on your hands. Barely there on purpose -- it is weather, not a warning.
        if (this.tick % 3 == 0 && player.worldObj != null && player.worldObj.isRaining()
                && player.worldObj.canBlockSeeTheSky((int) Math.floor(player.posX),
                        (int) Math.floor(player.posY), (int) Math.floor(player.posZ))) {
            if (player.worldObj.rand.nextInt(3) == 0) {
                pulse(player.worldObj.rand.nextBoolean() ? ControllerType.RIGHT
                                                         : ControllerType.LEFT, 0.012F, 0.14F);
            }
        }

        // Starving: the same rhythm as the shaking hunger bar.
        if (player.getFoodStats().getFoodLevel() == 0 && this.tick % 15 == 0) {
            pulse(ControllerType.LEFT, 0.03F, 0.3F);
        }
    }

    private static void pulse(ControllerType hand, float seconds, float amplitude) {
        MCOpenVR.triggerHapticPulse(hand, seconds, 160F, amplitude);
    }
}
