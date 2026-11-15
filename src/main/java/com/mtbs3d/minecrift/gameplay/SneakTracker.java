package com.mtbs3d.minecrift.gameplay;

import lol.gzmc.vivecraftx.VR;

import com.mtbs3d.minecrift.api.IRoomscaleAdapter;
import com.mtbs3d.minecrift.provider.MCOpenVR;

import com.mtbs3d.minecrift.settings.AutoCalibration;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;


public class SneakTracker {
	public boolean sneakOverride=false;

	public boolean isActive(EntityPlayerSP p){
		if(VR.settings().seated)
			return false;
		// A fixed view height pins the head at the player's standing height and throws the
		// headset's own height away, so crouching no longer moves it: there is nothing here left
		// to measure, and a reading taken anyway describes the setting rather than the player.
		// AutoCalibration stops learning in this mode for the same reason.
		if(VR.settings().vrFixedHeight)
			return false;
		if(!VR.settings().vrFreeMove && !VR.settings().simulateFalling)
			return false;
		if(!VR.settings().realisticSneakEnabled)
			return false;
		if(p==null || p.isDead || !p.onGround)
			return false;
		if(p.isRiding())
			return false;
		return true;
	}

	public void doProcess(Minecraft minecraft, EntityPlayerSP player){
		if(!isActive(player)) {
			sneakOverride = false;
			return;
		}

		// Measured against the player, not against the view. The history holds positions that
		// already carry however far the view is crouched, and the view is crouched because of
		// what this method decided last time -- so comparing the history raw lets our own drop
		// satisfy the test that causes it. That closes a loop, and a loop with a threshold in it
		// does not settle: it crosses, reverses, and crosses back, which is what the player sees
		// as the camera being pulled up again as they go down.
		double headY = MCOpenVR.hmdPivotHistory.latest().yCoord + MCOpenVR.getViewCrouchDrop();

	    if(( AutoCalibration.getPlayerHeight() - headY )> VR.settings().sneakThreshold){
		   sneakOverride=true;
	    }else{
		    sneakOverride=false;
	    }
	}

}
