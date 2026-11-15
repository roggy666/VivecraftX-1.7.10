package com.mtbs3d.minecrift.gui;

import lol.gzmc.vivecraftx.VRRenderState;

import lol.gzmc.vivecraftx.VR;

import com.mtbs3d.minecrift.api.ErrorHelper;
import com.mtbs3d.minecrift.gui.framework.*;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.VRSettings;
import com.mtbs3d.minecrift.settings.VRSettings.VrOptions;

import de.fruitfly.ovr.structs.HmdParameters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;

import java.util.List;

import org.lwjgl.util.Color;

public class GuiRenderOpticsSettings  extends BaseGuiSettings implements GuiEventEx
{
    protected boolean reinit = false;

    static VRSettings.VrOptions[] monoDisplayOptions = new VRSettings.VrOptions[] {
            VRSettings.VrOptions.MONO_FOV,
            VRSettings.VrOptions.DUMMY,
            VRSettings.VrOptions.FSAA,
    };

    static VRSettings.VrOptions[] openVRDisplayOptions = new VRSettings.VrOptions[] {
            VRSettings.VrOptions.RENDER_SCALEFACTOR,
            VRSettings.VrOptions.MIRROR_DISPLAY,     
            VRSettings.VrOptions.MIRROR_EYE,
            VRSettings.VrOptions.MIRROR_CROP,
            VRSettings.VrOptions.MIRROR_GUI,
            VRSettings.VrOptions.FSAA,
            VRSettings.VrOptions.STENCIL_ON,
            VRSettings.VrOptions.VR_HOTSWITCH,
            VRSettings.VrOptions.VR_CLOSE_WITH_RUNTIME,
            VRSettings.VrOptions.VR_REMEMBER,
            VRSettings.VrOptions.VR_TOGGLE_BUTTON,
            VRSettings.VrOptions.DUMMY,

    };
    
    static VRSettings.VrOptions[] MROptions = new VRSettings.VrOptions[] {
            VRSettings.VrOptions.MIXED_REALITY_UNITY_LIKE,
            VRSettings.VrOptions.MIXED_REALITY_RENDER_HANDS,
            VRSettings.VrOptions.MIXED_REALITY_KEY_COLOR,
            VRSettings.VrOptions.MIXED_REALITY_FOV,
            VRSettings.VrOptions.MIXED_REALITY_UNDISTORTED,
            VRSettings.VrOptions.MONO_FOV,
            VRSettings.VrOptions.MIXED_REALITY_ALPHA_MASK,
    };
    
    static VRSettings.VrOptions[] UDOptions = new VRSettings.VrOptions[] {
            VRSettings.VrOptions.MONO_FOV,
    };

    GameSettings settings;
    VRSettings vrSettings;
    Minecraft mc;
    GuiSelectOption selectOption;

    public GuiRenderOpticsSettings(GuiScreen par1GuiScreen, VRSettings par2vrSettings, GameSettings gameSettings)
    {
    	super( par1GuiScreen, par2vrSettings);
        screenTitle = I18n.format("vivecraftx.screen.stereo");
        settings = gameSettings;
        this.vrSettings = par2vrSettings;
        this.mc = Minecraft.getMinecraft();
    }

    /**
     * Adds the buttons (and other controls) to the screen in question.
     */
    public void initGui()
    {
    	String productName = "";

    	// this.screenTitle = var1.translateKey("options.videoTitle");
    	this.buttonList.clear();
    	this.buttonList.add(new GuiButtonEx(ID_GENERIC_DEFAULTS, this.width / 2 - 155 ,  this.height -25 ,150,20, I18n.format("vivecraftx.button.defaults")));
    	this.buttonList.add(new GuiButtonEx(ID_GENERIC_DONE, this.width / 2 - 155  + 160, this.height -25,150,20, I18n.format("vivecraftx.button.done")));

    	addButtons(openVRDisplayOptions,0);
    	// The mixed-reality and undistorted sub-menus lived here. Both configure a camera
    	// pass that is not written, so their modes cannot be chosen and their buttons did
    	// nothing; see VRSettings.MIRROR_MIXED_REALITY.
    }

    
    public void addButtons(VRSettings.VrOptions[] buttons, int starty){
        for (int var12 = 2; var12 < buttons.length + 2; ++var12)
        {
            VRSettings.VrOptions var8 = buttons[var12 - 2];
            int width = this.width / 2 - 155 + var12 % 2 * 160;
            int height = this.height / 6 + 21 * (var12 / 2) - 10 + starty;

            if (var8 == VRSettings.VrOptions.DUMMY)
                continue;

            if (var8 == VRSettings.VrOptions.DUMMY_SMALL) {
            	starty += 5;
                continue;
            }
            
            if (var8.getEnumFloat())
            {
                float minValue = 0.0f;
                float maxValue = 1.0f;
                float increment = 0.001f;

                if (var8 == VRSettings.VrOptions.RENDER_SCALEFACTOR)
                {
                    minValue = 0.5f;
                    maxValue = 4.0f;
                    increment = 0.1f;
                }
                else if (var8 == VRSettings.VrOptions.MONO_FOV || var8 == VRSettings.VrOptions.MIXED_REALITY_FOV)
                {
                    minValue = 1f;
                    maxValue = 179f;
                    increment = 1f;
                }
                else if (var8 == VRSettings.VrOptions.MIRROR_CROP)
                {
                    minValue = 0f;
                    maxValue = 0.25f;
                    increment = 0.01f;
                }
                GuiSliderEx slider = new GuiSliderEx(var8.returnEnumOrdinal(), width, height, var8, this.guivrSettings.getKeyBinding(var8), minValue, maxValue, increment, this.guivrSettings.getOptionFloatValue(var8));
                slider.setEventHandler(this);
                slider.enabled = getEnabledState(var8);
                this.buttonList.add(slider);
            }
            else
            {
                if (false)
                {
                }
                else
                {
                    String keyBinding = this.guivrSettings.getKeyBinding(var8);
                    GuiSmallButtonEx button = new GuiSmallButtonEx(var8.returnEnumOrdinal(), width, height, var8, keyBinding);
                    button.enabled = getEnabledState(var8);
                    this.buttonList.add(button);
                }
            }
        }
    }
    
    /**
     * Fired when a control is clicked. This is the equivalent of ActionListener.actionPerformed(ActionEvent e).
     */
    protected void actionPerformed(GuiButton par1GuiButton)
    {
        VRSettings.VrOptions num = VRSettings.VrOptions.getEnumOptions(par1GuiButton.id);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (par1GuiButton.enabled)
        {
            if (par1GuiButton.id == ID_GENERIC_DONE)
            {
                VR.settings().saveOptions();
                this.mc.displayGuiScreen(this.parentGuiScreen);
            }
            else if (par1GuiButton.id == ID_GENERIC_DEFAULTS)
            {

                VR.settings().renderScaleFactor = 1.0f;
                VR.settings().displayMirrorMode = VRSettings.MIRROR_ON_CROPPED;
                VR.settings().mixedRealityKeyColor = new Color();
                VR.settings().mixedRealityRenderHands = false;
                VR.settings().insideBlockSolidColor = false;
                VR.settings().mixedRealityUnityLike = true;
                VR.settings().mixedRealityMRPlusUndistorted = true;
                VR.settings().mixedRealityAlphaMask = false;
                VR.settings().mixedRealityFov = 40;
                minecraft.gameSettings.fovSetting = 70f;
                VR.settings().useFsaa = true;
                VR.settings().vrUseStencil = true;
                VRRenderState.reinitFramebuffers = true;
			    this.guivrSettings.saveOptions();
            }
            else if (par1GuiButton.id == ID_GENERIC_MODE_CHANGE) // Mode Change
            {
                VR.settings().saveOptions();
               // selectOption = new GuiSelectOption(this, this.guivrSettings, "Select StereoProvider", "Select the render provider:", pluginModeChangeButton.getPluginNames());
                this.mc.displayGuiScreen(selectOption);
            }
            else if (par1GuiButton.id == VRSettings.VrOptions.OTHER_RENDER_SETTINGS.returnEnumOrdinal())
            {
                VR.settings().saveOptions();
                this.mc.displayGuiScreen(new GuiOtherRenderOpticsSettings(this, this.guivrSettings));
            }
            else if (par1GuiButton instanceof GuiSmallButtonEx)
            {
                this.guivrSettings.setOptionValue(((GuiSmallButtonEx)par1GuiButton).returnVrEnumOptions(), 1);
                par1GuiButton.displayString = this.guivrSettings.getKeyBinding(VRSettings.VrOptions.getEnumOptions(par1GuiButton.id));
                reinit = true;
            }

            if (num == VRSettings.VrOptions.MIRROR_DISPLAY ||
                num == VRSettings.VrOptions.FSAA)
	        {
                VRRenderState.reinitFramebuffers = true;
	        }
        }
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

    @Override
    public boolean event(int id, VRSettings.VrOptions enumm)
    {
        boolean ret = false;

        if (     enumm == VRSettings.VrOptions.RENDER_SCALEFACTOR)
        {
            VRRenderState.reinitFramebuffers = true;
            ret = true;
        }

        return ret;
    }

    @Override
    public boolean event(int id, String s)
    {
        boolean success = true;
        String title = null;
        String error = null;

        if (id == GuiSelectOption.ID_OPTION_SELECTED)
        {
//            String origId = pluginModeChangeButton.getSelectedID();
//
//            try {
//                pluginModeChangeButton.setPluginByName(s);
//                VR.settings().stereoProviderPluginID = pluginModeChangeButton.getSelectedID();
//                VR.stereo() = PluginManager.configureStereoProvider(VR.settings().stereoProviderPluginID, true);
//                VR.settings().badStereoProviderPluginID = "";
//                VR.settings().saveOptions();
//                VRRenderState.reinitFramebuffers = true;
//                this.reinit = true;
//            }
//            catch (Throwable e) {
//                e.printStackTrace();
//                error = e.getClass().getName() + ": " + e.getMessage();
//                title = "Failed to initialise stereo provider: " + pluginModeChangeButton.getSelectedName();
//                VRRenderState.errorHelper = new ErrorHelper(title, error, "Reverted to previous renderer!", VRRenderState.ERROR_DISPLAY_TIME_SECS);
//                success = false;
//            }
//
//            if (!success) {
//                pluginModeChangeButton.setPluginByID(origId);
//                VR.settings().stereoProviderPluginID = pluginModeChangeButton.getSelectedID();
//                try {
//                    VR.stereo() = PluginManager.configureStereoProvider(VR.settings().stereoProviderPluginID);
//                }
//                catch (Exception ex) {}
//            }
        }

        return success;
    }

    @Override
    protected String[] getTooltipLines(String displayString, int buttonId)
    {
        VRSettings.VrOptions e = VRSettings.VrOptions.getEnumOptions(buttonId);

    	if( e != null )
    	switch(e)
    	{
	        case FSAA:
	            return tooltipLines("vivecraftx.tooltip.fsaa");
            case RENDER_SCALEFACTOR:
                return tooltipLines("vivecraftx.tooltip.renderScalefactor");
            case MIRROR_DISPLAY:
                return tooltipLines("vivecraftx.tooltip.mirrorDisplay");
            case MIRROR_CROP:
                return tooltipLines("vivecraftx.tooltip.mirrorCrop");
            case MIRROR_EYE:
                return tooltipLines("vivecraftx.tooltip.mirrorEye");
            case MIRROR_GUI:
                return tooltipLines("vivecraftx.tooltip.mirrorGui");
            case MONO_FOV:
                return tooltipLines("vivecraftx.tooltip.monoFov");
            case STENCIL_ON:
                return tooltipLines("vivecraftx.tooltip.stencilOn");
            case VR_HOTSWITCH:
                return tooltipLines("vivecraftx.tooltip.vrHotswitch");
            case VR_REMEMBER:
                return tooltipLines("vivecraftx.tooltip.vrRemember");
            case VR_TOGGLE_BUTTON:
                return tooltipLines("vivecraftx.tooltip.vrToggleButton");
            case VR_CLOSE_WITH_RUNTIME:
                return tooltipLines("vivecraftx.tooltip.vrCloseWithRuntime");
    	default:
    		return null;
    	}
    	else
    	switch(buttonId)
    	{
	    	case ID_GENERIC_DEFAULTS:
	    		return tooltipLines("vivecraftx.tooltip.button.defaults");
    		default:
    			return null;
    	}
    }

    private boolean getEnabledState(VRSettings.VrOptions var8)
    {
        String s = var8.getEnumString();

        return true;
    }
}

