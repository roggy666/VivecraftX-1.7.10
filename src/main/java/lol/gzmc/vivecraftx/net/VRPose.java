package lol.gzmc.vivecraftx.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.util.Vec3;

/**
 * Where somebody's head and hands are, relative to where they are standing.
 *
 * <p>Relative rather than absolute because the two ends of the wire do not agree about where a
 * player is to the centimetre -- the position is interpolated on the receiving side and arrives a
 * moment late. An absolute head position would then be a few centimetres away from the body it
 * belongs to, and would swim about as the body caught up. An offset from the feet does not care.
 *
 * <p>Rotations travel as quaternions. Three angles would be smaller but would have to agree on an
 * order to be applied in, and would snag when a player looks straight up or down, which in VR is
 * a thing people do constantly.
 */
public final class VRPose {

    /** Head, main hand, off hand. */
    public final Vec3[] positions = new Vec3[3];
    public final float[][] rotations = new float[3][4];

    /** Set while the sender is actually in VR; false means draw them as an ordinary player. */
    public boolean inVr;

    /** The sender plays with their hands swapped, so their main hand is their left. */
    public boolean reversed;

    /**
     * The sender is playing seated, with a mouse rather than with their body.
     *
     * <p>Their controllers are then wherever they have been put down, which is nowhere useful,
     * so a figure posed from them would stand like a puppet with its strings cut. Upstream sends
     * this for the same reason and uses it the same way.
     */
    public boolean seated;

    /** How large the world is for them, which is how large they are to everyone else. */
    public float worldScale = 1F;

    public VRPose() {
        for (int i = 0; i < 3; i++) {
            positions[i] = Vec3.createVectorHelper(0, 0, 0);
            rotations[i][0] = 1F;
        }
    }

    public void write(ByteBuf buf) {
        buf.writeBoolean(this.inVr);
        buf.writeBoolean(this.reversed);
        buf.writeBoolean(this.seated);
        buf.writeFloat(this.worldScale);
        for (int i = 0; i < 3; i++) {
            buf.writeFloat((float) this.positions[i].xCoord);
            buf.writeFloat((float) this.positions[i].yCoord);
            buf.writeFloat((float) this.positions[i].zCoord);
            for (int j = 0; j < 4; j++) {
                buf.writeFloat(this.rotations[i][j]);
            }
        }
    }

    /**
     * @return false when the payload is not a whole pose, in which case nothing has been changed
     */
    public boolean read(ByteBuf buf) {
        // Checked before anything is read, because reading past the end throws, and this runs on
        // the network thread where a throw costs the whole tick's relay for everybody rather than
        // just the malformed packet.
        if (buf.readableBytes() < BYTES) {
            return false;
        }
        this.inVr = buf.readBoolean();
        this.reversed = buf.readBoolean();
        this.seated = buf.readBoolean();
        this.worldScale = buf.readFloat();
        for (int i = 0; i < 3; i++) {
            this.positions[i] = Vec3.createVectorHelper(buf.readFloat(), buf.readFloat(),
                    buf.readFloat());
            for (int j = 0; j < 4; j++) {
                this.rotations[i][j] = buf.readFloat();
            }
        }
        return true;
    }

    /** Bytes on the wire, for sizing a buffer. */
    public static final int BYTES = 1 + 1 + 1 + 4 + 3 * (3 * 4 + 4 * 4);
}
