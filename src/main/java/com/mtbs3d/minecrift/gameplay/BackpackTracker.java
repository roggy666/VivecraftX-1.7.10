package com.mtbs3d.minecrift.gameplay;

import lol.gzmc.vivecraftx.util.Vec3Util;

import lol.gzmc.vivecraftx.VR;

import com.mtbs3d.minecrift.api.IRoomscaleAdapter;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

import java.util.Random;

/**
 * Created by cincodenada on 13-May-17.
 */
public class BackpackTracker {
	public boolean[] wasIn = new boolean[2];
	public int previousSlot = -1;
	
	public boolean isActive(EntityPlayerSP p){
		if(VR.settings().seated)
			return false;
		if(p == null) return false;
		if(p.isDead) return false;
		if(p.isPlayerSleeping()) return false;
		return true;
	}

	
	private Vec3 down = Vec3.createVectorHelper(0, -1, 0);
	
	public void doProcess(Minecraft minecraft, EntityPlayerSP player){
		if(!isActive(player)) {
			return;
		}
		IRoomscaleAdapter provider = VR.roomScale();

		Vec3 hmdPos=provider.getHMDPos_Room();

		for(int c=0; c<1; c++) { //just main for 1710, no dual wielding
			Vec3 controllerPos = provider.getControllerPos_Room(c);
			// Room space throughout. The hand is being compared with the head, and the two move
			// through the world together; measuring one of them in the world would make this
			// depend on where the play area happens to be standing.
			Vec3 controllerDir = provider.getControllerDir_Room(c);
			Vec3 hmddir = VR.player().getHMDDir_Room();
			Vec3 delta = Vec3Util.subtract(hmdPos, controllerPos);
			double dot = controllerDir.dotProduct(down);
			double dotDelta = delta.dotProduct(hmddir);

			// Reaching over your shoulder is three things at once, and it needs all three: the
			// hand level with your head, behind it, and pointing down your back. The last was
			// worked out and then never looked at, which left the gesture as "hand somewhere
			// behind your head" -- and your hand is behind your head a great deal of the time.
			boolean below = Math.abs(hmdPos.yCoord - controllerPos.yCoord) < 0.25;
			boolean behind = dotDelta > 0 && delta.lengthVector() > 0.05;
			boolean aimDown = dot > 0.6;

			boolean zone = below && behind && aimDown;

			if (zone){
				if(!wasIn[c]){
					if(player.inventory.currentItem != 0){
						previousSlot = player.inventory.currentItem;
						player.inventory.currentItem = 0;
					} else if (previousSlot > 0 && previousSlot < 9) {
						// Only if there is something to go back to. Reaching over your shoulder
						// with slot one already selected -- which is where a new world starts you
						// -- used to put the held slot at minus one, and the next tick of holding
						// left-click indexes the hotbar array with it and takes the game down.
						player.inventory.currentItem = previousSlot;
						previousSlot = -1;
					}
					MCOpenVR.triggerHapticPulse(c, 1500);
					wasIn[c] = true;
				}
			} else if ((dotDelta < 0 && delta.lengthVector() > 0.25) || dot < 0) {
				// Armed again only once the hand is well clear in front, or pointing up.
				// Without that, a hand resting near the edge of the zone swaps the held slot
				// back and forth every time it drifts a centimetre.
				wasIn[c] = false;
			}
		}
}

}
