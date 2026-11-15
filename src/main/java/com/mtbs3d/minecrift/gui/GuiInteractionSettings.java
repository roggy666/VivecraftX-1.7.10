package com.mtbs3d.minecrift.gui;

import lol.gzmc.vivecraftx.VR;

import com.mtbs3d.minecrift.gui.framework.BaseGuiSettings;
import com.mtbs3d.minecrift.gui.framework.GuiButtonEx;
import com.mtbs3d.minecrift.gui.framework.GuiSmallButtonEx;
import com.mtbs3d.minecrift.settings.VRSettings;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * What the hands reach, hit and open, and how much of the player's own body he can see while
 * doing it. These used to sit at the top of the locomotion screen, which together with the
 * roomscale options was more rows than a 480-pixel-tall GUI has room for.
 */
public class GuiInteractionSettings extends BaseGuiSettings
{
    /** The pair of DUMMY entries leaves a blank row between what the hands do and how they are drawn. */
    static VRSettings.VrOptions[] interactionOptions = new VRSettings.VrOptions[]
    {
            VRSettings.VrOptions.WEAPON_COLLISION,
            VRSettings.VrOptions.ONLY_SWORD_COLLISION,
            VRSettings.VrOptions.SWORD_BLOCK_COLLISION,
            VRSettings.VrOptions.REDUCED_PLAYER_REACH,
            VRSettings.VrOptions.REALISTIC_BLOCK_INTERACT,
            VRSettings.VrOptions.REALISTIC_ENTITY_INTERACT,
            VRSettings.VrOptions.ANIMAL_TOUCHING,
            VRSettings.VrOptions.REALISTIC_OPENING,
            VRSettings.VrOptions.DUMMY,
            VRSettings.VrOptions.DUMMY,
            VRSettings.VrOptions.RENDER_OWN_BODY,
            VRSettings.VrOptions.SPLIT_ARMS
    };

    public GuiInteractionSettings(GuiScreen guiScreen, VRSettings guivrSettings) {
        super( guiScreen, guivrSettings );
        screenTitle = I18n.format("vivecraftx.screen.interaction");
    }

    /**
     * Adds the buttons (and other controls) to the screen in question.
     */
    public void initGui()
    {
        this.buttonList.clear();
        this.buttonList.add(new GuiButtonEx(ID_GENERIC_DEFAULTS, this.width / 2 - 155 ,  this.height -25 ,150,20, I18n.format("vivecraftx.button.defaults")));
        this.buttonList.add(new GuiButtonEx(ID_GENERIC_DONE, this.width / 2 - 155  + 160, this.height -25,150,20, I18n.format("vivecraftx.button.done")));

        VRSettings.VrOptions[] buttons = interactionOptions;

        for (int var12 = 2; var12 < buttons.length + 2; ++var12)
        {
            VRSettings.VrOptions var8 = buttons[var12 - 2];
            int width = this.width / 2 - 155 + var12 % 2 * 160;
            int height = this.height / 6 + 21 * (var12 / 2) - 10;

            if (var8 == VRSettings.VrOptions.DUMMY)
                continue;

            this.buttonList.add(new GuiSmallButtonEx(var8.returnEnumOrdinal(), width, height, var8, this.guivrSettings.getKeyBinding(var8)));
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
                this.guivrSettings.weaponCollision = true;
                this.guivrSettings.onlySwordCollision = false;
                this.guivrSettings.swordBlockCollision = true;
                this.guivrSettings.reducedPlayerReach = true;
                this.guivrSettings.realisticBlockInteractEnabled = true;
                this.guivrSettings.realisticEntityInteractEnabled = true;
                this.guivrSettings.animaltouching = true;
                this.guivrSettings.realisticOpening = true;
                this.guivrSettings.renderOwnBody = false;
                this.guivrSettings.splitArms = false;

                VR.settings().saveOptions();
                this.reinit = true;
            }
            else if (par1GuiButton instanceof GuiSmallButtonEx)
            {
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
            case WEAPON_COLLISION:
                return tooltipLines("vivecraftx.tooltip.weaponCollision");
            case ONLY_SWORD_COLLISION:
                return tooltipLines("vivecraftx.tooltip.onlySwordCollision");
            case SWORD_BLOCK_COLLISION:
                return tooltipLines("vivecraftx.tooltip.swordBlockCollision");
            case REDUCED_PLAYER_REACH:
                return tooltipLines("vivecraftx.tooltip.reducedPlayerReach");
            case REALISTIC_BLOCK_INTERACT:
                return tooltipLines("vivecraftx.tooltip.realisticBlockInteract");
            case REALISTIC_ENTITY_INTERACT:
                return tooltipLines("vivecraftx.tooltip.realisticEntityInteract");
            case ANIMAL_TOUCHING:
                return tooltipLines("vivecraftx.tooltip.animalTouching");
            case REALISTIC_OPENING:
                return tooltipLines("vivecraftx.tooltip.realisticOpening");
            case RENDER_OWN_BODY:
                return tooltipLines("vivecraftx.tooltip.renderOwnBody");
            case SPLIT_ARMS:
                return tooltipLines("vivecraftx.tooltip.splitArms");
            default:
                return null;
        }
    }
}
