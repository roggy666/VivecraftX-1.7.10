package com.mtbs3d.minecrift.gui;

import lol.gzmc.vivecraftx.VR;

import com.mtbs3d.minecrift.gui.framework.BaseGuiSettings;
import com.mtbs3d.minecrift.gui.framework.GuiButtonEx;
import com.mtbs3d.minecrift.gui.framework.GuiEventEx;
import com.mtbs3d.minecrift.gui.framework.GuiSliderEx;
import com.mtbs3d.minecrift.gui.framework.GuiSmallButtonEx;
import com.mtbs3d.minecrift.provider.MCOpenVR;
import com.mtbs3d.minecrift.settings.VRSettings;
import com.mtbs3d.minecrift.settings.VRSettings.VrOptions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

public class GuiSeatedOptions extends BaseGuiSettings implements GuiEventEx
{
	static VRSettings.VrOptions[] seatedOptions = new VRSettings.VrOptions[] {
			VRSettings.VrOptions.X_SENSITIVITY,
			VRSettings.VrOptions.Y_SENSITIVITY,
			VRSettings.VrOptions.KEYHOLE,
            VRSettings.VrOptions.SEATED_HUD_XHAIR
	};
	
	
    static VRSettings.VrOptions[] teleportSettings = new VRSettings.VrOptions[]
    {
            VRSettings.VrOptions.LIMIT_TELEPORT,
            VRSettings.VrOptions.SIMULATE_FALLING

    };
    static VRSettings.VrOptions[] freeMoveSettings = new VRSettings.VrOptions[]
    {
        VRSettings.VrOptions.SEATED_HMD,
        VRSettings.VrOptions.FOV_REDUCTION,
    };

	public GuiSeatedOptions(GuiScreen guiScreen, VRSettings guivrSettings) {
		super( guiScreen, guivrSettings );
		screenTitle = I18n.format("vivecraftx.screen.seated");
	}

	/**
	 * Adds the buttons (and other controls) to the screen in question.
	 */
	public void initGui()
	{
		this.buttonList.clear();
		this.buttonList.add(new GuiButtonEx(ID_GENERIC_DEFAULTS, this.width / 2 - 155 ,  this.height -25 ,150,20, I18n.format("vivecraftx.button.defaults")));
		this.buttonList.add(new GuiButtonEx(ID_GENERIC_DONE, this.width / 2 - 155  + 160, this.height -25,150,20, I18n.format("vivecraftx.button.done")));
      
		GuiSmallButtonEx mode = new GuiSmallButtonEx(VRSettings.VrOptions.MOVE_MODE.returnEnumOrdinal(), this.width / 2 - 68, this.height / 6 + 80,VRSettings.VrOptions.MOVE_MODE, this.guivrSettings.getKeyBinding(VRSettings.VrOptions.MOVE_MODE));
        mode.setEventHandler(this);
        this.buttonList.add(mode);

		VRSettings.VrOptions[] buttons = seatedOptions;

		addButtons(buttons, 0);
		VR.settings().vrFreeMove = VR.player().getFreeMoveMode();
        if(VR.settings().vrFreeMove)
        	addButtons(freeMoveSettings,105);
        else
        	addButtons(teleportSettings,105); 
        
	}

	private void addButtons(VRSettings.VrOptions[] buttons, int y) {
		for (int var12 = 2; var12 < buttons.length + 2; ++var12)
		{
			VRSettings.VrOptions var8 = buttons[var12 - 2];
			int width = this.width / 2 - 155 + var12 % 2 * 160;
			int height = this.height / 6 + 21 * (var12 / 2) - 10 + y;

			if (var8 == VRSettings.VrOptions.DUMMY)
				continue;

			if (var8.getEnumFloat())
			{
				float minValue = 0.0f;
				float maxValue = 1.0f;
				float increment = 0.01f;

				if (var8 == VRSettings.VrOptions.X_SENSITIVITY)
				{
					minValue = 0.1f;
					maxValue = 5f;
					increment = 0.01f;
				}
				else if (var8 == VRSettings.VrOptions.Y_SENSITIVITY)
				{
					minValue = 0.1f;
					maxValue = 5f;
					increment = 0.01f;
				}
				else if (var8 == VRSettings.VrOptions.KEYHOLE)
				{
					minValue = 0f;
					maxValue = 40f;
					increment = 5f;
				}

				this.buttonList.add(new GuiSliderEx(var8.returnEnumOrdinal(), width, height, var8, this.guivrSettings.getKeyBinding(var8), minValue, maxValue, increment, this.guivrSettings.getOptionFloatValue(var8)));
			}
			else
			{
				this.buttonList.add(new GuiSmallButtonEx(var8.returnEnumOrdinal(), width, height, var8, this.guivrSettings.getKeyBinding(var8)));
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
			if (par1GuiButton.id == ID_GENERIC_DONE)
			{
				VR.settings().saveOptions();
				this.mc.displayGuiScreen(this.parentGuiScreen);
			}
			else if (par1GuiButton.id == ID_GENERIC_DEFAULTS)
			{
				VRSettings vrSettings=VR.settings();
				VR.settings().keyholeX=15;
				VR.settings().xSensitivity=1;
				VR.settings().ySensitivity=1;
				VR.settings().vrFreeMove = true;
				VR.settings().useFOVReduction = false;
				VR.settings().seatedHudAltMode = false;
				VR.settings().seatedUseHMD = false;
				
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
	protected String[] getTooltipLines(String displayString, int buttonId)
	{
        VRSettings.VrOptions e = VRSettings.VrOptions.getEnumOptions(buttonId);

        if (e == null)
            return null;

        switch(e)
        {
            case KEYHOLE:
                return tooltipLines("vivecraftx.tooltip.keyhole");
            case X_SENSITIVITY:
                return tooltipLines("vivecraftx.tooltip.xSensitivity");
            case Y_SENSITIVITY:
                return tooltipLines("vivecraftx.tooltip.ySensitivity");
            case MOVE_MODE:
                return tooltipLines("vivecraftx.tooltip.seatedMoveMode");
            case LIMIT_TELEPORT:
                return tooltipLines("vivecraftx.tooltip.limitTeleport");
            case SIMULATE_FALLING:
                return tooltipLines("vivecraftx.tooltip.simulateFalling");
            case SEATED_HUD_XHAIR:
                return tooltipLines("vivecraftx.tooltip.seatedHudXhair");
            case SEATED_HMD:
                return tooltipLines("vivecraftx.tooltip.seatedHmd");
            case FOV_REDUCTION:
                return tooltipLines("vivecraftx.tooltip.fovReduction");
            default:
                return null;
        }
    }

	@Override
	public boolean event(int id, VrOptions enumm) {
		return true;
	}

	@Override
	public boolean event(int id, String s) {
		return true;
	}
}
