package com.mtbs3d.minecrift.control;

import com.mtbs3d.minecrift.provider.MCOpenVR;

import net.minecraft.client.settings.KeyBinding;

/**
 * The groups SteamVR activates and deactivates together.
 *
 * <p>This is what lets one physical button mean different things without the player binding it
 * twice: only one of {@code INGAME} and {@code GUI} is ever active, so a trigger can swing a sword
 * in the world and click a button in a menu.
 *
 * <p>{@code usage} is what SteamVR's binding editor offers. "leftright" means the set is bound per
 * hand and the editor shows two columns; "single" means one binding serves both. The advanced sets
 * stay hidden from the editor unless the player asks for them, because they are plumbing rather
 * than controls.
 */
public enum VRInputActionSet {

    INGAME("/actions/ingame", "vivecraft.actionset.ingame", "leftright", false),
    GUI("/actions/gui", "vivecraft.actionset.gui", "leftright", false),
    GLOBAL("/actions/global", "vivecraft.actionset.global", "leftright", false),
    MOD("/actions/mod", "vivecraft.actionset.mod", "leftright", false),
    CONTEXTUAL("/actions/contextual", "vivecraft.actionset.contextual", "single", false),
    KEYBOARD("/actions/keyboard", "vivecraft.actionset.keyboard", "single", true),
    MIXED_REALITY("/actions/mixedreality", "vivecraft.actionset.mixedReality", "single", true),
    TECHNICAL("/actions/technical", "vivecraft.actionset.technical", "leftright", true);

    public final String name;
    public final String localizedName;
    public final String usage;
    public final boolean advanced;

    VRInputActionSet(String name, String localizedName, String usage, boolean advanced) {
        this.name = name;
        this.localizedName = localizedName;
        this.usage = usage;
        this.advanced = advanced;
    }

    /**
     * Which set a binding belongs in, decided by its category.
     *
     * <p>Bindings from other mods go in a set of their own, so that binding one of them cannot
     * take a button away from the game's own controls.
     */
    public static VRInputActionSet fromKeyBinding(KeyBinding keyBinding) {
        String category = keyBinding.getKeyCategory();
        if (MCOpenVR.CATEGORY_GUI.equals(category)) {
            return GUI;
        }
        if (MCOpenVR.CATEGORY_CLIMBEY.equals(category)) {
            return CONTEXTUAL;
        }
        if (MCOpenVR.CATEGORY_KEYBOARD.equals(category)) {
            return KEYBOARD;
        }
        return MCOpenVR.isModBinding(keyBinding) ? MOD : INGAME;
    }
}
