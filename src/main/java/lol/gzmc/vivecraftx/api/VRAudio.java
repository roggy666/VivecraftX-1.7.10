package lol.gzmc.vivecraftx.api;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.VRRenderState;
import lol.gzmc.vivecraftx.VRState;

import net.minecraft.util.Vec3;

/**
 * Where the player's ears are, for anything that plays sound of its own.
 *
 * <p>A mod with its own audio engine cannot be helped by moving the game's listener, because it
 * has its own. In a headset that matters more than it sounds: this version writes the player's
 * yaw and pitch from the <em>controller</em>, since that is what aims at blocks, so a mod that
 * derives its listener from the player entity ends up with a soundscape that swings when the
 * player waves an arm and does not move when they turn their head.
 *
 * <p>So rather than reach into such a mod, this offers it the one thing it cannot work out for
 * itself. Deliberately a single static method returning plain numbers: a caller can bind to it
 * with three lines of reflection, needs no types from here, and keeps working whether or not
 * this mod is installed. It is the only part of this mod meant to be called from outside it,
 * and its shape will not change.
 */
public final class VRAudio {

    private VRAudio() {}

    /**
     * The head's position and orientation this frame, or null when there is no headset to ask.
     *
     * <p>Nine doubles: three for where the ears are, three for the direction they face, three
     * for which way is up. That is the order OpenAL itself wants a listener in, so a caller can
     * hand it straight over.
     *
     * <p>The position is the one the eye is actually drawn from this frame, not the player
     * entity's -- those differ by however far the player has leaned or stepped inside their own
     * room, which is exactly the distance that makes positional audio convincing or not. Null
     * rather than a fallback, so a caller can tell the difference between "the head is here" and
     * "there is no head" and keep its own behaviour for the second.
     */
    public static double[] listenerPose() {
        if (!VRState.running() || VR.roomScale() == null) {
            return null;
        }
        Vec3 head = VRRenderState.getEyeRenderPos(RenderPass.Center);
        Vec3 look = VR.roomScale().getHMDDir_World();
        Vec3 up = VR.roomScale().getCustomHMDVector(Vec3.createVectorHelper(0, 1, 0));
        if (head == null || look == null || up == null) {
            return null;
        }
        return new double[] {
                head.xCoord, head.yCoord, head.zCoord,
                look.xCoord, look.yCoord, look.zCoord,
                up.xCoord, up.yCoord, up.zCoord};
    }
}
