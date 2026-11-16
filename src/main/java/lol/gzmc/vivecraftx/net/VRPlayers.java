package lol.gzmc.vivecraftx.net;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.Vec3;

/**
 * Where everyone else's head and hands are.
 *
 * <p>Poses arrive on the network thread and are read while drawing, so the map is concurrent.
 * They are not handed over to the client thread to be applied at a tick boundary, because the
 * only thing that would buy is a frame of lag.
 *
 * <p>Keyed by entity id rather than by name or UUID, because that is what the packet carries and
 * what the renderer has in its hand. An id is reused once its owner is gone, which is what the
 * staleness check is for: a pose nobody has refreshed in a second is dropped, so a recycled id
 * cannot inherit somebody else's hands.
 *
 * <p>Two poses are kept per player, not one. They arrive twenty times a second and are drawn
 * ninety or more, so a single pose means hands that jump from position to position while the
 * body they belong to glides smoothly between them -- which looks worse than no tracking at all,
 * because the jerk draws the eye. Keeping the previous one as well lets the drawing side ask
 * where a hand was partway between the two, exactly as the game does for the body.
 */
public final class VRPlayers {

    private VRPlayers() {}

    /** A pose nobody has refreshed in this long is forgotten. */
    private static final long STALE_AFTER = 1000L;

    private static final Map<Integer, Tracked> TRACKED = new ConcurrentHashMap<Integer, Tracked>();

    /** One player's recent history: what arrived last, and the two the drawing side blends. */
    private static final class Tracked {
        volatile VRPose latest;
        volatile VRPose previous;
        volatile VRPose current;
        volatile long received;
    }

    /** Scratch for the blended result. Rendering is single-threaded, and this is handed out. */
    private static final VRPose BLENDED = new VRPose();

    public static void put(int entityId, VRPose pose) {
        Tracked tracked = TRACKED.get(Integer.valueOf(entityId));
        if (tracked == null) {
            tracked = new Tracked();
            // A player who has just appeared has no history to blend, so both ends of the blend
            // are the one pose we have; the next tick gives it something to move from.
            tracked.previous = pose;
            tracked.current = pose;
            TRACKED.put(Integer.valueOf(entityId), tracked);
        }
        tracked.latest = pose;
        tracked.received = System.currentTimeMillis();
    }

    /**
     * Moves every player's history on by one tick.
     *
     * <p>Called once a client tick. Poses arrive whenever the network delivers them, which is
     * near enough to once a tick but not aligned to anything; pinning them to the tick clock
     * here is what lets the drawing side use the same partial tick the rest of the game does.
     */
    public static void tick() {
        for (Tracked tracked : TRACKED.values()) {
            VRPose latest = tracked.latest;
            if (latest != null) {
                tracked.previous = tracked.current == null ? latest : tracked.current;
                tracked.current = latest;
            }
        }
    }

    /** The last pose received, unblended. Null when there is none, or it has gone stale. */
    public static VRPose get(int entityId) {
        Tracked tracked = live(entityId);
        return tracked == null ? null : tracked.current;
    }

    /**
     * Where this player was partway through the current tick.
     *
     * <p>Returns a shared object, valid until the next call. Callers use it and let it go; they
     * do not keep it.
     */
    public static VRPose get(int entityId, float partialTicks) {
        Tracked tracked = live(entityId);
        if (tracked == null) {
            return null;
        }
        VRPose from = tracked.previous;
        VRPose to = tracked.current;
        if (from == null || to == null) {
            return to;
        }
        if (from == to) {
            return to;
        }
        blend(from, to, partialTicks < 0F ? 0F : partialTicks > 1F ? 1F : partialTicks);
        return BLENDED;
    }

    private static Tracked live(int entityId) {
        Tracked tracked = TRACKED.get(Integer.valueOf(entityId));
        if (tracked == null) {
            return null;
        }
        if (System.currentTimeMillis() - tracked.received > STALE_AFTER) {
            TRACKED.remove(Integer.valueOf(entityId));
            return null;
        }
        VRPose current = tracked.current;
        return current != null && current.inVr ? tracked : null;
    }

    private static void blend(VRPose from, VRPose to, float t) {
        BLENDED.inVr = to.inVr;
        BLENDED.reversed = to.reversed;
        // Seated has to come across with the rest of it. Left out, the blended pose always says
        // standing, and a seated player's arms get posed from two controllers lying on a desk --
        // which is the worst case of the arms leaving the body, because a desk is nowhere near
        // where a shoulder is.
        BLENDED.seated = to.seated;
        BLENDED.worldScale = from.worldScale + (to.worldScale - from.worldScale) * t;
        for (int i = 0; i < 3; i++) {
            Vec3 a = from.positions[i];
            Vec3 b = to.positions[i];
            BLENDED.positions[i] = Vec3.createVectorHelper(
                    a.xCoord + (b.xCoord - a.xCoord) * t,
                    a.yCoord + (b.yCoord - a.yCoord) * t,
                    a.zCoord + (b.zCoord - a.zCoord) * t);
            slerpish(from.rotations[i], to.rotations[i], t, BLENDED.rotations[i]);
        }
    }

    /**
     * Blends two rotations.
     *
     * <p>Straight interpolation of the four numbers, then back onto the unit sphere. A proper
     * slerp would hold the angular speed constant through the blend; between two poses a
     * twentieth of a second apart the difference is far below what an eye can see, and this
     * costs no trigonometry per hand per frame.
     *
     * <p>The sign check matters more than the maths: a rotation and its negation describe the
     * same orientation, so without it a hand that crosses that boundary takes the long way round
     * and spins through a full turn in one tick.
     */
    private static void slerpish(float[] from, float[] to, float t, float[] out) {
        float dot = from[0] * to[0] + from[1] * to[1] + from[2] * to[2] + from[3] * to[3];
        float sign = dot < 0F ? -1F : 1F;
        float length = 0F;
        for (int i = 0; i < 4; i++) {
            out[i] = from[i] + (to[i] * sign - from[i]) * t;
            length += out[i] * out[i];
        }
        if (length <= 1.0E-8F) {
            out[0] = 1F;
            out[1] = 0F;
            out[2] = 0F;
            out[3] = 0F;
            return;
        }
        length = (float) Math.sqrt(length);
        for (int i = 0; i < 4; i++) {
            out[i] /= length;
        }
    }

    public static void clear() {
        TRACKED.clear();
    }

    /** Drops whoever has stopped talking, so the map does not grow for the whole session. */
    public static void expire() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Tracked>> it = TRACKED.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().received > STALE_AFTER) {
                it.remove();
            }
        }
    }
}
