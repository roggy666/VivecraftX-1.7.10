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

public class GuiHUDSettings extends BaseGuiSettings
{
    static VRSettings.VrOptions[] hudOptions = new VRSettings.VrOptions[] {
//            VRSettings.VrOptions.HUD_HIDE,
            VRSettings.VrOptions.HUD_LOCK_TO,
            VRSettings.VrOptions.HUD_OCCLUSION,
            VRSettings.VrOptions.HUD_SCALE,
            VRSettings.VrOptions.HUD_DISTANCE,
            VRSettings.VrOptions.HUD_WRIST_OFFSET,
            VRSettings.VrOptions.HUD_PITCH,
            VRSettings.VrOptions.HUD_YAW,
            VRSettings.VrOptions.HUD_OPACITY,
            VRSettings.VrOptions.RENDER_MENU_BACKGROUND,
            VRSettings.VrOptions.CONTAINER_GUI_AT_FACE,
            VRSettings.VrOptions.TOUCH_HOTBAR,
            VRSettings.VrOptions.PHYSICAL_KEYBOARD,
            VRSettings.VrOptions.KEYBOARD_LAYOUT,
            VRSettings.VrOptions.AUTO_OPEN_KEYBOARD,
    };

    public GuiHUDSettings(GuiScreen guiScreen, VRSettings guivrSettings) {
        super( guiScreen, guivrSettings );
        screenTitle = I18n.format("vivecraftx.screen.hud");
    }

    /**
     * Adds the buttons (and other controls) to the screen in question.
     */
    public void initGui()
    {
        this.buttonList.clear();
        this.buttonList.add(new GuiSmallButtonEx(VRSettings.VrOptions.HUD_HIDE.returnEnumOrdinal(), this.width / 2 - 78, this.height / 6 - 14, VRSettings.VrOptions.HUD_HIDE, this.guivrSettings.getKeyBinding(VRSettings.VrOptions.HUD_HIDE)));
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

                if (var8 == VRSettings.VrOptions.HUD_SCALE)
                {
                    minValue = 0.35f;
                    maxValue = 2.5f;
                    increment = 0.01f;
                }
                else if (var8 == VRSettings.VrOptions.HUD_DISTANCE)
                {
                    minValue = 0.25f;
                    maxValue = 5.0f;
                    increment = 0.01f;
                }
                else if (var8 == VRSettings.VrOptions.HUD_WRIST_OFFSET)
                {
                    minValue = 0.0f;
                    maxValue = 4.0f;
                    increment = 0.25f;
                }
                else if (var8 == VRSettings.VrOptions.HUD_PITCH)
                {
                    minValue = -45f;
                    maxValue = 45f;
                    increment = 1f;
                }
                else if (var8 == VRSettings.VrOptions.HUD_YAW)
                {
                    minValue = -100f;
                    maxValue = 100f;
                    increment = 1f;
                }
                else if (var8 == VRSettings.VrOptions.HUD_OPACITY)
                {
                    minValue = 0.15f;
                    maxValue = 1.0f;
                    increment = 0.05f;
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
           if (par1GuiButton.id < 200 && par1GuiButton instanceof GuiSmallButtonEx)
            {
                VRSettings.VrOptions num = VRSettings.VrOptions.getEnumOptions(par1GuiButton.id);
                this.guivrSettings.setOptionValue(((GuiSmallButtonEx)par1GuiButton).returnVrEnumOptions(), 1);
                par1GuiButton.displayString = this.guivrSettings.getKeyBinding(VRSettings.VrOptions.getEnumOptions(par1GuiButton.id));
            }
            else if (par1GuiButton.id == ID_GENERIC_DONE)
            {
                VR.settings().saveOptions();
                this.mc.displayGuiScreen(this.parentGuiScreen);
            }
            else if (par1GuiButton.id == ID_GENERIC_DEFAULTS)
            {
                this.guivrSettings.hudDistance = 1.25f;
                this.guivrSettings.hudScale = 1.5f;
                this.guivrSettings.hudPitchOffset = -2f;
                this.guivrSettings.hudYawOffset = 0f;
                this.guivrSettings.hideGui = false;
                this.guivrSettings.hudOpacity = 0.95f;
                this.guivrSettings.menuBackground = false;
                this.guivrSettings.vrHudLockMode = guivrSettings.HUD_LOCK_HAND;
                this.guivrSettings.hudOcclusion = true;

                VR.settings().saveOptions();
                this.reinit = true;
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
            case HUD_OPACITY:
                return tooltipLines("vivecraftx.tooltip.hudOpacity");
            case HUD_HIDE:
                return tooltipLines("vivecraftx.tooltip.hudHide");
            case HUD_SCALE:
                return tooltipLines("vivecraftx.tooltip.hudScale");
            case HUD_PITCH:
                return tooltipLines("vivecraftx.tooltip.hudPitch");
            case HUD_YAW:
                return tooltipLines("vivecraftx.tooltip.hudYaw");
            case PHYSICAL_KEYBOARD:
                return tooltipLines("vivecraftx.tooltip.physicalKeyboard");
            case KEYBOARD_LAYOUT:
                return tooltipLines("vivecraftx.tooltip.keyboardLayout");
            case AUTO_OPEN_KEYBOARD:
                return tooltipLines("vivecraftx.tooltip.autoOpenKeyboard");
            case HUD_WRIST_OFFSET:
                return tooltipLines("vivecraftx.tooltip.hudWristOffset");
            case HUD_DISTANCE:
                return tooltipLines("vivecraftx.tooltip.hudDistance");
            case HUD_OCCLUSION:
                return tooltipLines("vivecraftx.tooltip.hudOcclusion");
            case RENDER_MENU_BACKGROUND:
                return tooltipLines("vivecraftx.tooltip.renderMenuBackground");
            case CONTAINER_GUI_AT_FACE:
                return tooltipLines("vivecraftx.tooltip.containerGuiAtFace");
            case HUD_LOCK_TO:
                return tooltipLines("vivecraftx.tooltip.hudLockTo");
            case TOUCH_HOTBAR:
                return tooltipLines("vivecraftx.tooltip.touchHotbar");
            default:
                return null;
        }
    }
}
