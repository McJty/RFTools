package mcjty.rftools.keys;

import mcjty.lib.debugtools.DumpBlockNBT;
import mcjty.lib.debugtools.DumpItemNBT;
import mcjty.lib.network.Arguments;
import mcjty.rftools.CommandHandler;
import mcjty.rftools.network.RFToolsMessages;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

public class KeyInputHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KeyBindings.porterNextDestination.isPressed()) {
            RFToolsMessages.sendToServer(CommandHandler.CMD_CYCLE_DESTINATION, Arguments.builder().value(true));
        } else if (KeyBindings.porterPrevDestination.isPressed()) {
            RFToolsMessages.sendToServer(CommandHandler.CMD_CYCLE_DESTINATION, Arguments.builder().value(false));
        } else if (KeyBindings.debugDumpNBTItem.isPressed()) {
            DumpItemNBT.dumpHeldItem(RFToolsMessages.INSTANCE, Minecraft.getMinecraft().player, false);
        } else if (KeyBindings.debugDumpNBTBlock.isPressed()) {
            DumpBlockNBT.dumpFocusedBlock(RFToolsMessages.INSTANCE, Minecraft.getMinecraft().player, true, false);
        }
    }
}
