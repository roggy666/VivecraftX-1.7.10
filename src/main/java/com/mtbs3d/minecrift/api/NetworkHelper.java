package com.mtbs3d.minecrift.api;

import lol.gzmc.vivecraftx.VR;
import net.minecraft.client.entity.EntityPlayerSP;
import lol.gzmc.vivecraftx.net.VRPose;
import lol.gzmc.vivecraftx.net.VRNetwork;
import lol.gzmc.vivecraftx.VRState;
import de.fruitfly.ovr.structs.Quatf;
import jopenvr.OpenVRUtil;
import com.mtbs3d.minecrift.provider.MCOpenVR;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.apache.commons.lang3.ArrayUtils;
import org.lwjgl.util.vector.Matrix4f;

import com.google.common.base.Charsets;
import com.mtbs3d.minecrift.utils.Quaternion;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.util.Vec3;

public class NetworkHelper {

	public enum PacketDiscriminators {
		VERSION,
		REQUESTDATA,
		HEADDATA,
		CONTROLLER0DATA,
		CONTROLLER1DATA,
		WORLDSCALE,
		DRAW,
		MOVEMODE,
		UBERPACKET
	}

	private final static String channel = "Vivecraft";
	
	public static C17PacketCustomPayload getVivecraftClientPacket(PacketDiscriminators command, byte[] payload)
	{
		payload = ArrayUtils.add(payload,0,(byte)command.ordinal());
        return (new C17PacketCustomPayload(channel, payload));
	}
	
	public static S3FPacketCustomPayload getVivecraftServerPacket(PacketDiscriminators command, byte[] payload)
	{
		payload = ArrayUtils.add(payload,0,(byte)command.ordinal());
        return (new S3FPacketCustomPayload(channel, payload));
	}
	
	/**
	 * Tells everyone nearby where this player's head and hands are.
	 *
	 * <p>Everything is sent relative to the feet, because the two ends disagree about exactly
	 * where a player is -- the position is interpolated on the receiving side and arrives a
	 * moment late -- and an absolute head position would float a few centimetres off the body it
	 * belongs to.
	 */
	public static void sendVRPlayerPositions(IRoomscaleAdapter player) {
		EntityPlayerSP self = Minecraft.getMinecraft().thePlayer;
		if (self == null || !VRState.running()) return;

		VRPose pose = new VRPose();
		pose.inVr = true;
		pose.reversed = VR.settings().vrReverseHands;
		pose.seated = VR.settings().seated;
		pose.worldScale = VR.player().worldScale;

		float worldRot = VR.player().worldRotationRadians;
		de.fruitfly.ovr.structs.Matrix4f turn = de.fruitfly.ovr.structs.Matrix4f.rotationY(worldRot);

		put(pose, 0, player.getHMDPos_World(), de.fruitfly.ovr.structs.Matrix4f.multiply(turn,
				MCOpenVR.hmdRotation), self);
		for (int c = 0; c < 2; c++) {
			put(pose, c + 1, player.getControllerPos_World(c),
					de.fruitfly.ovr.structs.Matrix4f.multiply(turn, MCOpenVR.getAimRotation(c)),
					self);
		}
		VRNetwork.send(pose);
	}

	/**
	 * Says, once, that this player is no longer in VR.
	 *
	 * <p>Otherwise leaving VR is communicated only by going quiet, and going quiet is also what
	 * a dropped connection looks like: everyone else would keep the last pose for a second and
	 * then watch the arms snap back. With hot-switching that happens every time somebody puts
	 * their headset down, which is often.
	 */
	public static void sendLeftVR() {
		if (Minecraft.getMinecraft().thePlayer == null) return;
		VRPose pose = new VRPose();
		pose.inVr = false;
		VRNetwork.send(pose);
	}

	private static void put(VRPose pose, int slot, Vec3 world,
			de.fruitfly.ovr.structs.Matrix4f rotation, EntityPlayerSP self) {
		if (world == null) return;
		pose.positions[slot] = Vec3.createVectorHelper(world.xCoord - self.posX,
				world.yCoord - self.boundingBox.minY, world.zCoord - self.posZ);
		Quatf q = OpenVRUtil.convertMatrix4ftoRotationQuat(rotation);
		pose.rotations[slot][0] = q.w;
		pose.rotations[slot][1] = q.x;
		pose.rotations[slot][2] = q.y;
		pose.rotations[slot][3] = q.z;
	}
}
