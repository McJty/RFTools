package mcjty.rftools.blocks.shield;

import mcjty.lib.network.CommandHandler;
import mcjty.lib.network.PacketRequestListFromServer;
import mcjty.lib.varia.Logging;
import mcjty.rftools.RFTools;
import mcjty.rftools.blocks.shield.filters.ShieldFilter;
import mcjty.rftools.network.RFToolsMessages;
import mcjty.typed.Type;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

public class PacketGetFilters extends PacketRequestListFromServer<ShieldFilter, PacketGetFilters, PacketFiltersReady> {

    public PacketGetFilters() {
    }

    public PacketGetFilters(BlockPos pos) {
        super(RFTools.MODID, pos, ShieldTEBase.CMD_GETFILTERS);
    }

    public static class Handler implements IMessageHandler<PacketGetFilters, IMessage> {
        @Override
        public IMessage onMessage(PacketGetFilters message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> handle(message, ctx));
            return null;
        }

        private void handle(PacketGetFilters message, MessageContext ctx) {
            TileEntity te = ctx.getServerHandler().player.getEntityWorld().getTileEntity(message.pos);
            if(!(te instanceof CommandHandler)) {
                Logging.log("createStartScanPacket: TileEntity is not a CommandHandler!");
                return;
            }
            CommandHandler commandHandler = (CommandHandler) te;
            List<ShieldFilter> list = commandHandler.executeWithResultList(message.command, message.args, Type.create(ShieldFilter.class));
            RFToolsMessages.INSTANCE.sendTo(new PacketFiltersReady(message.pos, ShieldTEBase.CLIENTCMD_GETFILTERS, list), ctx.getServerHandler().player);
        }
    }
}
