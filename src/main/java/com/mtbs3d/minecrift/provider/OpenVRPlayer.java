package com.mtbs3d.minecrift.provider;

import lol.gzmc.vivecraftx.util.OptiFineCompatUtil;

import lol.gzmc.vivecraftx.util.VanillaExt;

import lol.gzmc.vivecraftx.VRRenderState;

import lol.gzmc.vivecraftx.util.Vec3Util;

import lol.gzmc.vivecraftx.VR;
import lol.gzmc.vivecraftx.net.VRServerRules;
import lol.gzmc.vivecraftx.net.VRNetwork;
import lol.gzmc.vivecraftx.ViveCraftX;

import lol.gzmc.vivecraftx.api.RenderPass;
import lol.gzmc.vivecraftx.util.ChatUtil;


import de.fruitfly.ovr.enums.EyeType;
import de.fruitfly.ovr.structs.EulerOrient;
import de.fruitfly.ovr.structs.Matrix4f;
import de.fruitfly.ovr.structs.Quatf;
import de.fruitfly.ovr.structs.Vector3f;
import de.fruitfly.ovr.util.BufferUtil;
import io.netty.util.concurrent.GenericFutureListener;
import jopenvr.OpenVRUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockVine;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.util.*;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Random;

import javax.swing.plaf.multi.MultiViewportUI;

import org.lwjgl.util.vector.Quaternion;

import com.google.common.base.Charsets;
import com.mtbs3d.minecrift.api.IRoomscaleAdapter;
import com.mtbs3d.minecrift.api.NetworkHelper;
import com.mtbs3d.minecrift.api.NetworkHelper.PacketDiscriminators;
import com.mtbs3d.minecrift.gameplay.EntityVRTeleportFX;
import com.mtbs3d.minecrift.gameplay.VRMovementStyle;
import com.mtbs3d.minecrift.render.QuaternionHelper;
import com.mtbs3d.minecrift.settings.AutoCalibration;
import com.mtbs3d.minecrift.settings.VRSettings;
import com.mtbs3d.minecrift.utils.Utils;

// VIVE
public class OpenVRPlayer implements IRoomscaleAdapter
{
    public double lastRoomUpdateTime = 0;
    public Vec3 movementTeleportDestination = Vec3.createVectorHelper(0.0,0.0,0.0);
    public int movementTeleportDestinationSideHit;
    public double movementTeleportProgress;
    public double movementTeleportDistance;
        
    public Vec3 roomOrigin = Vec3.createVectorHelper(0,0,0);
    public Vec3 lastroomOrigin = Vec3.createVectorHelper(0,0,0);
//    
    public VRMovementStyle vrMovementStyle = new VRMovementStyle();
    public Vec3[] movementTeleportArc = new Vec3[50];
    public int movementTeleportArcSteps = 0;
    private boolean freeMoveMode = true;        // true when connected to another server that doesn't have this mod
	public boolean useLControllerForRestricedMovement = true;
    
    private float teleportEnergy;
    
	private Vec3 walkMultOffset= Vec3.createVectorHelper(0, 0, 0);
    
	public double wfMode = 0;
	public int wfCount = 0;
    
    public static OpenVRPlayer get()
    {
        return VR.player();
    }

    public OpenVRPlayer()
    {
        for (int i=0;i<50;i++)
        {
            movementTeleportArc[i] = Vec3.createVectorHelper(0,0,0);
        }
    }
    
    public void setRoomOrigin(double x, double y, double z, boolean reset, boolean onframe ) { 
	    if(!onframe){
	    	if (reset){
		    		//VRRenderState.interPolatedRoomOrigin = Vec3.createVectorHelper(x, y, z);
		    		lastroomOrigin = Vec3.createVectorHelper(x, y, z);
		    		VRRenderState.interPolatedRoomOrigin = Vec3.createVectorHelper(x, y, z);
		    	} else {
		    	}
	    }
	    
    	this.roomOrigin.xCoord = x;
    	this.roomOrigin.yCoord = y;
    	this.roomOrigin.zCoord = z;
        lastRoomUpdateTime = VR.stereo().getCurrentTimeSecs();
        VRRenderState.irpUpdatedThisFrame = onframe;
    }
    
    
    public Vec3 getPosition(Entity entity, float p_70666_1_)
    {
        if (p_70666_1_ == 1.0F)
        {
            return Vec3.createVectorHelper(entity.posX, entity.posY, entity.posZ);
        }
        else
        {
            double var2 = entity.prevPosX + (entity.posX - entity.prevPosX) * (double)p_70666_1_;
            double var4 = entity.prevPosY + (entity.posY - entity.prevPosY) * (double)p_70666_1_;
            double var6 = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * (double)p_70666_1_;
            return Vec3.createVectorHelper(var2, var4, var6);
        }
    }
    
    //set room 
    public void snapRoomOriginToPlayerEntity(EntityPlayerSP player, boolean reset, boolean onFrame, float nano)
    {
    	if (Thread.currentThread().getName().equals("Server thread"))
    		return;

    	if(player.posX == 0 && player.posY == 0 &&player.posZ == 0) return;

    	Minecraft mc = Minecraft.getMinecraft();

    	Vec3 campos = VR.roomScale().getHMDPos_Room();

    	campos.rotateAroundY(worldRotationRadians);

    	double x,y,z;

    	if(onFrame){
    		x = VRRenderState.interpolatedPlayerPos.xCoord - campos.xCoord;

    		if(player.isRiding()){
    			y = getPosition(player.ridingEntity, nano).yCoord + player.ridingEntity.getMountedYOffset();
    		} else
    			y = VRRenderState.interpolatedPlayerPos.yCoord- player.yOffset;

    		z = VRRenderState.interpolatedPlayerPos.zCoord - campos.zCoord;
    	} else {
    		x = player.posX - campos.xCoord;

    		if(player.isRiding()){
   				y = player.ridingEntity.posY + player.ridingEntity.getMountedYOffset();
    		} 
    		else
    			y = player.posY - player.yOffset;

    		z = player.posZ - campos.zCoord;
    	}

    	setRoomOrigin(x, y, z, reset, onFrame);
    	this.roomScaleMovementDelay = 3;       
    }
    
    public  double topofhead = 1.62;
        
    private float lastworldRotation= 0f;
	private float lastWorldScale;
    
	public void checkandUpdateRotateScale(boolean onFrame, float nano){
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || !VanillaExt.isInitFromServer(mc.thePlayer)) return;
		if(mc.currentScreen!=null) return;
		if(!onFrame) {
			if(this.wfCount > 0){
				if(this.wfCount < 40){
					this.worldScale-=this.wfMode / 2;
					if(this.worldScale >  VR.settings().vrWorldScale && this.wfMode <0) this.worldScale = VR.settings().vrWorldScale;
					if(this.worldScale <  VR.settings().vrWorldScale && this.wfMode >0) this.worldScale = VR.settings().vrWorldScale;
				}else {
					this.worldScale+=this.wfMode / 2;
					if(this.worldScale >  VR.settings().vrWorldScale*20) this.worldScale = 20;
					if(this.worldScale <  VR.settings().vrWorldScale/10) this.worldScale = 0.1f;				
				}
				this.wfCount--;
			} else 	this.worldScale =  VR.settings().vrWorldScale;
		}
	    this.worldRotationRadians = (float) Math.toRadians(VR.settings().vrWorldRotation);
	    
	    if (worldRotationRadians!= lastworldRotation || worldScale != lastWorldScale) {
	    	if(mc.thePlayer!=null) 
	    		snapRoomOriginToPlayerEntity(mc.thePlayer, true, onFrame, nano);
	    }
	    if(!onFrame)    lastWorldScale = worldScale;		
	}
	
	/**
	 * Getting out of a boat or a cart by stepping out of it.
	 *
	 * <p>Pressing a key to leave something you are sitting in is the kind of thing that only
	 * makes sense without a body. In the room you simply walk out of it, and the game should
	 * agree. Judged from the head rather than the feet, because the feet belong to the entity
	 * and the entity is still in the vehicle -- the head is the part that has actually left.
	 */
	private void checkRoomscaleDismount(EntityPlayerSP player) {
		if (VR.settings() == null || !VR.settings().realisticDismount || VR.settings().seated) {
			return;
		}
		if (player.ridingEntity == null) {
			return;
		}
		Vec3 head = getHMDPos_World();
		if (head == null) {
			return;
		}
		// A margin outside the vehicle, so leaning over the side to look at the water is not
		// mistaken for climbing out of the boat.
		final double margin = 0.35D;
		AxisAlignedBB box = player.ridingEntity.boundingBox.expand(margin, 4.0D, margin);
		if (head.xCoord < box.minX || head.xCoord > box.maxX
				|| head.zCoord < box.minZ || head.zCoord > box.maxZ) {
			player.mountEntity(null);
		}
	}

	/**
	 * Whether hitting this block should work it rather than break it.
	 *
	 * <p>By type rather than by name, so a mod's own door is a door too. Pressure plates are
	 * left out: you stand on those, and a hand brushing one should not fire it.
	 */
	public static boolean isOpenable(Block block) {
		return block instanceof net.minecraft.block.BlockDoor
				|| block instanceof net.minecraft.block.BlockTrapDoor
				|| block instanceof net.minecraft.block.BlockFenceGate
				|| block instanceof net.minecraft.block.BlockButton
				|| block instanceof net.minecraft.block.BlockLever;
	}

	public void preTick(){
		lastworldRotation = worldRotationRadians;
		lastroomOrigin = Vec3.createVectorHelper(roomOrigin.xCoord, roomOrigin.yCoord, roomOrigin.zCoord);
	}

    /** The tick the physical update last ran on, so two entry points cannot both run it. */
    private static int lastPhysicalTick = -1;

    /** One line, once, naming the way in that worked. */
    private static boolean announcedLivingUpdate;

    /**
     * Runs the physical update for this tick, from whichever hook got here first.
     *
     * <p>There are two, because the one that ought to be enough -- straight after the player's own
     * living update, where Vivecraft put it -- turned out never to fire in this pack, and every
     * physical gesture in the mod hangs off it. Rather than leave the whole lot dead while the
     * reason is found, the concrete client player's own update is hooked as well. Whichever
     * arrives first for a given tick does the work and the other stands down, so the behaviour is
     * the same either way and the log says which one it was.
     */
    public void runPhysicalTick(EntityPlayerSP player, Minecraft mc, Random rand, String via,
    		boolean fallback)
    {
    	if (lastPhysicalTick == player.ticksExisted) return;
    	lastPhysicalTick = player.ticksExisted;
    	// Said only when the hook that ought to be enough was not the one that got here, because
    	// that is an anomaly worth a line in the log rather than something to announce every session.
    	if (!announcedLivingUpdate && fallback) {
    		announcedLivingUpdate = true;
    		ViveCraftX.LOG.info("The player's living update never reached us; running the physical"
    				+ " tick from {} instead", via);
    	}
    	onLivingUpdate(player, mc, rand);
    }

    public void onLivingUpdate(EntityPlayerSP player, Minecraft mc, Random rand)
    {
    	if(!VanillaExt.isInitFromServer(player)) return;
               
	    VR.runTracker().doProcess(mc, player);

	    VR.jumpTracker().doProcess(mc, player);
   
	    VR.sneakTracker().doProcess(mc, player);
	    
		VR.autoFood().doProcess(mc,player);
		
		VR.haptics().doProcess(mc, player);
		
		VR.rowing().doProcess(mc, player);
		
		VR.horse().doProcess(mc, player);
		
		checkRoomscaleDismount(player);
		
		VR.backpackTracker().doProcess(mc, player);
        
        this.checkandUpdateRotateScale(false, 1);

	    VR.swimTracker().doProcess(mc,player);

	    VR.climbTracker().doProcess(mc, player);

        VR.interactTracker().doProcess(mc, player);

        VR.swingTracker().doProcess(mc, (net.minecraft.client.entity.EntityClientPlayerMP) player);
	    
	    AutoCalibration.logHeadPos(MCOpenVR.hmdPivotHistory.latest());

    	NetworkHelper.sendVRPlayerPositions(this);
        
		/** MINECRIFT */
		mc.thePlayer.stepHeight = VR.settings().walkUpBlocks ? 1f * VR.player().worldScale : 0.5f;
		if (!this.getFreeMoveMode()) mc.thePlayer.stepHeight = 0.0f;
		/** END MINECRIFT */
        
       if(false){         //experimental - EXPERIMENT FAILED
           topofhead = (double) (VR.roomScale().getHMDPos_Room().yCoord + .05);
           
           if(topofhead < .5) {topofhead = 0.5f;}
           if(topofhead > 1.8) {topofhead = 1.8f;}
           
           player.height = (float) topofhead - 0.05f;
           player.boundingBox.maxY = player.boundingBox.minY +  topofhead;  	   
       } else {
    	  // player.height = 1.8f;
       }
             
        if (getFreeMoveMode()) {
        	if(player.movementInput.moveForward ==0 && player.movementInput.moveStrafe == 0) doPlayerMoveInRoom(player);
        	
			  return; //let mc handle look direction movement
			// controller vs gaze movement is handled in Entity.java > moveFlying
          }
				
        mc.mcProfiler.startSection("VRPlayerOnLivingUpdate");

        if (teleportEnergy < 100) { teleportEnergy++;}
        
        boolean doTeleport = false;
        Vec3 dest = null;

        if ((player.movementInput.moveForward != 0 || player.movementInput.moveStrafe != 0) && !player.isRiding()) //holding down Ltrigger
        {
            dest = movementTeleportDestination;

            if (vrMovementStyle.teleportOnRelease)
            {
                if (VanillaExt.getMovementTeleportTimer(player) == 0)
                {
                    String sound = vrMovementStyle.startTeleportingSound;
                    if (sound != null)
                    {
                        player.playSound(sound, vrMovementStyle.startTeleportingSoundVolume,
                                1.0F / (rand.nextFloat() * 0.4F + 1.2F) + 1.0f * 0.5F);
                    }
                }
                VanillaExt.setMovementTeleportTimer(player, VanillaExt.getMovementTeleportTimer(player) + 1);
                if (VanillaExt.getMovementTeleportTimer(player) > 0)
                {
                    movementTeleportProgress = (float) VanillaExt.getMovementTeleportTimer(player) / 4.0f;
                    if (movementTeleportProgress>=1.0f)
                    {
                        movementTeleportProgress = 1.0f;
                    }

                    if (dest.xCoord != 0 || dest.yCoord != 0 || dest.zCoord != 0)
                    {
                        Vec3 eyeCenterPos = getHMDPos_World();

                        // cloud of sparks moving past you
                        Vec3 motionDir = dest.addVector(-eyeCenterPos.xCoord, -eyeCenterPos.yCoord, -eyeCenterPos.zCoord).normalize();
                        Vec3 forward;
						
						forward	= player.getLookVec();

                        Vec3 right = forward.crossProduct(Vec3.createVectorHelper(0, 1, 0));
                        Vec3 up = right.crossProduct(forward);

                        if (vrMovementStyle.airSparkles)
                        {
                            for (int iParticle = 0; iParticle < 3; iParticle++)
                            {
                                double forwardDist = rand.nextDouble() * 1.0 + 3.5;
                                double upDist = rand.nextDouble() * 2.5;
                                double rightDist = rand.nextDouble() * 4.0 - 2.0;

                                Vec3 sparkPos = Vec3.createVectorHelper(eyeCenterPos.xCoord + forward.xCoord * forwardDist,
                                        eyeCenterPos.yCoord + forward.yCoord * forwardDist,
                                        eyeCenterPos.zCoord + forward.zCoord * forwardDist);
                                sparkPos = sparkPos.addVector(right.xCoord * rightDist, right.yCoord * rightDist, right.zCoord * rightDist);
                                sparkPos = sparkPos.addVector(up.xCoord * upDist, up.yCoord * upDist, up.zCoord * upDist);

                                double speed = -0.6;
                                EntityFX particle = new EntityVRTeleportFX(
                                        player.worldObj,
                                        sparkPos.xCoord, sparkPos.yCoord, sparkPos.zCoord,
                                        motionDir.xCoord * speed, motionDir.yCoord * speed, motionDir.zCoord * speed,
                                        1.0f);
                                mc.effectRenderer.addEffect(particle);
                            }
                        }
                    }
                }
            }
            else
            {
                if (VanillaExt.getMovementTeleportTimer(player) >= 0 && (dest.xCoord != 0 || dest.yCoord != 0 || dest.zCoord != 0))
                {
                    if (VanillaExt.getMovementTeleportTimer(player) == 0)
                    {
                        String sound = vrMovementStyle.startTeleportingSound;
                        if (sound != null)
                        {
                            player.playSound(sound, vrMovementStyle.startTeleportingSoundVolume,
                                    1.0F / (rand.nextFloat() * 0.4F + 1.2F) + 1.0f * 0.5F);
                        }
                    }
                    VanillaExt.setMovementTeleportTimer(player, VanillaExt.getMovementTeleportTimer(player) + 1);

                    Vec3 playerPos = Vec3.createVectorHelper(player.posX, player.posY, player.posZ);
                    double dist = dest.distanceTo(playerPos);
                    double progress = (VanillaExt.getMovementTeleportTimer(player) * 1.0) / (dist + 3.0);

                    if (VanillaExt.getMovementTeleportTimer(player) > 0)
                    {
                        movementTeleportProgress = progress;

                        // spark at dest point
                        if (vrMovementStyle.destinationSparkles)
                        {
                            player.worldObj.spawnParticle("instantSpell", dest.xCoord, dest.yCoord, dest.zCoord, 0, 1.0, 0);
                        }

                        // cloud of sparks moving past you
                        Vec3 motionDir = dest.addVector(-player.posX, -player.posY, -player.posZ).normalize();
                        Vec3 forward = player.getLookVec();
                        Vec3 right = forward.crossProduct(Vec3.createVectorHelper(0, 1, 0));
                        Vec3 up = right.crossProduct(forward);

                        if (vrMovementStyle.airSparkles)
                        {
                            for (int iParticle = 0; iParticle < 3; iParticle++)
                            {
                                double forwardDist = rand.nextDouble() * 1.0 + 3.5;
                                double upDist = rand.nextDouble() * 2.5;
                                double rightDist = rand.nextDouble() * 4.0 - 2.0;
                                Vec3 sparkPos = Vec3.createVectorHelper(player.posX + forward.xCoord * forwardDist,
                                        player.posY + forward.yCoord * forwardDist,
                                        player.posZ + forward.zCoord * forwardDist);
                                sparkPos = sparkPos.addVector(right.xCoord * rightDist, right.yCoord * rightDist, right.zCoord * rightDist);
                                sparkPos = sparkPos.addVector(up.xCoord * upDist, up.yCoord * upDist, up.zCoord * upDist);

                                double speed = -0.6;
                                EntityFX particle = new EntityVRTeleportFX(
                                        player.worldObj,
                                        sparkPos.xCoord, sparkPos.yCoord, sparkPos.zCoord,
                                        motionDir.xCoord * speed, motionDir.yCoord * speed, motionDir.zCoord * speed,
                                        1.0f);
                                mc.effectRenderer.addEffect(particle);
                            }
                        }
                    } else
                    {
                        movementTeleportProgress = 0;
                    }

                    if (progress >= 1.0)
                    {
                        doTeleport = true;
                    }
                }
            }
        }
        else //not holding down Ltrigger
        {
            if (vrMovementStyle.teleportOnRelease && movementTeleportProgress>=1.0f)
            {
                dest = movementTeleportDestination;
                doTeleport = true;
            }
            VanillaExt.setMovementTeleportTimer(player, 0);
            movementTeleportProgress = 0;
        }

        if (doTeleport && dest!=null && (dest.xCoord != 0 || dest.yCoord !=0 || dest.zCoord != 0)) //execute teleport
        {
            movementTeleportDistance = (float)MathHelper.sqrt_double(dest.squareDistanceTo(player.posX, player.posY, player.posZ));
            boolean playTeleportSound = movementTeleportDistance > 0.0f && vrMovementStyle.endTeleportingSound != null;
            Block block = null;

            if (playTeleportSound)
            {
                String sound = vrMovementStyle.endTeleportingSound;
                if (sound != null)
                {
                    player.playSound(sound, vrMovementStyle.endTeleportingSoundVolume, 1.0F);
                }
            }
            else
            {
                playFootstepSound(mc, dest.xCoord, dest.yCoord, dest.zCoord);
            }

            VR.swingTracker().settle();
            
            //execute teleport      
            
            // Who does the moving. A client that moves itself more than ten blocks in a tick
            // is snapped back by any server's own cheat check -- rightly, since that is what
            // moving ten blocks in a tick usually means. So on a server that has this mod, the
            // move is asked for and the server performs it; the client still moves locally so
            // the view does not wait for the round trip, and holds back its own movement packet
            // for that tick so the server is not told the same thing twice in a form it will
            // refuse. Everywhere else the distance is kept inside what a server will accept.
            player.setPositionAndUpdate(dest.xCoord, dest.yCoord, dest.zCoord);
            if (VRServerRules.serverMovesUs()) {
                VRNetwork.requestTeleport(dest.xCoord, dest.yCoord, dest.zCoord);
                VanillaExt.suppressNextMoveUpdate();
            }

            doTeleportCallback(); //callback unreliable if not vanilla, so just do it and assume.

            if(VR.settings().vrLimitedSurvivalTeleport){
              player.addExhaustion((float) (movementTeleportDistance / 16 * 1.2f));    
              
              if (!VR.player().getFreeMoveMode() && mc.playerController.isNotCreative() && VR.player().vrMovementStyle.arcAiming){
              	teleportEnergy -= movementTeleportDistance * 4;	
              }              
            }
            
          //  System.out.println("teleport " + dest.toString());
            player.fallDistance = 0.0F;

            if (playTeleportSound)
            {
                String sound = vrMovementStyle.endTeleportingSound;
                if (sound != null)
                {
                    player.playSound(sound, vrMovementStyle.endTeleportingSoundVolume, 1.0F);
                }
            }
            else
            {
                playFootstepSound(mc, dest.xCoord, dest.yCoord, dest.zCoord);
            }

            VanillaExt.setMovementTeleportTimer(player, -1);
            
        }
        else //standing still
        {
			doPlayerMoveInRoom(player);
        }

        mc.mcProfiler.endSection();
    }

    
    public void doTeleportCallback(){
        Minecraft mc = Minecraft.getMinecraft();

        VR.swingTracker().settle();

        if(VR.settings().vrLimitedSurvivalTeleport){
          mc.thePlayer.addExhaustion((float) (movementTeleportDistance / 16 * 1.2f));    
          
          if (!VR.player().getFreeMoveMode() && mc.playerController.isNotCreative() && VR.player().vrMovementStyle.arcAiming){
          	teleportEnergy -= movementTeleportDistance * 4;	
          }       
        }
        
        mc.thePlayer.fallDistance = 0.0F;

        VanillaExt.setMovementTeleportTimer(mc.thePlayer, -1);
        
    }
    
    private int roomScaleMovementDelay = 0;
    
    private void doPlayerMoveInRoom(EntityPlayerSP player){
    	// this needs... work...
    	//if(true) return;
    	if(roomScaleMovementDelay > 0){
    		roomScaleMovementDelay--;
    		return;
    	}
    	if(player.isSneaking()) {return;} //jrbudda : prevent falling off things or walking up blocks while moving in room scale.
    	if(player.isRiding()) return; //dont fall off the tracks man
    	if(player.isDead) return; //
    	if(player.isPlayerSleeping()) return; //
//    	if(this.interPolatedRoomOrigin.xCoord == 0 && this.interPolatedRoomOrigin.yCoord ==0 && this.interPolatedRoomOrigin.zCoord == 0) return;
//    	
    	if(Math.abs(player.motionX) > 0.01) return;
    	if(Math.abs(player.motionZ) > 0.01) return;
    	
    	Minecraft mc = Minecraft.getMinecraft();
    	float playerHalfWidth = player.width / 2.0F;

    	// move player's X/Z coords as the HMD moves around the room

    	Vec3 eyePos = getHMDPos_World();

    	double x = eyePos.xCoord;
    	double y = player.posY;
    	double z = eyePos.zCoord;

    	// create bounding box at dest position
    	AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
    			x - (double) playerHalfWidth,
    			y - (double) player.yOffset,
    			z - (double) playerHalfWidth,
    			x + (double) playerHalfWidth,
    			y - (double) player.yOffset + (double) player.height,
    			z + (double) playerHalfWidth);

    	Vec3 torso = null;

    	// valid place to move player to?
    	float var27 = 0.0625F;
    	boolean emptySpot = mc.theWorld.getCollidingBoundingBoxes(player, bb).isEmpty();

    	if (emptySpot)
    	{
    		// don't call setPosition style functions to avoid shifting room origin
    		player.posX = x;
    		if (!VR.settings().simulateFalling)	{
    			player.posY = y;                	
    		}
    		player.posZ = z;
    
    		 if(player.ridingEntity!=null){ //you're coming with me, horse! //TODO: use mount's bounding box.
    				player.ridingEntity.posX = x;
    				if (!VR.settings().simulateFalling)	{
    					player.ridingEntity.posY = y;                	
    	    		}
    				player.ridingEntity.posZ = z;
    		 }
    		 
    		player.boundingBox.setBounds(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY + player.height, bb.maxZ);
    		player.fallDistance = 0.0F;

    		torso = getEstimatedTorsoPosition(x, y, z);


    	}

    	//             test for climbing up a block
      	else if ((VR.settings().walkUpBlocks || (player.isOnLadder() && VR.settings().realisticClimbEnabled)) && player.fallDistance == 0)    	{
    		if (torso == null)
    		{
    			torso = getEstimatedTorsoPosition(x, y, z);
    		}

    		// is the player significantly inside a block?
    		float climbShrink = player.width * 0.45f;
    		double shrunkClimbHalfWidth = playerHalfWidth - climbShrink;
    		AxisAlignedBB bbClimb = AxisAlignedBB.getBoundingBox(
    				torso.xCoord - shrunkClimbHalfWidth,
    				bb.minY,
    				torso.zCoord - shrunkClimbHalfWidth,
    				torso.xCoord + shrunkClimbHalfWidth,
    				bb.maxY,
    				torso.zCoord + shrunkClimbHalfWidth);

    		boolean notyet = mc.theWorld.getCollidingBoundingBoxes(player, bbClimb).isEmpty();

    		if(!notyet){
    			double xOffset = torso.xCoord - x;
    			double zOffset = torso.zCoord - z;
    			bb.minX += xOffset;
    			bb.maxX += xOffset;                	 
    			bb.minZ += zOffset;
    			bb.maxZ += zOffset;     
    			
    			int extra = 0;
    			if(player.isOnLadder() && VR.settings().realisticClimbEnabled)
    				extra = 6;
    			
    			for (int i = 0; i <=10 + extra ; i++)
    			{
    				bb.minY += 0.1f;
    				bb.maxY += 0.1f;

    				emptySpot = mc.theWorld.getCollidingBoundingBoxes(player, bb).isEmpty();
    				if (emptySpot)
    				{
    	    			x += xOffset;  	
    	    			z += zOffset;
    					y += 0.1f*i;
    					
    					 player.posX = x;
    					 player.posY = y;
    				     player.posZ = z;
    					
    					player.boundingBox.setBounds(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);

    					roomOrigin.xCoord += xOffset;
    					roomOrigin.yCoord += 0.1f*i;
    					roomOrigin.zCoord += zOffset;

    					Vec3 look = player.getLookVec();
    					Vec3 forward = Vec3.createVectorHelper(look.xCoord,0,look.zCoord).normalize();
    					player.fallDistance = 0.0F;
    					playFootstepSound(mc,
    							player.posX + forward.xCoord * 0.4f,
    							player.posY-player.height,
    							player.posZ + forward.zCoord * 0.4f);
    					break;
    				}
    			}
    		}
    	}
    }
	
    public void playFootstepSound( Minecraft mc, double x, double y, double z )
    {
        Block block = mc.theWorld.getBlock(MathHelper.floor_double(x),
                MathHelper.floor_double(y - 0.5f),
                MathHelper.floor_double(z));

        if (block != null && block.getMaterial() != Material.air)
        {
            mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(OptiFineCompatUtil.getStepSound(block.stepSound)),
                    (block.stepSound.getVolume() + 1.0F) / 8.0F,
                    OptiFineCompatUtil.getFrequency(block.stepSound) * 0.5F,
                    (float) x, (float) y, (float) z));
        }
    }

    // use simple neck modeling to estimate torso location
    public Vec3 getEstimatedTorsoPosition(double x, double y, double z)
    {
        Entity player = Minecraft.getMinecraft().thePlayer;
        Vec3 look = player.getLookVec();
        Vec3 forward = Vec3.createVectorHelper(look.xCoord, 0, look.zCoord).normalize();
        float factor = (float)look.yCoord * 0.25f;
        Vec3 torso = Vec3.createVectorHelper(
                x + forward.xCoord * factor,
                y + forward.yCoord * factor,
                z + forward.zCoord * factor);

        return torso;
    }


    public void updateTeleportArc(Minecraft mc, Entity player)
    {
        Vec3 start = this.getControllerOffhandPos_World();
        Vec3 tiltedAim = VR.roomScale().getControllerOffhandDir_World();
        Matrix4f handRotation =MCOpenVR.getAimRotation(1);
 
        if(VR.settings().seated){
        	start = VRRenderState.getControllerRenderPos(0);
        	tiltedAim = VR.roomScale().getControllerDir_World(0);
        	handRotation =MCOpenVR.getAimRotation(0);
        }
        
        Matrix4f rot = Matrix4f.rotationY(this.worldRotationRadians);
        handRotation = Matrix4f.multiply(rot, handRotation);
              
        // extract hand roll
        Quatf handQuat = OpenVRUtil.convertMatrix4ftoRotationQuat(handRotation);
        EulerOrient euler = OpenVRUtil.getEulerAnglesDegYXZ(handQuat);
        
        int maxSteps = 50;
        movementTeleportArc[0].xCoord = start.xCoord;
        movementTeleportArc[0].yCoord = start.yCoord;
        movementTeleportArc[0].zCoord = start.zCoord;
        movementTeleportArcSteps = 1;

        // calculate gravity vector for arc
        float gravityAcceleration = 0.098f;
        Matrix4f rollCounter = OpenVRUtil.rotationZMatrix(OptiFineCompatUtil.DEG_TO_RAD*-euler.roll);
        Matrix4f gravityTilt = OpenVRUtil.rotationXMatrix((float)Math.PI * -.8f);
        Matrix4f gravityRotation = Matrix4f.multiply(handRotation, rollCounter);
        
        Vector3f forward = new Vector3f(0,1,0);
        Vector3f gravityDirection = gravityRotation.transform(forward);
        Vec3 gravity = Vec3.createVectorHelper(-gravityDirection.x, -gravityDirection.y, -gravityDirection.z);
        gravity.xCoord *= gravityAcceleration;
        gravity.yCoord *= gravityAcceleration;
        gravity.zCoord *= gravityAcceleration;
        
     //   gravity.rotateAroundY(this.worldRotationRadians);

        // calculate initial move step	
        float speed = 0.5f;
        Vec3 velocity = Vec3.createVectorHelper(
                tiltedAim.xCoord * speed,
                tiltedAim.yCoord * speed,
                tiltedAim.zCoord * speed);

        Vec3 pos = Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord);
        Vec3 newPos = Vec3.createVectorHelper(0,0,0);

        // trace arc
        for (int i=movementTeleportArcSteps;i<maxSteps;i++)
        {
        	if (i*4 > teleportEnergy) {
        		break;
        		}
        	
            newPos.xCoord = pos.xCoord + velocity.xCoord;
            newPos.yCoord = pos.yCoord + velocity.yCoord;
            newPos.zCoord = pos.zCoord + velocity.zCoord;
            
          		
            boolean	water =false;
            Block b = VRRenderState.getEyeCollisionBlock(VR.currentPass(), true);
            water = b!=null &&b.getMaterial() == Material.water;

            
            MovingObjectPosition collision = tpRaytrace(player.worldObj, pos, newPos, !water, true, false);
			
            if (collision != null && collision.typeOfHit != MovingObjectPosition.MovingObjectType.MISS)
            {
                movementTeleportArc[i].xCoord = collision.hitVec.xCoord;
                movementTeleportArc[i].yCoord = collision.hitVec.yCoord;
                movementTeleportArc[i].zCoord = collision.hitVec.zCoord;
                movementTeleportArcSteps = i + 1;

                Vec3 traceDir = pos.subtract(newPos).normalize();
                Vec3 reverseEpsilon = Vec3.createVectorHelper(-traceDir.xCoord * 0.02, -traceDir.yCoord * 0.02, -traceDir.zCoord * 0.02);

                checkAndSetTeleportDestination(mc, player, start, collision, reverseEpsilon);
                          
                break;
            }

            pos.xCoord = newPos.xCoord;
            pos.yCoord = newPos.yCoord;
            pos.zCoord = newPos.zCoord;

            movementTeleportArc[i].xCoord = newPos.xCoord;
            movementTeleportArc[i].yCoord = newPos.yCoord;
            movementTeleportArc[i].zCoord = newPos.zCoord;
            movementTeleportArcSteps = i + 1;

            velocity.xCoord += gravity.xCoord;
            velocity.yCoord += gravity.yCoord;
            velocity.zCoord += gravity.zCoord;
        }
    }

    public MovingObjectPosition tpRaytrace(World w, Vec3 p_147447_1_, Vec3 p_147447_2_, boolean p_147447_3_, boolean p_147447_4_, boolean p_147447_5_)
    {
        if (!Double.isNaN(p_147447_1_.xCoord) && !Double.isNaN(p_147447_1_.yCoord) && !Double.isNaN(p_147447_1_.zCoord))
        {
            if (!Double.isNaN(p_147447_2_.xCoord) && !Double.isNaN(p_147447_2_.yCoord) && !Double.isNaN(p_147447_2_.zCoord))
            {
                int var6 = MathHelper.floor_double(p_147447_2_.xCoord);
                int var7 = MathHelper.floor_double(p_147447_2_.yCoord);
                int var8 = MathHelper.floor_double(p_147447_2_.zCoord);
                int var9 = MathHelper.floor_double(p_147447_1_.xCoord);
                int var10 = MathHelper.floor_double(p_147447_1_.yCoord);
                int var11 = MathHelper.floor_double(p_147447_1_.zCoord);
                Block var12 = w.getBlock(var9, var10, var11);
                int var13 = w.getBlockMetadata(var9, var10, var11);
           
                if(var12 == Blocks.water){
                	p_147447_4_ = !p_147447_3_;
                }
                
                if (var12 == Blocks.waterlily || (!p_147447_4_ || var12.getCollisionBoundingBoxFromPool(w, var9, var10, var11) != null) && OptiFineCompatUtil.canStopRayTrace(var12, var13, p_147447_3_))
                {
                    MovingObjectPosition var14 = var12.collisionRayTrace(w, var9, var10, var11, p_147447_1_, p_147447_2_);

                    if (var14 != null)
                    {
                        return var14;
                    }
                }

                MovingObjectPosition var40 = null;
                var13 = 200;

                while (var13-- >= 0)
                {
                    if (Double.isNaN(p_147447_1_.xCoord) || Double.isNaN(p_147447_1_.yCoord) || Double.isNaN(p_147447_1_.zCoord))
                    {
                        return null;
                    }

                    if (var9 == var6 && var10 == var7 && var11 == var8)
                    {
                        return p_147447_5_ ? var40 : null;
                    }

                    boolean var41 = true;
                    boolean var15 = true;
                    boolean var16 = true;
                    double var17 = 999.0D;
                    double var19 = 999.0D;
                    double var21 = 999.0D;

                    if (var6 > var9)
                    {
                        var17 = (double)var9 + 1.0D;
                    }
                    else if (var6 < var9)
                    {
                        var17 = (double)var9 + 0.0D;
                    }
                    else
                    {
                        var41 = false;
                    }

                    if (var7 > var10)
                    {
                        var19 = (double)var10 + 1.0D;
                    }
                    else if (var7 < var10)
                    {
                        var19 = (double)var10 + 0.0D;
                    }
                    else
                    {
                        var15 = false;
                    }

                    if (var8 > var11)
                    {
                        var21 = (double)var11 + 1.0D;
                    }
                    else if (var8 < var11)
                    {
                        var21 = (double)var11 + 0.0D;
                    }
                    else
                    {
                        var16 = false;
                    }

                    double var23 = 999.0D;
                    double var25 = 999.0D;
                    double var27 = 999.0D;
                    double var29 = p_147447_2_.xCoord - p_147447_1_.xCoord;
                    double var31 = p_147447_2_.yCoord - p_147447_1_.yCoord;
                    double var33 = p_147447_2_.zCoord - p_147447_1_.zCoord;

                    if (var41)
                    {
                        var23 = (var17 - p_147447_1_.xCoord) / var29;
                    }

                    if (var15)
                    {
                        var25 = (var19 - p_147447_1_.yCoord) / var31;
                    }

                    if (var16)
                    {
                        var27 = (var21 - p_147447_1_.zCoord) / var33;
                    }

                    boolean var35 = false;
                    byte var42;

                    if (var23 < var25 && var23 < var27)
                    {
                        if (var6 > var9)
                        {
                            var42 = 4;
                        }
                        else
                        {
                            var42 = 5;
                        }

                        p_147447_1_.xCoord = var17;
                        p_147447_1_.yCoord += var31 * var23;
                        p_147447_1_.zCoord += var33 * var23;
                    }
                    else if (var25 < var27)
                    {
                        if (var7 > var10)
                        {
                            var42 = 0;
                        }
                        else
                        {
                            var42 = 1;
                        }

                        p_147447_1_.xCoord += var29 * var25;
                        p_147447_1_.yCoord = var19;
                        p_147447_1_.zCoord += var33 * var25;
                    }
                    else
                    {
                        if (var8 > var11)
                        {
                            var42 = 2;
                        }
                        else
                        {
                            var42 = 3;
                        }

                        p_147447_1_.xCoord += var29 * var27;
                        p_147447_1_.yCoord += var31 * var27;
                        p_147447_1_.zCoord = var21;
                    }

                    Vec3 var36 = Vec3.createVectorHelper(p_147447_1_.xCoord, p_147447_1_.yCoord, p_147447_1_.zCoord);
                    var9 = (int)(var36.xCoord = (double)MathHelper.floor_double(p_147447_1_.xCoord));

                    if (var42 == 5)
                    {
                        --var9;
                        ++var36.xCoord;
                    }

                    var10 = (int)(var36.yCoord = (double)MathHelper.floor_double(p_147447_1_.yCoord));

                    if (var42 == 1)
                    {
                        --var10;
                        ++var36.yCoord;
                    }

                    var11 = (int)(var36.zCoord = (double)MathHelper.floor_double(p_147447_1_.zCoord));

                    if (var42 == 3)
                    {
                        --var11;
                        ++var36.zCoord;
                    }

                    Block var37 = w.getBlock(var9, var10, var11);
                    int var38 = w.getBlockMetadata(var9, var10, var11);

                    if (!p_147447_4_ || var37.getCollisionBoundingBoxFromPool(w, var9, var10, var11) != null)
                    {
                        if (OptiFineCompatUtil.canStopRayTrace(var37, var38, p_147447_3_))
                        {
                            MovingObjectPosition var39 = var37.collisionRayTrace(w, var9, var10, var11, p_147447_1_, p_147447_2_);

                            if (var39 != null)
                            {
                                return var39;
                            }
                        }
                        else
                        {
                            var40 = new MovingObjectPosition(var9, var10, var11, var42, p_147447_1_, false);
                        }
                    }
                }

                return p_147447_5_ ? var40 : null;
            }
            else
            {
                return null;
            }
        }
        else
        {
            return null;
        }
    }
    
    public void updateTeleportDestinations(Minecraft mc, Entity player)
    {
        mc.mcProfiler.startSection("updateTeleportDestinations");

        // no teleporting if on a server that disallows teleporting
        // A server may forbid teleporting outright, and some do: it crosses ground everyone
        // else has to walk. Said once, because a gesture that silently does nothing looks like
        // a broken mod.
        if (!VRServerRules.teleportAllowed())
        {
            if (VRServerRules.shouldExplainRefusal()) {
                ChatUtil.print("This server does not allow teleporting.");
            }
            movementTeleportDestination.xCoord = 0.0;
            movementTeleportDestination.yCoord = 0.0;
            movementTeleportDestination.zCoord = 0.0;
            movementTeleportArcSteps = 0;
            return;
        }

        if (getFreeMoveMode())
        {
            movementTeleportDestination.xCoord = 0.0;
            movementTeleportDestination.yCoord = 0.0;
            movementTeleportDestination.zCoord = 0.0;
            movementTeleportArcSteps = 0;
            return;
        }

        if (vrMovementStyle.arcAiming)
        {
            movementTeleportDestination.xCoord = 0.0;
            movementTeleportDestination.yCoord = 0.0;
            movementTeleportDestination.zCoord = 0.0;

            if (movementTeleportProgress>0.0f)
            {
                updateTeleportArc(mc, player);
            }
        }
        else //non-arc modes.
        {
            Vec3 start = this.getControllerOffhandPos_World();
            Vec3 aimDir = VR.roomScale().getControllerOffhandDir_World();

            // setup teleport forwards to the mouse cursor
            double movementTeleportDistance = 250.0;
            Vec3 movementTeleportPos = start.addVector(
                    aimDir.xCoord * movementTeleportDistance,
                    aimDir.yCoord * movementTeleportDistance,
                    aimDir.zCoord * movementTeleportDistance);
            MovingObjectPosition collision = mc.theWorld.rayTraceBlocks(start, movementTeleportPos, !mc.thePlayer.isInWater());
            Vec3 traceDir = start.subtract(movementTeleportPos).normalize();
            Vec3 reverseEpsilon = Vec3.createVectorHelper(-traceDir.xCoord * 0.02, -traceDir.yCoord * 0.02, -traceDir.zCoord * 0.02);

            // don't update while charging up a teleport
            if (movementTeleportProgress != 0)
                return;

            if (collision != null && collision.typeOfHit != MovingObjectPosition.MovingObjectType.MISS)
            {
                checkAndSetTeleportDestination(mc, player, start, collision, reverseEpsilon);
            }
        }
        mc.mcProfiler.endSection();
    }

    // look for a valid place to stand on the block that the trace collided with
    private boolean checkAndSetTeleportDestination(Minecraft mc, Entity player, Vec3 start, MovingObjectPosition collision, Vec3 reverseEpsilon)
    {
        boolean bFoundValidSpot = false;

		Block testClimb = player.worldObj.getBlock(collision.blockX, collision.blockY, collision.blockZ);

    	if (testClimb == Blocks.water){
    		Vec3 hitVec = Vec3.createVectorHelper(collision.hitVec.xCoord, collision.blockY, collision.hitVec.zCoord );

    		Vec3 offset = Vec3Util.subtract(hitVec, player.posX, player.boundingBox.minY, player.posZ);
    		AxisAlignedBB bb = player.boundingBox.copy().offset(offset.xCoord, offset.yCoord, offset.zCoord);
    		boolean emptySpotReq = mc.theWorld.getCollidingBoundingBoxes(player,bb).isEmpty();

    		if(!emptySpotReq){
    			Vec3 center = Vec3.createVectorHelper(collision.blockX, collision.blockY, collision.blockZ).addVector(0.5, 0, 0.5);
    			offset = Vec3Util.subtract(center, player.posX, player.boundingBox.minY, player.posZ);
    			bb = player.boundingBox.copy().offset(offset.xCoord, offset.yCoord, offset.zCoord);
    			emptySpotReq = mc.theWorld.getCollidingBoundingBoxes(player,bb).isEmpty(); 	
    		}
    		
    		float ex = 0;
    		if(VR.settings().seated)ex = 0.5f;
    		
    		if(emptySpotReq){
    			movementTeleportDestination = Vec3.createVectorHelper((bb.maxX + bb.minX) /2 ,bb.minY + ex, (bb.maxZ + bb.minZ)/2);
    			movementTeleportDestinationSideHit = collision.sideHit;
    			bFoundValidSpot =  true;
    		}

    	} else if (collision.sideHit != 1) 
		{ //sides
		//jrbudda require arc hitting top of block.	unless ladder or vine.
		//	System.out.println(testClimb.getUnlocalizedName() + " " + collision.typeOfHit + " " + collision.sideHit);
			   				   			   
			if(testClimb instanceof BlockLadder|| testClimb instanceof BlockVine){
			            Vec3 dest = Vec3.createVectorHelper(collision.blockX+0.5, collision.blockY + 0.5, collision.blockZ+0.5);
	            		Block playerblock = player.worldObj.getBlock((int)player.posX, (int)player.boundingBox.minY -1, (int)player.posZ);
	            		if(playerblock == testClimb) dest.yCoord-=1;
                        movementTeleportDestination.xCoord = dest.xCoord;
                        movementTeleportDestination.yCoord = dest.yCoord;
                        movementTeleportDestination.zCoord = dest.zCoord;
                        movementTeleportDestinationSideHit = collision.sideHit;
						bFoundValidSpot =  true; //really should check if the block above is passable. Maybe later.
			} else {
					if (!mc.thePlayer.capabilities.allowFlying && VR.settings().vrLimitedSurvivalTeleport) {return false;} //if creative, check if can hop on top.
			}
		}
		
        for ( int k = 0; k < 1 && !bFoundValidSpot; k++ )
        {
            Vec3 hitVec = collision.hitVec;// ( k == 1 ) ? collision.hitVec.addVector(-reverseEpsilon.xCoord, -reverseEpsilon.yCoord, -reverseEpsilon.zCoord)
                    						//: collision.hitVec.addVector(reverseEpsilon.xCoord, reverseEpsilon.yCoord, reverseEpsilon.zCoord);

            Vec3 debugPos = Vec3.createVectorHelper(
                    MathHelper.floor_double(hitVec.xCoord) + 0.5,
                    MathHelper.floor_double(hitVec.yCoord),
                    MathHelper.floor_double(hitVec.zCoord) + 0.5);

            int bx = collision.blockX;
            int bz = collision.blockZ;

            // search for a solid block with two empty blocks above it
            int startBlockY = collision.blockY -1 ; 
            startBlockY = Math.max(startBlockY, 0);
            for (int by = startBlockY; by < startBlockY + 2; by++)
            {
            	if (canStand(player.worldObj,bx, by, bz))
            	{
            		// How far this landing spot is from the player, judged separately along and
            		// up and down, because those are different kinds of shortcut: stepping up a
            		// block is nothing, dropping a few is something you could have done by
            		// jumping, and crossing sixteen is the part that replaces walking.
            		double rise = (by + 1) - player.posY;
            		if (rise > VRServerRules.upLimit()) continue;
            		if (-rise > VRServerRules.downLimit()) continue;
            		double alongX = (bx + 0.5) - player.posX;
            		double alongZ = (bz + 0.5) - player.posZ;
            		float maxTeleportDist = VRServerRules.horizLimit();
            		if (alongX * alongX + alongZ * alongZ > maxTeleportDist * maxTeleportDist) {
            			continue;
            		}

            		float var27 = 0.0625F; //uhhhh?

            		double ox = hitVec.xCoord - player.posX;
            		double oy = by + 1 - player.posY;
            		double oz = hitVec.zCoord - player.posZ;
            		AxisAlignedBB bb = player.boundingBox.copy().contract((double)var27, (double)var27, (double)var27).offset(ox, oy, oz); 
            		bb.minY = by+1f;
            		bb.maxY = by+2.8f;
            		boolean emptySpotReq = mc.theWorld.getCollidingBoundingBoxes(player,bb).isEmpty();

            		double ox2 = bx + 0.5f - player.posX;
            		double oy2 = by + 1.0f - player.posY;
            		double oz2 = bz + 0.5f - player.posZ;
            		AxisAlignedBB bb2 = player.boundingBox.copy().contract((double)var27, (double)var27, (double)var27).offset(ox2, oy2, oz2);
            		bb2.minY = by+1f;
            		bb2.maxY = by+2.8f;
            		boolean emptySpotCenter = mc.theWorld.getCollidingBoundingBoxes(player,bb2).isEmpty();

            		List l = mc.theWorld.getCollidingBoundingBoxes(player,bb2);

            		Vec3 dest;

            		//teleport to exact spot unless collision, then teleport to center.

            		if (emptySpotReq) {           	
            			dest = Vec3.createVectorHelper(hitVec.xCoord, by+1,hitVec.zCoord);
            		}
            		else {
            			dest = Vec3.createVectorHelper(bx + 0.5f, by + 1f, bz + 0.5f);
            		}

            		if (start.distanceTo(dest) <= maxTeleportDist && (emptySpotReq || emptySpotCenter))
            		{

            			testClimb = player.worldObj.getBlock(bx, by, bz);
            			
            			double y = testClimb.getBlockBoundsMaxY();
            			if (testClimb == Blocks.farmland) y = 1f; //cheeky bastard
            			
            			movementTeleportDestination.xCoord = dest.xCoord;
            			movementTeleportDestination.yCoord = y + by;
            			movementTeleportDestination.zCoord = dest.zCoord;
            			movementTeleportDestinationSideHit = collision.sideHit;

            			debugPos.xCoord = bx + 0.5;
            			debugPos.yCoord = by + 1;
            			debugPos.zCoord = bz + 0.5;

            			bFoundValidSpot = true;

            			break;

            		}
            	}

            }
        }
        
        if(bFoundValidSpot) { movementTeleportDistance = start.distanceTo(movementTeleportDestination);}
        
        return bFoundValidSpot;
    }

    private boolean canStand(World w, int bx, int by, int bz){
    	
    	return w.getBlock(bx,  by,  bz).isCollidable() && OptiFineCompatUtil.isPassable(w.getBlock(bx, by + 1, bz), w, bx, by + 1, bz) &&  OptiFineCompatUtil.isPassable(w.getBlock(bx, by + 2, bz), w, bx, by + 2, bz);
 
    }
    
    // rough interpolation between arc locations
    public Vec3 getInterpolatedArcPosition(float progress)
    {
        // not enough points to interpolate or before start
        if (movementTeleportArcSteps == 1 || progress <= 0.0f)
        {
            return Vec3.createVectorHelper(
                    movementTeleportArc[0].xCoord,
                    movementTeleportArc[0].yCoord,
                    movementTeleportArc[0].zCoord);
        }

        // past end of arc
        if (progress>=1.0f)
        {
            return Vec3.createVectorHelper(
                    movementTeleportArc[movementTeleportArcSteps-1].xCoord,
                    movementTeleportArc[movementTeleportArcSteps-1].yCoord,
                    movementTeleportArc[movementTeleportArcSteps-1].zCoord);
        }

        // which two points are we between?
        float stepFloat = progress * (float)(movementTeleportArcSteps - 1);
        int step = (int) Math.floor(stepFloat);

        double deltaX = movementTeleportArc[step+1].xCoord - movementTeleportArc[step].xCoord;
        double deltaY = movementTeleportArc[step+1].yCoord - movementTeleportArc[step].yCoord;
        double deltaZ = movementTeleportArc[step+1].zCoord - movementTeleportArc[step].zCoord;

        float stepProgress = stepFloat - step;

        return Vec3.createVectorHelper(
                movementTeleportArc[step].xCoord + deltaX * stepProgress,
                movementTeleportArc[step].yCoord + deltaY * stepProgress,
                movementTeleportArc[step].zCoord + deltaZ * stepProgress);
    }

	/**
	 * Hitting things by swinging now lives in its own tracker, with everything else
	 * physical. It was two hundred lines in the middle of this class and had stopped being
	 * readable; see {@link com.mtbs3d.minecrift.gameplay.SwingTracker}.
	 */

	public boolean getFreeMoveMode() { return freeMoveMode; }
	
	public void setFreeMoveMode(boolean free) { 
		
		
		boolean was = freeMoveMode;
		freeMoveMode = free;

		if(free != was){
			C17PacketCustomPayload pack1 = NetworkHelper.getVivecraftClientPacket(PacketDiscriminators.MOVEMODE, freeMoveMode ?  new byte[]{1} : new byte[]{0});
			C17PacketCustomPayload pack2 = new C17PacketCustomPayload("MC|Vive|FreeMove", (byte[]) (freeMoveMode ?  new byte[]{1} : new byte[]{0} ));	
			
			if(Minecraft.getMinecraft().getNetHandler() !=null){
				Minecraft.getMinecraft().getNetHandler().addToSendQueue(pack1);
				Minecraft.getMinecraft().getNetHandler().addToSendQueue(pack2);			
			}
			
			// One line, not three. This used to say the same thing twice over -- once here and
			// once from the key handler -- and then add a warning that was never true, because
			// the flag behind it was hardcoded and nothing ever cleared it.
			if (VR.settings().seated) {
				ChatUtil.print("Movement: " + (free ? "walking, on the keys" : "teleport, on W"));
			} else {
				ChatUtil.print("Movement: " + (free
						? VR.settings().getKeyBinding(VRSettings.VrOptions.FREEMOVE_MODE)
						: "teleport"));
			}

			// Said only when it is actually true, and only once a session: on a server without
			// this mod, a teleport further than nine blocks would be undone by the server, so
			// the range is held below that and the player should know why it feels short.
			if (!free && VRServerRules.shouldWarnAboutRange()) {
				ChatUtil.print("Teleport range is limited to "
						+ VRServerRules.horizLimit() + " blocks on this server.");
			}
		}
	
	}

	public float getTeleportEnergy () {return teleportEnergy;}

	Vec3 getWalkMultOffset()
	{	
		EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
		if(player==null || !VanillaExt.isInitFromServer(player))
			return Vec3.createVectorHelper(0, 0, 0);
		float walkmult=VR.settings().walkMultiplier;
		Vec3 pos=Vec3Util.scale(MCOpenVR.getCenterEyePosition(), worldScale);
		return pos.subtract(Vec3.createVectorHelper(pos.xCoord*walkmult,pos.yCoord,pos.zCoord*walkmult));
	}
	
	//================= IROOMSCALEADAPTER =============================
	
	
	public float worldScale =  VR.settings().vrWorldScale;
	public float worldRotationRadians;
	
	@Override
	public boolean isHMDTracking() {
		return MCOpenVR.headIsTracking;
	}

	@Override
	public Vec3 getHMDPos_World() {	
		Vec3 out = Vec3Util.add(Vec3Util.scale(MCOpenVR.getCenterEyePosition(), worldScale), getWalkMultOffset());
		out.rotateAroundY(worldRotationRadians);
		return out.addVector(roomOrigin.xCoord, roomOrigin.yCoord, roomOrigin.zCoord);
	}

	@Override
	public Vec3 getHMDDir_World() {
		Vector3f v3 = MCOpenVR.headDirection;
		Vec3 out = Vec3.createVectorHelper(v3.x, v3.y, v3.z);
		out.rotateAroundY(worldRotationRadians);
		return out;
	}

	@Override
	public float getHMDYaw_World() {
		Vec3 dir = getHMDDir_World();
		 return (float)Math.toDegrees(Math.atan2(-dir.xCoord, dir.zCoord));    
	}

	@Override
	public float getHMDPitch_World() {
		Vec3 dir = getHMDDir_World();
		return (float)Math.toDegrees(Math.asin(dir.yCoord/dir.lengthVector())); 
	}
	
	@Override
	public boolean isControllerMainTracking() {
		return MCOpenVR.controllerTracking[0];
	}

	@Deprecated
	public Vec3 getControllerMainPos_World() {
		Vec3 out = Vec3Util.add(Vec3Util.scale(MCOpenVR.getAimSource(0), worldScale), getWalkMultOffset());
		out.rotateAroundY(worldRotationRadians);
		return out.addVector(roomOrigin.xCoord, roomOrigin.yCoord, roomOrigin.zCoord);
		}

	@Override
	public Vec3 getControllerMainDir_World() {
		Vector3f v3 = MCOpenVR.controllerDirection;
		Vec3 out = Vec3.createVectorHelper(v3.x, v3.y, v3.z);
		out.rotateAroundY(worldRotationRadians);
		return out;
	}

	@Override
	public float getControllerMainYaw_World() {
		Vec3 dir = getControllerMainDir_World();
		return (float)Math.toDegrees(Math.atan2(-dir.xCoord, dir.zCoord)); 
	}

	@Override
	public float getControllerMainPitch_World() {
		return MCOpenVR.aimPitch;
	}

	@Override
	public boolean isControllerOffhandTracking() {
		return MCOpenVR.controllerTracking[1];
	}
	
	@Deprecated
	public Vec3 getControllerOffhandPos_World() {
		Vec3 out = Vec3Util.add(Vec3Util.scale(MCOpenVR.getAimSource(1), worldScale), getWalkMultOffset());
		out.rotateAroundY(worldRotationRadians);
		return out.addVector(roomOrigin.xCoord, roomOrigin.yCoord, roomOrigin.zCoord);
		}

	@Override
	public Vec3 getControllerOffhandDir_World() {
		Vector3f v3 = MCOpenVR.lcontrollerDirection;
		Vec3 out = Vec3.createVectorHelper(v3.x, v3.y, v3.z);
		out.rotateAroundY(worldRotationRadians);
		return out;
	}

	@Override
	public float getControllerOffhandYaw_World() {
		Vec3 dir = getControllerOffhandDir_World();
		return (float)Math.toDegrees(Math.atan2(-dir.xCoord, dir.zCoord)); 
	}

	@Override
	public float getControllerOffhandPitch_World() {
		return MCOpenVR.laimPitch;
	}

	@Override
	public Vec3 getRoomOriginPos_World() {
		return roomOrigin;
	}

	@Override
	public Vec3 getRoomOriginUpDir_World() { //ummmm
		return Vec3.createVectorHelper(0, 1, 0);
	}
	

	@Override
	public FloatBuffer getHMDMatrix_World() {
		Matrix4f out = MCOpenVR.hmdRotation;
		Matrix4f rot = Matrix4f.rotationY(worldRotationRadians);
		return Matrix4f.multiply(rot, out).toFloatBuffer();
	}
	
	@Override //always interpolated
	public Vec3 getEyePos_World(RenderPass eye) {
		Vec3 out = Vec3Util.add(Vec3Util.scale(MCOpenVR.getEyePosition(eye), worldScale), getWalkMultOffset());
		out.rotateAroundY(worldRotationRadians);
		return out.addVector(roomOrigin.xCoord, roomOrigin.yCoord, roomOrigin.zCoord);
	}
	
	@Override
	public float getControllerYaw_World(int controller) {
		Vec3 dir = getControllerDir_World(controller);
		return (float)Math.toDegrees(Math.atan2(-dir.xCoord, dir.zCoord)); 
	}

	@Override
	public float getControllerPitch_World(int controller) {
		Vec3 dir = getControllerDir_World(controller);
		return (float)Math.toDegrees(Math.asin(dir.yCoord/dir.lengthVector())); 
	}

	@Override
	public float getControllerRoll_World(int controller) {
		org.lwjgl.util.vector.Matrix4f mat = 
				(org.lwjgl.util.vector.Matrix4f)new org.lwjgl.util.vector.Matrix4f().
				load(getControllerMatrix_World_Normal(controller));
		return (float)-Math.toDegrees(Math.atan2(mat.m10, mat.m11));
	}
	
	@Override
	public FloatBuffer getControllerMatrix_World(int controller) {
		Matrix4f out = MCOpenVR.getAimRotation(controller);
		Matrix4f rot = Matrix4f.rotationY(worldRotationRadians);
		return Matrix4f.multiply(rot,out).transposed().toFloatBuffer();
	}

	public FloatBuffer getControllerMatrix_World_Normal(int controller) {
		Matrix4f out = MCOpenVR.getAimRotation(controller);
		Matrix4f rot = Matrix4f.rotationY(worldRotationRadians);
		return Matrix4f.multiply(rot,out).toFloatBuffer();
	}
	
	@Override
	public Vec3 getCustomHMDVector(Vec3 axis) {
		Vector3f v3 = MCOpenVR.hmdRotation.transform(new Vector3f((float)axis.xCoord, (float)axis.yCoord, (float)axis.zCoord));
		Vec3 out = Vec3.createVectorHelper(v3.x, v3.y, v3.z);
		out.rotateAroundY(worldRotationRadians);
		return out;
	}

	@Override
	public Vec3 getCustomControllerVector(int controller, Vec3 axis) {
		Vector3f v3 = MCOpenVR.getAimRotation(controller).transform(new Vector3f((float)axis.xCoord, (float)axis.yCoord, (float)axis.zCoord));
		Vec3 out = Vec3.createVectorHelper(v3.x, v3.y, v3.z);
		out.rotateAroundY(worldRotationRadians);
		return out;
	}

	public Vec3 getCustomHandVector(int controller, Vec3 axis) {
		Vector3f v3 = MCOpenVR.getHandRotation(controller).transform(new Vector3f((float)axis.xCoord, (float)axis.yCoord,(float) axis.zCoord));
		Vec3 out =  Vec3Util.rotateYaw(Vec3.createVectorHelper(v3.x, v3.y, v3.z), worldRotationRadians);
		return out;
	}
	
	@Override
	public Vec3 getHMDPos_Room() {
		return Vec3Util.add(Vec3Util.scale(MCOpenVR.getCenterEyePosition(), worldScale), getWalkMultOffset());
	}

	@Override
	public Vec3 getControllerPos_Room(int i) {
		return Vec3Util.add(Vec3Util.scale(MCOpenVR.getAimSource(i), worldScale), getWalkMultOffset());
	}
	
	@Override
	public Vec3 getControllerDir_Room(int c) {
		Vector3f v3 = c==0?MCOpenVR.controllerDirection : MCOpenVR.lcontrollerDirection;
		return Vec3.createVectorHelper(v3.x, v3.y, v3.z);
	}
	
	@Override
	public Vec3 getEyePos_Room(RenderPass eye) {
		return Vec3Util.add(Vec3Util.scale(MCOpenVR.getEyePosition(eye), worldScale), getWalkMultOffset());
	}

	@Override
	public FloatBuffer getHMDMatrix_Room() {
		return MCOpenVR.hmdRotation.toFloatBuffer();
	}

	@Override
	public float getControllerYaw_Room(int controller) {
		if(controller == 0) return MCOpenVR.aimYaw;
		return MCOpenVR.laimYaw;
	}

	@Override
	public float getControllerPitch_Room(int controller) {
		if(controller == 0) return MCOpenVR.aimPitch;
		return MCOpenVR.laimPitch;
	}

	@Override
	public Vec3 getControllerPos_World(int c) {
		return c == 0 ? this.getControllerMainPos_World() : this.getControllerOffhandPos_World();
	}

	/**
	 * Where a hand has been over the last moment, averaged, in world space.
	 *
	 * <p>{@link MCOpenVR#getSmoothedAimSource} answers in the room's own frame, in metres from the
	 * play area, because the gameplay trackers that wanted it measure a hand against the head and
	 * both of those are room positions. Anything that measures a hand against something standing
	 * in the world needs the same three steps the accessors above take -- world scale, world
	 * rotation, room origin -- or it is subtracting a position in one frame from a position in
	 * another and calling the difference a distance.
	 */
	public Vec3 getSmoothedControllerPos_World(int controller, double seconds) {
		Vec3 out = Vec3Util.add(
				Vec3Util.scale(MCOpenVR.getSmoothedAimSource(controller, seconds), worldScale),
				getWalkMultOffset());
		out.rotateAroundY(worldRotationRadians);
		return out.addVector(roomOrigin.xCoord, roomOrigin.yCoord, roomOrigin.zCoord);
	}

	@Override
	public Vec3 getControllerDir_World(int c) {
		Vector3f v3 = c==0?MCOpenVR.controllerDirection : MCOpenVR.lcontrollerDirection;
		Vec3 out = Vec3Util.rotateYaw(Vec3.createVectorHelper(v3.x, v3.y, v3.z), worldRotationRadians);
		return out;
	}
	
	private void hackPCMP(){

	hitBlockDelay = Utils.getDeclaredField(Minecraft.getMinecraft().playerController.getClass(), 
			"blockHitDelay", 
			"i", 
			"field_78781_i");

	isHittingBlock = Utils.getDeclaredField(Minecraft.getMinecraft().playerController.getClass(), 
			"isHittingBlock", 
			"j", 
			"field_78778_j");
	
		if(hitBlockDelay!=null){
			hitBlockDelay.setAccessible(true);
		}
		if(isHittingBlock!=null){
			isHittingBlock.setAccessible(true);
		}
	}

	public boolean getIsHittingBlock(){
		boolean ret = false;
		try {
			ret =	isHittingBlock.getBoolean(Minecraft.getMinecraft().playerController);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ret;

	}
	
	Field hitBlockDelay, isHittingBlock;
	public double lastTeleportArcDisplayOffset;
    //JRBUDDA: This is the only thing this file does, necessary? Use reflection to modify field directly from elsewhere?
    // VIVE START - function to allow damaging blocks immediately
	public void clearBlockHitDelay() { 
	
		if(hitBlockDelay == null) {
			hackPCMP();
		}
		
		if(hitBlockDelay !=null){
			try {
				hitBlockDelay.setInt(Minecraft.getMinecraft().playerController, 0);
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
    // VIVE END - function to allow damaging blocks immediately

	public Vec3 getHMDDir_Room() {
		Vector3f v3 = MCOpenVR.headDirection;
		Vec3 out = Vec3.createVectorHelper(v3.x, v3.y, v3.z);
		return out;
	}



}

