package lol.gzmc.vivecraftx.net;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Poses on their way from one player to everyone who can see them, sent on the server's thread.
 *
 * <p>For the same reason the teleport requests next door are queued: a packet arrives on a
 * network thread, and this version of the game offers no way to hand work back to the server
 * thread. Two things went wrong with doing the relay there. The list of players in a world is
 * walked to decide who is near enough, and the server thread adds to and removes from that list
 * whenever somebody joins, leaves, dies or changes dimension -- so the walk could fail part way
 * through and take that tick's relay down with it, for everybody, not only for the player whose
 * arrival caused it. And the channel the poses go out on carries the recipient in a field it sets
 * just before writing, so two players' network threads relaying at the same moment could each
 * overwrite the other's recipient and send a pose to the wrong client.
 *
 * <p>One tick of delay, which for a pose that is interpolated at the far end anyway is nothing,
 * against a race with everything else that touches a world's player list.
 */
public final class VRPoseRelay {

    private VRPoseRelay() {}

    /** How far away a pose is still worth sending. The same bound the handler used to apply. */
    private static final double RANGE = VRNetwork.POSE_RANGE;

    private static final class Pending {
        private final EntityPlayerMP sender;
        private final VRPose pose;

        private Pending(EntityPlayerMP sender, VRPose pose) {
            this.sender = sender;
            this.pose = pose;
        }
    }

    private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<Pending>();

    /**
     * Takes a pose to relay on the next tick.
     *
     * <p>The pose is handed over whole and not reused by the caller, so the queue holds the only
     * reference to it by the time this returns.
     */
    static void queue(EntityPlayerMP sender, VRPose pose) {
        PENDING.add(new Pending(sender, pose));
    }

    /** One handler for the server bus, registered whichever side this is. */
    public static class Ticker {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Pending pending;
            while ((pending = PENDING.poll()) != null) {
                try {
                    relay(pending);
                } catch (Throwable t) {
                    ViveCraftX.LOG.debug("A VR pose could not be relayed", t);
                }
            }
        }

        private static void relay(Pending pending) {
            EntityPlayerMP sender = pending.sender;
            if (sender == null || sender.isDead || sender.worldObj == null) {
                return;
            }
            // Nothing is kept: a pose is only worth anything for the frame it describes, and a
            // player who stops sending should stop being posed rather than freeze where they were.
            VRNetwork.ToWatchers out =
                    new VRNetwork.ToWatchers(sender.getEntityId(), pending.pose);
            for (Object o : sender.worldObj.playerEntities) {
                EntityPlayer other = (EntityPlayer) o;
                if (other == sender || !(other instanceof EntityPlayerMP)) {
                    continue;
                }
                if (other.getDistanceSqToEntity(sender) > RANGE * RANGE) {
                    continue;
                }
                VRNetwork.sendToOne(out, (EntityPlayerMP) other);
            }
        }
    }
}
