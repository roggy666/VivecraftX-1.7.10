/**
* Copyright 2013 Mark Browning, StellaArtois
* Licensed under the LGPL 3.0 or later (See LICENSE.md for details)
*/
package com.mtbs3d.minecrift.settings;

import lol.gzmc.vivecraftx.VR;

import lol.gzmc.vivecraftx.util.ChatUtil;

import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.provider.OpenVRPlayer;
import com.mtbs3d.minecrift.settings.VRSettings;
import com.mtbs3d.minecrift.utils.Quaternion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.util.Vec3;

import java.nio.FloatBuffer;

import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Matrix4f;

public class VRHotkeys {

	static long nextRead = 0;
	static final long COOLOFF_PERIOD_MILLIS = 500;

	public static boolean handleKeyboardInputs(Minecraft mc)
	{
		// Support cool-off period for key presses - otherwise keys can get spammed...
		if (nextRead != 0 && System.currentTimeMillis() < nextRead)
		return false;

		// Capture Minecrift key events
		boolean gotKey = false;

		// Debug aim
		if (Keyboard.getEventKey() == Keyboard.KEY_RSHIFT && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
		{
			VR.settings().storeDebugAim = true;
			ChatUtil.print("Show aim (RCTRL+RSHIFT): done");
			gotKey = true;
		}

		// Walk up blocks
		if (Keyboard.getEventKey() == Keyboard.KEY_B && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
		{
			VR.settings().walkUpBlocks = !VR.settings().walkUpBlocks;
			ChatUtil.print("Walk up blocks (RCTRL+B): " + (VR.settings().walkUpBlocks ? "YES" : "NO"));
			gotKey = true;
		}

		// Player inertia
		if (Keyboard.getEventKey() == Keyboard.KEY_I && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL))
		{
			VR.settings().inertiaFactor += 1;
			if (VR.settings().inertiaFactor > VRSettings.INERTIA_MASSIVE)
			VR.settings().inertiaFactor = VRSettings.INERTIA_NONE;
			switch (VR.settings().inertiaFactor)
			{
			case VRSettings.INERTIA_NONE:
				ChatUtil.print("Player player movement inertia (LCTRL-I): None");
				break;
			case VRSettings.INERTIA_NORMAL:
				ChatUtil.print("Player player movement inertia (LCTRL-I): Normal");
				break;
			case VRSettings.INERTIA_LARGE:
				ChatUtil.print("Player player movement inertia (LCTRL-I): Large");
				break;
			case VRSettings.INERTIA_MASSIVE:
				ChatUtil.print("Player player movement inertia (LCTRL-I): Massive");
				break;
			}
			gotKey = true;
		}

		// Render full player model or just an disembodied hand...
		if (Keyboard.getEventKey() == Keyboard.KEY_H && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
		{
			VR.settings().renderFullFirstPersonModelMode++;
			if (VR.settings().renderFullFirstPersonModelMode > VRSettings.RENDER_FIRST_PERSON_NONE)
			VR.settings().renderFullFirstPersonModelMode = VRSettings.RENDER_FIRST_PERSON_FULL;

				switch (VR.settings().renderFullFirstPersonModelMode)
			{
			case VRSettings.RENDER_FIRST_PERSON_FULL:
				ChatUtil.print("First person model (RCTRL-H): Full");
				break;
			case VRSettings.RENDER_FIRST_PERSON_HAND:
				ChatUtil.print("First person model (RCTRL-H): Hand");
				break;
			case VRSettings.RENDER_FIRST_PERSON_NONE:
				ChatUtil.print("First person model (RCTRL-H): None");
				break;
			}
			gotKey = true;
		}
		// VIVE START - hotkeys

		// Testing different movement styles
		if (Keyboard.getEventKey() == Keyboard.KEY_M && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
		{
			// JRBUDDA ADDED all dis.
			if (VR.player().getFreeMoveMode()) {
				//cycle restricted movement styles
				if (VR.player().useLControllerForRestricedMovement) {
					VR.player().useLControllerForRestricedMovement = false;
					ChatUtil.print("Restricted movement mode set to gaze");
				} else {
					VR.player().useLControllerForRestricedMovement = true;
					ChatUtil.print("Restricted movement mode set to left controller");
				}
			} else {
				
				OpenVRPlayer vrp = VR.player();
				
				// cycle VR movement styles
				if (vrp.vrMovementStyle.name == "Minimal") vrp.vrMovementStyle.setStyle("Beam");
				else if (vrp.vrMovementStyle.name == "Beam") vrp.vrMovementStyle.setStyle("Tunnel");
				else if (vrp.vrMovementStyle.name == "Tunnel") vrp.vrMovementStyle.setStyle("Grapple");
				else if (vrp.vrMovementStyle.name == "Grapple") vrp.vrMovementStyle.setStyle("Arc");
				else vrp.vrMovementStyle.setStyle("Minimal");
			
						
			}
					
			gotKey = true;
		}

		if (Keyboard.getEventKey() == Keyboard.KEY_R && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
		{
			// for testing restricted client mode
			
			if (VR.player().getFreeMoveMode()) {
				VR.player().setFreeMoveMode(false);
							ChatUtil.print("Restricted movement disabled (teleporting allowed)");
				} else {
				VR.player().setFreeMoveMode(true);
				ChatUtil.print("Restricted movement enabled (no teleporting)");
			}
			
			gotKey = true;
		}
		
		
		if (Keyboard.getEventKey() == Keyboard.KEY_UP && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamrotPitch -= 0.5;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_DOWN && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamrotPitch += 0.5;	
			gotKey = true;
		
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_LEFT && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamrotYaw -= 0.5;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_RIGHT && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamrotYaw += 0.5;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_PRIOR && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamrotRoll -= 0.05;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_NEXT && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamrotRoll += 0.05;	
			gotKey = true;
		}
		if(!gotKey){
		if (Keyboard.getEventKey() == Keyboard.KEY_LEFT && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamposX -= 0.01;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_RIGHT&& Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamposX += 0.01;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_UP&& Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamposZ -= 0.01;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_DOWN && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamposZ += 0.01;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_PRIOR && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
		{
			VR.settings().vrFixedCamposY += 0.01;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_NEXT && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
		{
			VR.settings().vrFixedCamposY -= 0.01;
			gotKey = true;
		}
		
		if (Keyboard.getEventKey() == Keyboard.KEY_INSERT && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
		{
			mc.gameSettings.fovSetting +=1 ;
			gotKey = true;
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_DELETE && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))

		{
			mc.gameSettings.fovSetting -=1 ;
			gotKey = true;
		}
		}
		if (Keyboard.getEventKey() == Keyboard.KEY_HOME && Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
		{
			snapMRCam(mc,0);
			gotKey = true;
		}

		// VIVE END - hotkeys

		if (gotKey) {
			VR.settings().saveOptions();
		}

		return gotKey;
	}

	
	public static void snapMRCam(Minecraft mc, int controller) {
		
		VR.settings().vrFixedCamrotPitch = -VR.roomScale().getControllerPitch_World(controller);
		VR.settings().vrFixedCamrotYaw = 180+ VR.roomScale().getControllerYaw_World(controller) -  VR.settings().vrWorldRotation;
		VR.settings().vrFixedCamrotRoll = VR.roomScale().getControllerRoll_World(controller);

		Vec3 pos = VR.roomScale().getControllerPos_Room(controller);
		VR.settings().vrFixedCamposX = ((float)pos.xCoord) / VR.player().worldScale;
		VR.settings().vrFixedCamposY = ((float)pos.yCoord) / VR.player().worldScale;
		VR.settings().vrFixedCamposZ = ((float)pos.zCoord) / VR.player().worldScale;
		
	}
}
