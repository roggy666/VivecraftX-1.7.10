package lol.gzmc.vivecraftx.net;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

import io.netty.buffer.ByteBuf;

import lol.gzmc.vivecraftx.ViveCraftX;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Sends each VR player's head and hands to everyone who can see them.
 *
 * <p>Without this a player in a headset looks, to everybody else, like someone standing perfectly
 * still and facing one way: the game only ever had a body position and two angles to describe a
 * player with, and a headset produces far more than that. Two people in the same world could not
 * wave at each other, point at anything, or tell where the other was looking.
 *
 * <p>The relay goes through the server because clients cannot talk to each other directly, and it
 * is sent only to players close enough to see the sender, so a busy server is not paying to
 * deliver hand positions across the map. A player whose client does not have this mod sends
 * nothing and is drawn exactly as before, which is also what happens to anyone whose data has
 * stopped arriving.
 */
public final class VRNetwork {

    private VRNetwork() {}

    private static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("vivecraftx");

    /** Past this, the sender is out of sight and their hands are nobody's business. */
    private static final double RANGE = 64.0D;

    /** The same bound, for the relay that now does the sending. */
    static final double POSE_RANGE = RANGE;

    /** Sends one message to one player. Only the server thread may call this. */
    static void sendToOne(IMessage message, EntityPlayerMP player) {
        CHANNEL.sendTo(message, player);
    }

    public static void init() {
        CHANNEL.registerMessage(FromPlayer.Handler.class, FromPlayer.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ToWatchers.Handler.class, ToWatchers.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(Rules.Handler.class, Rules.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(TeleportTo.Handler.class, TeleportTo.class, 3, Side.SERVER);
    }

    /** Called on the client, once a tick, with where this player's head and hands are. */
    public static void send(VRPose pose) {
        CHANNEL.sendToServer(new FromPlayer(pose));
    }

    /** Asks the server to put the player somewhere, because the client may not do it itself. */
    public static void requestTeleport(double x, double y, double z) {
        CHANNEL.sendToServer(new TeleportTo((float) x, (float) y, (float) z));
    }

    /** Tells one player what this server allows. Sent as they join. */
    public static void sendRules(EntityPlayerMP player) {
        CHANNEL.sendTo(new Rules(), player);
    }

    /** What a player tells the server about themselves. */
    public static class FromPlayer implements IMessage {

        private VRPose pose = new VRPose();

        public FromPlayer() {}

        public FromPlayer(VRPose pose) {
            this.pose = pose;
        }

        private boolean whole;

        @Override
        public void fromBytes(ByteBuf buf) {
            this.whole = this.pose.read(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            this.pose.write(buf);
        }

        public static class Handler implements IMessageHandler<FromPlayer, IMessage> {
            @Override
            public IMessage onMessage(FromPlayer message, MessageContext ctx) {
                if (!message.whole) {
                    return null;
                }
                EntityPlayerMP sender = ctx.getServerHandler().playerEntity;
                if (sender == null || sender.worldObj == null) {
                    return null;
                }
                // Handed to the server thread rather than fanned out from here. See VRPoseRelay
                // for why: this runs on a network thread, and both the world's player list and
                // the channel's one-recipient-at-a-time send belong to the server's.
                VRPoseRelay.queue(sender, message.pose);
                return null;
            }
        }
    }

    /**
     * What this server allows, sent to each player as they arrive.
     *
     * <p>Receiving it is also how a client learns that the server has this mod at all, which is
     * what decides whether a teleport is something it may do itself or something it must ask
     * for.
     */
    public static class Rules implements IMessage {

        private boolean teleport = true;
        private boolean forced;
        private int up = 1;
        private int down = 4;
        private int horiz = 16;

        public Rules() {
            this.teleport = VRServerConfig.teleportAllowed;
            this.forced = VRServerConfig.teleportLimitsForced;
            this.up = VRServerConfig.teleportUpLimit;
            this.down = VRServerConfig.teleportDownLimit;
            this.horiz = VRServerConfig.teleportHorizLimit;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.teleport = buf.readBoolean();
            this.forced = buf.readBoolean();
            this.up = buf.readInt();
            this.down = buf.readInt();
            this.horiz = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(this.teleport);
            buf.writeBoolean(this.forced);
            buf.writeInt(this.up);
            buf.writeInt(this.down);
            buf.writeInt(this.horiz);
        }

        public static class Handler implements IMessageHandler<Rules, IMessage> {
            @Override
            public IMessage onMessage(Rules message, MessageContext ctx) {
                VRServerRules.accept(message.teleport, message.forced, message.up, message.down,
                        message.horiz);
                return null;
            }
        }
    }

    /**
     * A player asking to be put somewhere.
     *
     * <p>A teleport is movement the client decides on, and a client that simply moved itself
     * ten blocks would be snapped back by the server's own cheat check -- which is right, since
     * that is what moving ten blocks in a tick usually means. So the server does the moving,
     * which also gives it the final say over whether to do it at all.
     */
    public static class TeleportTo implements IMessage {

        private float x;
        private float y;
        private float z;

        public TeleportTo() {}

        public TeleportTo(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.x = buf.readFloat();
            this.y = buf.readFloat();
            this.z = buf.readFloat();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeFloat(this.x);
            buf.writeFloat(this.y);
            buf.writeFloat(this.z);
        }

        public static class Handler implements IMessageHandler<TeleportTo, IMessage> {
            @Override
            public IMessage onMessage(TeleportTo message, MessageContext ctx) {
                if (!VRServerConfig.teleportAllowed) {
                    return null;
                }
                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
                if (player == null || player.worldObj == null) {
                    return null;
                }
                // Queued rather than done here. This arrives on a network thread, and this
                // version of the game has no way to hand work to the server thread, so moving a
                // player from here would race everything else touching them this tick.
                VRServerTeleports.queue(player, message.x, message.y, message.z);
                return null;
            }
        }
    }

    /** What everyone nearby is told about that player. */
    public static class ToWatchers implements IMessage {

        private int entityId;
        private VRPose pose = new VRPose();

        public ToWatchers() {}

        public ToWatchers(int entityId, VRPose pose) {
            this.entityId = entityId;
            this.pose = pose;
        }

        private boolean whole;

        @Override
        public void fromBytes(ByteBuf buf) {
            if (buf.readableBytes() < 4 + VRPose.BYTES) {
                return;
            }
            this.entityId = buf.readInt();
            this.whole = this.pose.read(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.entityId);
            this.pose.write(buf);
        }

        public static class Handler implements IMessageHandler<ToWatchers, IMessage> {
            @Override
            public IMessage onMessage(ToWatchers message, MessageContext ctx) {
                if (!message.whole) {
                    return null;
                }
                try {
                    VRPlayers.put(message.entityId, message.pose);
                } catch (Throwable t) {
                    ViveCraftX.LOG.debug("A VR pose could not be stored", t);
                }
                return null;
            }
        }
    }
}
