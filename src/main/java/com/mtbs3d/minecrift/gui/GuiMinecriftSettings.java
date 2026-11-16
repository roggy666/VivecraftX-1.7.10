/**
  * Copyright 2013 Mark Browning, StellaArtois
 * Licensed under the LGPL 3.0 or later (See LICENSE.md for details)
 */
package com.mtbs3d.minecrift.gui;

import lol.gzmc.vivecraftx.VR;

import com.mtbs3d.minecrift.api.IStereoProvider;
import com.mtbs3d.minecrift.gui.framework.BaseGuiSettings;
import com.mtbs3d.minecrift.gui.framework.GuiButtonEx;
import com.mtbs3d.minecrift.gui.framework.GuiEventEx;
import com.mtbs3d.minecrift.gui.framework.GuiSliderEx;
import com.mtbs3d.minecrift.gui.framework.GuiSmallButtonEx;
import com.mtbs3d.minecrift.gui.framework.VROption;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.VRSettings;
import com.mtbs3d.minecrift.settings.VRSettings.VrOptions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;


public class GuiMinecriftSettings extends BaseGuiSettings implements GuiEventEx
{
    public static final int PROFILES_ID = 915;
    public static final int INTERACTION_ID = 212;

    

    // Rows one to four are the way in to the sub-screens, so the four of them sit together
    // above the settings this screen owns. Everything below used to start half a row higher,
    // which left no room for a fourth pair of buttons.
    static VROption[] vrAlwaysOptions = new VROption[]
        {
            new VROption(202,                                      VROption.Position.POS_RIGHT,  2,  VROption.ENABLED, "vivecraftx.menu.hud"),
            new VROption(206,                                      VROption.Position.POS_LEFT,   1f, VROption.ENABLED, "vivecraftx.menu.stereo"),
            new VROption(207,								         VROption.Position.POS_RIGHT,  1f, VROption.ENABLED, "vivecraftx.menu.quickCommands"),
            new VROption(210, 							           VROption.Position.POS_RIGHT,  3f, VROption.ENABLED, "vivecraftx.menu.crosshair"),
            new VROption(INTERACTION_ID,                           VROption.Position.POS_LEFT,   4f, VROption.ENABLED, "vivecraftx.menu.interaction"),
            new VROption(VRSettings.VrOptions.PLAY_MODE_SEATED,       VROption.Position.POS_LEFT,   5.5f, VROption.ENABLED, null),
            new VROption(VRSettings.VrOptions.WORLD_SCALE,       	VROption.Position.POS_LEFT,   7f, VROption.ENABLED, null),
            new VROption(VRSettings.VrOptions.WORLD_ROTATION,       VROption.Position.POS_RIGHT,   7f, VROption.ENABLED, null),
            new VROption(VRSettings.VrOptions.RESET_ORIGIN,       VROption.Position.POS_LEFT,   8f, VROption.ENABLED, null),

        };

    static VROption[] vrStandingOptions = new VROption[]
            {
                new VROption(209,                                      VROption.Position.POS_LEFT,   2f, VROption.ENABLED, "vivecraftx.menu.locomotion"),
                new VROption(220, 							           VROption.Position.POS_LEFT,   3f, VROption.ENABLED, "vivecraftx.menu.controls"),
                new VROption(VRSettings.VrOptions.REVERSE_HANDS,       VROption.Position.POS_RIGHT,   5.5f, VROption.ENABLED, null),
                new VROption(VRSettings.VrOptions.WORLD_ROTATION_INCREMENT,VROption.Position.POS_RIGHT,   8f, VROption.ENABLED, null)
            };

    static VROption[] vrSeatedOptions = new VROption[]
            {
                    new VROption(211,                                      VROption.Position.POS_LEFT,   3f, VROption.ENABLED, "vivecraftx.menu.seated"),
            };

    static VROption[] vrConfirm = new VROption[]
            {
                    new VROption(222,                                      VROption.Position.POS_RIGHT,  2,  VROption.ENABLED, "vivecraftx.button.cancel"),
                    new VROption(223,                                      VROption.Position.POS_LEFT,   2, VROption.ENABLED, "vivecraftx.button.ok"),
            };
    
    boolean isConfirm = false;
    /** An array of all of EnumOption's video options. */

    GameSettings settings;

    public GuiMinecriftSettings( GuiScreen par1GuiScreen,
                                VRSettings par2vrSettings,
                                GameSettings gameSettings)
    {
    	super( par1GuiScreen, par2vrSettings );
    	screenTitle = I18n.format("vivecraftx.screen.vrSettings");
        settings = gameSettings;
    }

    private GuiSliderEx rotationSlider;
    
    /**
     * Adds the buttons (and other controls) to the screen in question.
     */
    public void initGui()
    {
    	this.buttonList.clear();
    	int profileButtonWidth = 240;

    	if(!isConfirm){
        	screenTitle = I18n.format("vivecraftx.screen.vrSettings");
    		GuiSmallButtonEx profilesButton = new GuiSmallButtonEx(PROFILES_ID, (this.width / 2 - 155 + 1 * 160 / 2) - ((profileButtonWidth - 150) / 2), this.height / 6 - 14, profileButtonWidth, 20, I18n.format("vivecraftx.button.profile", VRSettings.getCurrentProfile()));
    		this.buttonList.add(profilesButton);
    		this.buttonList.add(new GuiButtonEx(ID_GENERIC_DEFAULTS, this.width / 2 - 155 ,  this.height -25 ,150,20, I18n.format("vivecraftx.button.defaults")));
    		this.buttonList.add(new GuiButtonEx(ID_GENERIC_DONE, this.width / 2 - 155  + 160, this.height -25,150,20, I18n.format("vivecraftx.button.done")));
    		VROption[] buttons = null;

    		buttons = vrAlwaysOptions;

    		processButtons(buttons);

    		if(VR.settings().seated) {
    			processButtons(vrSeatedOptions);
    		}else 
    			processButtons(vrStandingOptions);

    	}
    	else {
            this.screenTitle = I18n.format("vivecraftx.screen.seatedConfirm");
			processButtons(vrConfirm);
    	}
    }

	private void processButtons(VROption[] buttons) {
		for (VROption var8 : buttons)
    	{
    		int width = var8.getWidth(this.width);
    		int height = var8.getHeight(this.height);
    		VrOptions o = VrOptions.getEnumOptions(var8.getOrdinal());
    		
    		if(o==VrOptions.RESET_ORIGIN && (!guivrSettings.seated && MCOpenVR.isVive())) continue;
    		
    		if(o==null || o.getEnumBoolean() ){
      			GuiSmallButtonEx button = new GuiSmallButtonEx(var8.getOrdinal(), width, height, var8._e, var8.getButtonText());
    			button.enabled = var8._enabled;
    			this.buttonList.add(button);
    		}
    		else if (o.getEnumFloat()){
                float minValue = 0.0f;
                float maxValue = 1.0f;
                float increment = 0.001f;
                
    			if(o == VrOptions.WORLD_SCALE){
                     minValue = 0f;
                     maxValue = 29f;
                     increment = 1f;
    			}
    			else if (o == VrOptions.WORLD_ROTATION){
                     minValue = 0f;
                     maxValue = 360f;
                     // Zero is a legitimate increment -- it is what "smooth" means -- and a
                     // slider that snaps to multiples of it divides by it. Handed straight over,
                     // this knob was pinned to its left end for anyone using smooth turning.
                     increment = VR.settings().vrWorldRotationIncrement;
                     if (increment <= 0f) {
                         increment = 1f;
                     }
    			}
    			else if (o == VrOptions.WORLD_ROTATION_INCREMENT){
                    minValue = 0f;
                    // Six positions, not five: the setter maps 0 through 5 onto smooth, 10, 36,
                    // 45, 90 and 180 degrees, so stopping at four put 180 out of reach entirely.
                    maxValue = 5f;
                    increment = 1f;
   			}
    	        GuiSliderEx slider = new GuiSliderEx(o.returnEnumOrdinal(), width, height, o, this.guivrSettings.getKeyBinding(o), minValue, maxValue, increment, this.guivrSettings.getOptionFloatValue(o));
    	        slider.setEventHandler(this);
    	        slider.enabled = true;
    	        
    	        this.buttonList.add(slider);
    	        if (o == VrOptions.WORLD_ROTATION)rotationSlider = slider;
    		}
	   	}
	}

    /**
     * Fired when a control is clicked. This is the equivalent of ActionListener.actionPerformed(ActionEvent e).
     */
    protected void actionPerformed(GuiButton par1GuiButton)
    {
        if (par1GuiButton.enabled)
        {
            VRSettings vr = VR.settings();
//            IHMDInfo hmdInfo = Minecraft.getMinecraft().hmdInfo;
            IStereoProvider stereoProvider = VR.stereo();
//            IOrientationProvider headTracker = Minecraft.getMinecraft().headTracker;
//            IEyePositionProvider positionTracker = Minecraft.getMinecraft().positionTracker;

            if (par1GuiButton.id < 200 && par1GuiButton instanceof GuiSmallButtonEx)
            {
            	VRSettings.VrOptions num = VRSettings.VrOptions.getEnumOptions(par1GuiButton.id);
          
            	if (num == VRSettings.VrOptions.PLAY_MODE_SEATED)
                {
                	this.reinit = true;
            		if(VR.settings().seated == false){
                    	this.isConfirm = true;
                    	return;
                    }
                }
    			else if(num== VRSettings.VrOptions.RESET_ORIGIN){
    				MCOpenVR.resetPosition();
    				VR.settings().saveOptions();
    				this.mc.displayGuiScreen(null);
    				this.mc.setIngameFocus();
    			}

                this.guivrSettings.setOptionValue(((GuiSmallButtonEx)par1GuiButton).returnVrEnumOptions(), 1);
                par1GuiButton.displayString = this.guivrSettings.getKeyBinding(VRSettings.VrOptions.getEnumOptions(par1GuiButton.id));

            }
            else if (par1GuiButton.id == 201)
            {
                VR.settings().saveOptions();
              //  this.mc.displayGuiScreen(new GuiPlayerPreferenceSettings(this, this.guivrSettings));
            }
            else if (par1GuiButton.id == 202)
            {

                    VR.settings().saveOptions();
                    this.mc.displayGuiScreen(new GuiHUDSettings(this, this.guivrSettings));

            }
            else if (par1GuiButton.id == 206)
            {

                    VR.settings().saveOptions();
	                this.mc.displayGuiScreen(new GuiRenderOpticsSettings(this, this.guivrSettings, this.settings));

            } 
            else if (par1GuiButton.id == 207)
            {

                    VR.settings().saveOptions();
	                this.mc.displayGuiScreen(new GuiQuickCommandEditor(this, this.guivrSettings));

            } 
            else if (par1GuiButton.id == ID_GENERIC_DONE)
            {
                VR.settings().saveOptions();
                this.mc.displayGuiScreen(this.parentGuiScreen);
            }
            else if (par1GuiButton.id == 209)
            {
                this.guivrSettings.saveOptions();
                this.mc.displayGuiScreen(new GuiLocomotionSettings(this, this.guivrSettings));
            }
            else if (par1GuiButton.id == 210)
            {
                this.guivrSettings.saveOptions();
                this.mc.displayGuiScreen(new GuiOtherHUDSettings(this, this.guivrSettings));
            }
            else if (par1GuiButton.id == INTERACTION_ID)
            {
                this.guivrSettings.saveOptions();
                this.mc.displayGuiScreen(new GuiInteractionSettings(this, this.guivrSettings));
            }
            else if (par1GuiButton.id == 211)
            {
                this.guivrSettings.saveOptions();
                this.mc.displayGuiScreen(new GuiSeatedOptions(this, this.guivrSettings));
            }
            else if (par1GuiButton.id == 220)
            {
                this.guivrSettings.saveOptions();
                this.mc.displayGuiScreen(new GuiVRControls(this, this.guivrSettings));
            }
            else if (par1GuiButton.id == 222)
            {
            	VR.settings().seated = false;
                this.guivrSettings.saveOptions();
            	this.isConfirm = false;
            	this.reinit = true;
            }
            else if (par1GuiButton.id == 223)
            {
            	VR.settings().seated = true;
                this.guivrSettings.saveOptions();
            	this.isConfirm = false;
            	this.reinit = true;
            }
            else if (par1GuiButton.id == ID_GENERIC_DEFAULTS)
            {
                VR.settings().vrReverseHands = false;
                VR.settings().vrWorldRotation = 0;
                VR.settings().vrWorldScale = 1;
                VR.settings().vrWorldRotationIncrement = 45f;
                VR.settings().seated = false;
				MCOpenVR.clearOffset();
                this.guivrSettings.saveOptions();
            	this.reinit = true;
            }
            else if (par1GuiButton.id == PROFILES_ID)
            {
                VR.settings().saveOptions();
                this.mc.displayGuiScreen(new GuiSelectSettingsProfile(this, this.guivrSettings));
            }        	
        }
    }

    @Override
    protected String[] getTooltipLines(String displayString, int buttonId)
    {
        VRSettings.VrOptions e = VRSettings.VrOptions.getEnumOptions(buttonId);

    	if( e != null )
    	switch(e)
    	{
     	case REVERSE_HANDS:
    		return tooltipLines("vivecraftx.tooltip.reverseHands");
        case WORLD_SCALE:
            return tooltipLines("vivecraftx.tooltip.worldScale");
        case WORLD_ROTATION:
            return tooltipLines("vivecraftx.tooltip.worldRotation");
        case WORLD_ROTATION_INCREMENT:
            return tooltipLines("vivecraftx.tooltip.worldRotationIncrement");
        case PLAY_MODE_SEATED:
            return tooltipLines("vivecraftx.tooltip.playModeSeated");
        case RESET_ORIGIN:
            return tooltipLines("vivecraftx.tooltip.resetOrigin");
            default:
    		return null;
    	}
    	else
    	switch(buttonId)
    	{
	    	case 202:
	    		return tooltipLines("vivecraftx.tooltip.menu.hud");
	    	case 206:
	    		return tooltipLines("vivecraftx.tooltip.menu.stereo");
            case 209:
                return tooltipLines("vivecraftx.tooltip.menu.locomotion");
            case INTERACTION_ID:
                return tooltipLines("vivecraftx.tooltip.menu.interaction");
            case PROFILES_ID:
                return tooltipLines("vivecraftx.tooltip.menu.profiles");
    		default:
    			return null;
    	}
    }

	@Override
	public boolean event(int id, VrOptions enumm) {
		if(enumm == VrOptions.WORLD_ROTATION_INCREMENT){
	        VR.settings().vrWorldRotation = 0;
	        rotationSlider.increment = VR.settings().vrWorldRotationIncrement;
	        rotationSlider.setValue(0);			
		}

		
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean event(int id, String s) {
		// TODO Auto-generated method stub
		return false;
	}

}