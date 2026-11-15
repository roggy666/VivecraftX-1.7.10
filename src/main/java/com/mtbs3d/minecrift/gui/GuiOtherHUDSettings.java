package com.mtbs3d.minecrift.gui;

import lol.gzmc.vivecraftx.VR;

import com.mtbs3d.minecrift.gui.framework.BaseGuiSettings;
import com.mtbs3d.minecrift.gui.framework.GuiButtonEx;
import com.mtbs3d.minecrift.gui.framework.GuiSliderEx;
import com.mtbs3d.minecrift.gui.framework.GuiSmallButtonEx;
import com.mtbs3d.minecrift.settings.VRSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

public class GuiOtherHUDSettings extends BaseGuiSettings
{
    // VIVE START - hide options not supported by tracked controller UI
    static VRSettings.VrOptions[] hudOptions = new VRSettings.VrOptions[] {
            VRSettings.VrOptions.CROSSHAIR_SCALE,
            VRSettings.VrOptions.RENDER_CROSSHAIR_MODE,
            //VRSettings.VrOptions.CROSSHAIR_ROLL,
            VRSettings.VrOptions.RENDER_BLOCK_OUTLINE_MODE,
            VRSettings.VrOptions.MENU_CROSSHAIR_SCALE,
            VRSettings.VrOptions.CROSSHAIR_OCCLUSION,
            //VRSettings.VrOptions.MAX_CROSSHAIR_DISTANCE_AT_BLOCKREACH,
            VRSettings.VrOptions.CROSSHAIR_SCALES_WITH_DISTANCE,
            VRSettings.VrOptions.CHAT_FADE_AWAY,
            VRSettings.VrOptions.DUMMY,
    };
    // VIVE END - hide options not supported by tracked controller UI

    public GuiOtherHUDSettings(GuiScreen guiScreen, VRSettings guivrSettings) {
        super( guiScreen, guivrSettings );
        screenTitle = I18n.format("vivecraftx.screen.crosshair");
    }

    /**
     * Adds the buttons (and other controls) to the screen in question.
     */
    public void initGui()
    {
        this.buttonList.clear();
        this.buttonList.add(new GuiButtonEx(ID_GENERIC_DEFAULTS, this.width / 2 - 155 ,  this.height -25 ,150,20, I18n.format("vivecraftx.button.defaults")));
        this.buttonList.add(new GuiButtonEx(ID_GENERIC_DONE, this.width / 2 - 155  + 160, this.height -25,150,20, I18n.format("vivecraftx.button.done")));
        
        VRSettings.VrOptions[] buttons = hudOptions;

        for (int var12 = 2; var12 < buttons.length + 2; ++var12)
        {
            VRSettings.VrOptions var8 = buttons[var12 - 2];
            int width = this.width / 2 - 155 + var12 % 2 * 160;
            int height = this.height / 6 + 21 * (var12 / 2) - 10;

            if (var8 == VRSettings.VrOptions.DUMMY)
                continue;

            if (var8.getEnumFloat())
            {
                float minValue = 0.0f;
                float maxValue = 1.0f;
                float increment = 0.01f;

                if (var8 == VRSettings.VrOptions.CROSSHAIR_SCALE)
                {
                    minValue = 0.25f;
                    maxValue = 2.5f;
                    increment = 0.01f;
                }
                else if (var8 == VRSettings.VrOptions.MENU_CROSSHAIR_SCALE)
                {
                    minValue = 0.25f;
                    maxValue = 2.5f;
                    increment = 0.01f;
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
                this.guivrSettings.crosshairScale = 1.0f;
                this.guivrSettings.renderBlockOutlineMode = VRSettings.RENDER_BLOCK_OUTLINE_MODE_ALWAYS;
                this.guivrSettings.renderInGameCrosshairMode = VRSettings.RENDER_CROSSHAIR_MODE_ALWAYS;
                this.guivrSettings.menuCrosshairScale = 1f;
                this.guivrSettings.useCrosshairOcclusion = false;
                this.guivrSettings.crosshairScalesWithDistance = false;
                this.guivrSettings.chatFadeAway = true;


                VR.settings().saveOptions();
                this.reinit = true;
            }
            else if (par1GuiButton instanceof GuiSmallButtonEx)
            {
                VRSettings.VrOptions num = VRSettings.VrOptions.getEnumOptions(par1GuiButton.id);
                this.guivrSettings.setOptionValue(((GuiSmallButtonEx)par1GuiButton).returnVrEnumOptions(), 1);
                par1GuiButton.displayString = this.guivrSettings.getKeyBinding(VRSettings.VrOptions.getEnumOptions(par1GuiButton.id));
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
            case RENDER_CROSSHAIR_MODE:
                return tooltipLines("vivecraftx.tooltip.renderCrosshairMode");
            case CROSSHAIR_SCALE:
                return tooltipLines("vivecraftx.tooltip.crosshairScale");
            case MENU_CROSSHAIR_SCALE:
                return tooltipLines("vivecraftx.tooltip.menuCrosshairScale");
            case RENDER_BLOCK_OUTLINE_MODE:
                return tooltipLines("vivecraftx.tooltip.renderBlockOutlineMode");
            case CROSSHAIR_SCALES_WITH_DISTANCE:
                return tooltipLines("vivecraftx.tooltip.crosshairScalesWithDistance");
            case CROSSHAIR_OCCLUSION:
                return tooltipLines("vivecraftx.tooltip.crosshairOcclusion");
            case CHAT_FADE_AWAY:
                return tooltipLines("vivecraftx.tooltip.chatFadeAway");
            default:
                return null;
        }
    }
}
