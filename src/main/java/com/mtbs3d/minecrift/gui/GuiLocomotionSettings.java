package com.mtbs3d.minecrift.gui;

import lol.gzmc.vivecraftx.VR;

import com.mtbs3d.minecrift.gui.framework.*;
import com.mtbs3d.minecrift.settings.VRSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

public class GuiLocomotionSettings extends BaseGuiSettings implements GuiEventEx
{
    /**
     * How the player moves, and nothing else. Everything about what the hands do to the world
     * moved to {@link GuiInteractionSettings}: with both on one screen the last row of options
     * and the Done button landed below the bottom of the window at the usual GUI scales.
     */
    static VRSettings.VrOptions[] locomotionSettings = new VRSettings.VrOptions[]
    {
            VRSettings.VrOptions.REALISTIC_JUMP,
            VRSettings.VrOptions.REALISTIC_SNEAK,
            VRSettings.VrOptions.BCB_ON,
            VRSettings.VrOptions.REALISTIC_CLIMB,
            VRSettings.VrOptions.WALK_MULTIPLIER,
            VRSettings.VrOptions.REALISTIC_ROW,
            VRSettings.VrOptions.ALLOW_MODE_SWITCH,
            VRSettings.VrOptions.REALISTIC_SWIM,
            VRSettings.VrOptions.FIXED_HEIGHT,
            VRSettings.VrOptions.WORLD_HAPTICS,
            VRSettings.VrOptions.CHAT_HAPTICS,
            VRSettings.VrOptions.VEHICLE_ROTATION,
            VRSettings.VrOptions.REALISTIC_DISMOUNT,
            VRSettings.VrOptions.CALIBRATE_HEIGHT
    };

    static VRSettings.VrOptions[] teleportSettings = new VRSettings.VrOptions[]
    {
            VRSettings.VrOptions.WALK_UP_BLOCKS,
            VRSettings.VrOptions.LIMIT_TELEPORT,
            VRSettings.VrOptions.SIMULATE_FALLING,
            VRSettings.VrOptions.TELEPORT_HORIZ_LIMIT,
            VRSettings.VrOptions.TELEPORT_UP_LIMIT,
            VRSettings.VrOptions.TELEPORT_DOWN_LIMIT

    };
    static VRSettings.VrOptions[] freeMoveSettings = new VRSettings.VrOptions[]
    {
    		VRSettings.VrOptions.FREEMOVE_MODE,
            VRSettings.VrOptions.MOVEMENT_MULTIPLIER,
            VRSettings.VrOptions.INERTIA_FACTOR,
            VRSettings.VrOptions.FOV_REDUCTION,
            VRSettings.VrOptions.FOV_REDUCTION_MIN,
            VRSettings.VrOptions.SMOOTH_TURN_SPEED,
            VRSettings.VrOptions.JOYSTICK_DEADZONE,

    };
    
    public GuiLocomotionSettings(GuiScreen guiScreen, VRSettings guivrSettings) {
        super( guiScreen, guivrSettings );
        screenTitle = I18n.format("vivecraftx.screen.locomotion");
    }

    /**
     * Adds the buttons (and other controls) to the screen in question.
     */
    public void initGui()
    {
        this.buttonList.clear();
        this.buttonList.add(new GuiButtonEx(ID_GENERIC_DEFAULTS, this.width / 2 - 155 ,  this.height -25 ,150,20, I18n.format("vivecraftx.button.defaults")));
        this.buttonList.add(new GuiButtonEx(ID_GENERIC_DONE, this.width / 2 - 155  + 160, this.height -25,150,20, I18n.format("vivecraftx.button.done")));
        // Measured rather than written down. The two blocks below used to sit at fixed heights
        // chosen when the list above them was shorter, so every option added to it since has
        // been drawn straight through them. Asking the first block where it ended costs nothing
        // and cannot go stale.
        int next = addButtons(locomotionSettings, 0);
        VR.settings().vrFreeMove = VR.player().getFreeMoveMode();
        GuiSmallButtonEx mode = new GuiSmallButtonEx(
                VRSettings.VrOptions.MOVE_MODE.returnEnumOrdinal(), this.width / 2 - 68,
                this.height / 6 + next - 10, VRSettings.VrOptions.MOVE_MODE,
                this.guivrSettings.getKeyBinding(VRSettings.VrOptions.MOVE_MODE));
        mode.setEventHandler(this);
        this.buttonList.add(mode);
        next += 21;
        if (VR.player().getFreeMoveMode()) {
            addButtons(freeMoveSettings, next);
        } else {
            addButtons(teleportSettings, next);
        }
    }

	/**
	 * Lays a block of options out in two columns.
	 *
	 * @return the height the next block may start at, which is one row past the last one used
	 */
	private int addButtons(VRSettings.VrOptions[] buttons, int startY) {
		int extra = startY;
		int lastRow = 0;
        for (int var12 = 2; var12 < buttons.length + 2; ++var12)
        {
            VRSettings.VrOptions var8 = buttons[var12 - 2];
            int width = this.width / 2 - 155 + var12 % 2 * 160;
            int row = var12 / 2;
            int height = this.height / 6 + 21 * row - 10 + extra;
            if (row > lastRow) {
                lastRow = row;
            }

            if (var8 == VRSettings.VrOptions.DUMMY)
                continue;

            if (var8 == VRSettings.VrOptions.DUMMY_SMALL) {
                extra += 5;
                continue;
            }

            if (var8.getEnumFloat())
            {
                float minValue = 0.0f;
                float maxValue = 1.0f;
                float increment = 0.01f;

                if (var8 == VRSettings.VrOptions.MOVEMENT_MULTIPLIER)
                {
                    minValue = 0.15f;
                    maxValue = 1.3f;
                    increment = 0.01f;
                }
                if (var8 == VRSettings.VrOptions.STRAFE_MULTIPLIER)
                {
                    minValue = 0f;
                    maxValue = 1.0f;
                    increment = 0.01f;
                }
                else if (var8 == VRSettings.VrOptions.WALK_MULTIPLIER){
                    minValue=1f;
                    maxValue=10f;
                    increment=0.1f;
                }
                else if (var8 == VRSettings.VrOptions.SMOOTH_TURN_SPEED) {
                    minValue = 15f;
                    maxValue = 360f;
                    increment = 5f;
                }
                else if (var8 == VRSettings.VrOptions.JOYSTICK_DEADZONE) {
                    minValue = 0f;
                    maxValue = 0.5f;
                    increment = 0.01f;
                }
                else if (var8 == VRSettings.VrOptions.FOV_REDUCTION_MIN) {
                    minValue = 0.2f;
                    maxValue = 1.2f;
                    increment = 0.05f;
                }
                else if (var8 == VRSettings.VrOptions.TELEPORT_HORIZ_LIMIT) {
                    minValue = 0f;
                    maxValue = 64f;
                    increment = 1f;
                }
                else if (var8 == VRSettings.VrOptions.TELEPORT_UP_LIMIT
                        || var8 == VRSettings.VrOptions.TELEPORT_DOWN_LIMIT) {
                    minValue = 0f;
                    maxValue = 16f;
                    increment = 1f;
                }
                // VIVE START - new options
                GuiSliderEx slider = new GuiSliderEx(var8.returnEnumOrdinal(), width, height - 20, var8, this.guivrSettings.getKeyBinding(var8), minValue, maxValue, increment, this.guivrSettings.getOptionFloatValue(var8));
                slider.setEventHandler(this);
                slider.enabled = getEnabledState(var8);
                this.buttonList.add(slider);
            }
            else
            {
                GuiSmallButtonEx smallButton = new GuiSmallButtonEx(var8.returnEnumOrdinal(), width, height - 20, var8, this.guivrSettings.getKeyBinding(var8));
                smallButton.setEventHandler(this);
                smallButton.enabled = getEnabledState(var8);
                this.buttonList.add(smallButton);
            }
        }
		return extra + 21 * (lastRow + 1);
	}

    private boolean getEnabledState(VRSettings.VrOptions var8)
    {
        String s = var8.getEnumString();

        if(s==VRSettings.VrOptions.ALLOW_CRAWLING.getEnumString()) return false;
        if(s.equals(VRSettings.VrOptions.REALISTIC_JUMP.getEnumString()) ||
                s.equals(VRSettings.VrOptions.REALISTIC_SNEAK.getEnumString()))
            return !VR.settings().seated;
        

        return true;
    }

    /**
     * Draws the screen and all the components in it.
     */
    public void drawScreen(int par1, int par2, float par3)
    {
        if (reinit)
        {
            initGui();
            reinit = false;
        }
        super.drawScreen(par1,par2,par3);
    }

    /**
     * Fired when a control is clicked. This is the equivalent of ActionListener.actionPerformed(ActionEvent e).
     */
    protected void actionPerformed(GuiButton par1GuiButton)
    {
        VRSettings vr = VR.settings();

        if (par1GuiButton.enabled)
        {
            if (par1GuiButton.id == ID_GENERIC_DONE)
            {
                Minecraft.getMinecraft().gameSettings.saveOptions();
                VR.settings().saveOptions();
                this.mc.displayGuiScreen(this.parentGuiScreen);
            }
            else if (par1GuiButton.id == ID_GENERIC_DEFAULTS)
            {
                vr.inertiaFactor = VRSettings.INERTIA_NORMAL;
                vr.movementSpeedMultiplier = 1f;
                vr.simulateFalling = true;
                //jrbudda//
                vr.vrAllowCrawling = false;
                vr.vrAllowLocoModeSwotch = true;
                vr.vrFreeMove = false;
                vr.vrLimitedSurvivalTeleport = true;
                vr.vrShowBlueCircleBuddy = true;
                vr.walkMultiplier=1;
                vr.vrFreeMoveMode = vr.FREEMOVE_CONTROLLER;
                vr.realisticClimbEnabled = true;
                vr.realisticJumpEnabled = true;
                vr.realisticSneakEnabled = true;
                vr.realisticSwimEnabled = true;
                vr.realisticRowEnabled = true;
                vr.vehicleRotation = false;
                vr.useFOVReduction = false;
                vr.walkUpBlocks = true;
                //end jrbudda
                
                Minecraft.getMinecraft().gameSettings.viewBobbing = true;

                Minecraft.getMinecraft().gameSettings.saveOptions();
                VR.settings().saveOptions();
                this.reinit = true;
            }
            else if (par1GuiButton instanceof GuiSmallButtonEx)
            {
                VRSettings.VrOptions num = VRSettings.VrOptions.getEnumOptions(par1GuiButton.id);
                    this.guivrSettings.setOptionValue(((GuiSmallButtonEx)par1GuiButton).returnVrEnumOptions(), 1);
                    par1GuiButton.displayString = this.guivrSettings.getKeyBinding(VRSettings.VrOptions.getEnumOptions(par1GuiButton.id));
                    
                    if(num == VRSettings.VrOptions.MOVE_MODE){
                    	this.reinit = true;
                    }
                    
            }
        }
    }

    @Override
    public boolean event(int id, VRSettings.VrOptions enumm)
    {
        return true;
    }

    @Override
    public boolean event(int id, String s) {
        return true;
    }

    @Override
    protected String[] getTooltipLines(String displayString, int buttonId)
    {
        VRSettings.VrOptions e = VRSettings.VrOptions.getEnumOptions(buttonId);

        if (e == null)
            return null;

        switch(e)
        {
            case MOVEMENT_MULTIPLIER:
                return tooltipLines("vivecraftx.tooltip.movementMultiplier");
            case WALK_UP_BLOCKS:
                return tooltipLines("vivecraftx.tooltip.walkUpBlocks");
            case INERTIA_FACTOR:
                return tooltipLines("vivecraftx.tooltip.inertiaFactor");
            case SIMULATE_FALLING:
                return tooltipLines("vivecraftx.tooltip.simulateFalling");
            case ALLOW_MODE_SWITCH:
                return tooltipLines("vivecraftx.tooltip.allowModeSwitch");
            case MOVE_MODE:
                return tooltipLines("vivecraftx.tooltip.moveMode");
            case LIMIT_TELEPORT:
                return tooltipLines("vivecraftx.tooltip.limitTeleport");
            case BCB_ON:
                return tooltipLines("vivecraftx.tooltip.bcbOn");
            case REALISTIC_JUMP:
                return tooltipLines("vivecraftx.tooltip.realisticJump");
            case REALISTIC_SNEAK:
                return tooltipLines("vivecraftx.tooltip.realisticSneak");
            case SMOOTH_TURN_SPEED:
                return tooltipLines("vivecraftx.tooltip.smoothTurnSpeed");
            case JOYSTICK_DEADZONE:
                return tooltipLines("vivecraftx.tooltip.joystickDeadzone");
            case FOV_REDUCTION_MIN:
                return tooltipLines("vivecraftx.tooltip.fovReductionMin");
            case TELEPORT_HORIZ_LIMIT:
                return tooltipLines("vivecraftx.tooltip.teleportHorizLimit");
            case TELEPORT_UP_LIMIT:
                return tooltipLines("vivecraftx.tooltip.teleportUpLimit");
            case TELEPORT_DOWN_LIMIT:
                return tooltipLines("vivecraftx.tooltip.teleportDownLimit");
            case CALIBRATE_HEIGHT:
                return tooltipLines("vivecraftx.tooltip.calibrateHeight");
            case REALISTIC_DISMOUNT:
                return tooltipLines("vivecraftx.tooltip.realisticDismount");
            case WORLD_HAPTICS:
                return tooltipLines("vivecraftx.tooltip.worldHaptics");
            case CHAT_HAPTICS:
                return tooltipLines("vivecraftx.tooltip.chatHaptics");
            case FIXED_HEIGHT:
                return tooltipLines("vivecraftx.tooltip.fixedHeight");
            case REALISTIC_CLIMB:
                return tooltipLines("vivecraftx.tooltip.realisticClimb");
            case REALISTIC_SWIM:
                return tooltipLines("vivecraftx.tooltip.realisticSwim");
            case REALISTIC_ROW:
                return tooltipLines("vivecraftx.tooltip.realisticRow");
            case WALK_MULTIPLIER:
                return tooltipLines("vivecraftx.tooltip.walkMultiplier");
            case FREEMOVE_MODE:
                return tooltipLines("vivecraftx.tooltip.freemoveMode");
            case VEHICLE_ROTATION:
                return tooltipLines("vivecraftx.tooltip.vehicleRotation");
            case FOV_REDUCTION:
                return tooltipLines("vivecraftx.tooltip.fovReduction");
            default:
                return null;
        }
    }
}
