package lol.gzmc.vivecraftx.mixins;

import lol.gzmc.vivecraftx.render.VRChat;

import net.minecraftforge.client.GuiIngameForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Lets the player keep the last few chat lines on the interface panel instead of losing them.
 *
 * <p>Where the chat is drawn is settled through Forge's own event, which carries the position as a
 * writable field. How long it stays is not in any event: the fade lives inside
 * {@code GuiNewChat.drawChat}, which works it out from the counter it is handed. So the counter is
 * what this changes, and only that -- one argument at one call site, with the decision itself in
 * {@link VRChat} next to the rest of the chat's VR behaviour.
 *
 * <p>The argument rather than the method, because vanilla's chat is exactly what we want drawn:
 * the same lines, the same wrapping, the same scrollback, the same respect for the chat
 * visibility setting. Only the sense of time differs.
 */
@Mixin(GuiIngameForge.class)
public class MixinGuiIngameForge {

    @ModifyArg(
            method = "renderChat",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/gui/GuiNewChat;drawChat(I)V"))
    private int vivecraftx$chatMayStay(int updateCounter) {
        return VRChat.fadeCounter(updateCounter);
    }
}
