package lol.gzmc.vivecraftx.net;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Teleports asked for by players, carried out on the server's own thread.
 *
 * <p>Packets arrive on a network thread. Later versions of the game offer a way to hand work
 * back to the server thread; this one does not, so the request waits in a queue until the server
 * ticks. One tick of delay, against a race with everything else that touches a player.
 */
public final class VRServerTeleports {

    private VRServerTeleports() {}

    private static final class Request {
        private final EntityPlayerMP player;
        private final float x;
        private final float y;
        private final float z;

        private Request(EntityPlayerMP player, float x, float y, float z) {
            this.player = player;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Queue<Request> PENDING = new ConcurrentLinkedQueue<Request>();

    static void queue(EntityPlayerMP player, float x, float y, float z) {
        PENDING.add(new Request(player, x, y, z));
    }

    /** One handler for the server bus, registered whichever side this is. */
    public static class Ticker {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Request request;
            while ((request = PENDING.poll()) != null) {
                try {
                    apply(request);
                } catch (Throwable t) {
                    ViveCraftX.LOG.warn("A teleport could not be carried out", t);
                }
            }
        }

        private static void apply(Request request) {
            EntityPlayerMP player = request.player;
            if (player == null || player.isDead || player.playerNetServerHandler == null) {
                return;
            }
            if (VRServerConfig.teleportCostsFood && request.y > player.posY) {
                player.addExhaustion(0.05F);
            }
            // Through the connection rather than by setting the position, so the client is told
            // as well and the two do not disagree about where the player now is.
            player.playerNetServerHandler.setPlayerLocation(request.x, request.y, request.z,
                    player.rotationYaw, player.rotationPitch);
            player.fallDistance = 0F;
        }
    }
}
