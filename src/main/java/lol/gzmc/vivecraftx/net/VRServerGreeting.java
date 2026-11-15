package lol.gzmc.vivecraftx.net;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import net.minecraft.entity.player.EntityPlayerMP;

import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * Tells each arriving player what this server allows.
 *
 * <p>Sent unasked, as they join, rather than waiting for them to ask: a client that has to ask
 * would spend its first seconds not knowing whether it may teleport, which is exactly the
 * period during which somebody tries to.
 *
 * <p>Harmless to a client without this mod -- the packet goes to a channel it has not
 * registered and is dropped.
 */
public class VRServerGreeting {

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        if (!(event.entity instanceof EntityPlayerMP)) {
            return;
        }
        VRNetwork.sendRules((EntityPlayerMP) event.entity);
    }
}
