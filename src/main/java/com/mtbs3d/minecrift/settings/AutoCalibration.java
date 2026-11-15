package com.mtbs3d.minecrift.settings;

import lol.gzmc.vivecraftx.VR;

import lol.gzmc.vivecraftx.util.ChatUtil;

import com.mtbs3d.minecrift.provider.MCOpenVR;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class AutoCalibration {

	static double magnetDistance=0.05;
	static int weightCap =100;
	public static MagnetPoint currentCalibration;
	public static final float defaultHeight=1.65f;

	static Set<MagnetPoint> magnetSet=new HashSet<MagnetPoint>();

	public static void logHeadPos(Vec3 headpos){
		// With the view height held fixed, the head is wherever this says it should be, so
		// learning from it would only teach it what it already believes -- and if the player
		// calibrated while standing and then lay down, it would unlearn that.
		if(VR.settings()!=null && VR.settings().vrFixedHeight) return;
		double y=headpos.yCoord;
		MagnetPoint p =findPoint(y);
		if(p==null){
			p=new MagnetPoint();
			p.center=y;
			p.weight=1;
			magnetSet.add(p);
		}else{
			p.center=(p.center*0.75+y*0.25);
			applySucc(p);
			applyHeightBonus();
			if(p.weight > weightCap)
				p.weight = weightCap;
		}

		getBestEstimate();
	}

	private static void applyHeightBonus() {
		double highest=-1;
		MagnetPoint href=null;
		for (MagnetPoint m: magnetSet) {
			if(highest<m.center){
				href=m;
				highest=m.center;
			}
		}
		Iterator<MagnetPoint> i=magnetSet.iterator();
		while(i.hasNext()){
			MagnetPoint m=i.next();
			if(m.equals(href)){
				if(m.succ>0)
					m.weight++;
				else
					if(m.weight<weightCap){
						m.weight--;
						if(m.weight<0)
							i.remove();
					}
			} else {
				m.weight--;
				if(m.weight<0)
					i.remove();
			}
		}
	}

	static void applySucc(MagnetPoint p){
		for (MagnetPoint m: magnetSet) {
			if(m.equals(p))
				m.succ++;
			else
				m.succ=0;
		}
	}

	static MagnetPoint findPoint(double y){
		for (MagnetPoint m: magnetSet) {
			if(Math.abs(y-m.center)<magnetDistance){
				return m;
			}
		}
		return null;
	}


	static void getBestEstimate(){
		double highest=-1;
		MagnetPoint href=null;

		for (MagnetPoint m : magnetSet) {
			if (highest < m.weight) {
				href = m;
				highest = m.weight;
			}
		}

		currentCalibration=href;
	}

	public static void calibrateManual() {
		Minecraft mc=Minecraft.getMinecraft();
		VR.settings().manualCalibration=(float) MCOpenVR.hmdPivotHistory.latest().yCoord;
		ChatUtil.print(String.format("User height set to %.2fm",VR.settings().manualCalibration));
	}

	public static class MagnetPoint{
		public double center;
		public int weight;
		int succ;
	}

	/**
	 * The player's standing height as it was last settled on, not as it looks right now.
	 *
	 * <p>For the fixed view height, which works by holding the head at the player's standing
	 * height. Asking the live estimate for that would be circular -- the estimate is fed by the
	 * head position, and the head position would then be set from the estimate -- and worse, a
	 * player who starts the session lying down would teach it that lying down is their height.
	 */
	public static float getStoredPlayerHeight(){
		if(VR.settings().manualCalibration!=-1)
			return VR.settings().manualCalibration;
		if(VR.settings().autoCalibration!=-1)
			return VR.settings().autoCalibration;
		return defaultHeight;
	}

	public static float getPlayerHeight(){
		Minecraft mc=Minecraft.getMinecraft();
		if(VR.settings().manualCalibration!=-1)
			return VR.settings().manualCalibration;
		if(currentCalibration==null || currentCalibration.weight<weightCap) {
			if (VR.settings().autoCalibration != -1)
				return VR.settings().autoCalibration;
			else
				return defaultHeight;
		}
		return (float) currentCalibration.center;
	}
}
