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

	    if(( AutoCalibration.getPlayerHeight() - MCOpenVR.hmdPivotHistory.latest().yCoord )> VR.settings().sneakThreshold){
		   sneakOverride=true;
	    }else{
		    sneakOverride=false;
	    }
	}

}
