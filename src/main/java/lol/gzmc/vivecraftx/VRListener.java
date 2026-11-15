package lol.gzmc.vivecraftx;

import java.lang.reflect.Field;

import lol.gzmc.vivecraftx.api.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.util.Vec3;

import paulscode.sound.SoundSystem;

/**
 * Puts the ears on the head.
 *
 * <p>The game listens from the player's body, facing whichever way the body faces, because flat
 * there is no difference between the two. In room-scale there is: you can look along one wall
 * while your body faces another, and lean a metre out of yourself. Sound then arrives from the
 * wrong side, and turning your head does not move it -- which is more disorienting than it
 * sounds, because every other sense says it should have.
 *
 * <p>The listener is moved rather than the sounds, so distance, attenuation and everything else
 * the sound engine does carries on working untouched.
 *
 * <p>Reached by reflection, and found by type rather than by name. The field it needs is typed
 * as a private inner class, which a mixin cannot name and so cannot shadow -- the first attempt
 * at this crashed the game on startup for exactly that reason. Searching for the one field of
 * the right type has no name to get wrong, in either a development or a shipped game, and every
 * step of it is allowed to fail: if the sound engine is ever rearranged, this quietly does
 * nothing and the game keeps its own listener.
 */
public final class VRListener {

    private VRListener() {}

    private static Field soundManagerField;
    private static Field soundSystemField;
    private static boolean searched;
    private static boolean unavailable;

    /** Called once a frame, after the game has placed its own listener on the body. */
    public static void update() {
        if (unavailable || !VRState.running() || VR.roomScale() == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        try {
            SoundSystem system = soundSystem(mc);
            if (system == null) {
                return;
            }
            Vec3 head = VRRenderState.getEyeRenderPos(RenderPass.Center);
            Vec3 look = VR.roomScale().getHMDDir_World();
            if (head == null || look == null) {
                return;
            }
            // The up vector comes from the headset too, or tilting your head leaves the world
            // upright in your ears while it is tilted in your eyes.
            Vec3 up = VR.roomScale().getCustomHMDVector(Vec3.createVectorHelper(0, 1, 0));
            if (up == null) {
                up = Vec3.createVectorHelper(0, 1, 0);
            }
            system.setListenerPosition((float) head.xCoord, (float) head.yCoord,
                    (float) head.zCoord);
            system.setListenerOrientation(
                    (float) look.xCoord, (float) look.yCoord, (float) look.zCoord,
                    (float) up.xCoord, (float) up.yCoord, (float) up.zCoord);
        } catch (Throwable t) {
            // Once. A sound engine that cannot be reached will not become reachable later, and
            // complaining about it sixty times a second would be worse than the problem.
            unavailable = true;
            ViveCraftX.LOG.info("Sound will be heard from the body rather than the head: {}",
                    t.toString());
        }
    }

    /** The running sound engine, or null while there is not one yet. */
    private static SoundSystem soundSystem(Minecraft mc) throws IllegalAccessException {
        SoundHandler handler = mc.getSoundHandler();
        if (handler == null) {
            return null;
        }
        if (!searched) {
            searched = true;
            soundManagerField = fieldOfType(SoundHandler.class, SoundManager.class);
            soundSystemField = fieldOfType(SoundManager.class, SoundSystem.class);
            if (soundManagerField == null || soundSystemField == null) {
                unavailable = true;
                return null;
            }
        }
        Object manager = soundManagerField.get(handler);
        if (manager == null) {
            return null;
        }
        return (SoundSystem) soundSystemField.get(manager);
    }

    /**
     * The one field in a class that holds something of this type.
     *
     * <p>By type because names are obfuscated and the obfuscated name differs between a shipped
     * game and a development one, while the type does not. Null if there is more than one
     * candidate, since guessing between them would be worse than doing nothing.
     */
    private static Field fieldOfType(Class<?> owner, Class<?> type) {
        Field found = null;
        for (Field field : owner.getDeclaredFields()) {
            if (type.isAssignableFrom(field.getType())) {
                if (found != null) {
                    return null;
                }
                found = field;
            }
        }
        if (found != null) {
            found.setAccessible(true);
        }
        return found;
    }
}
