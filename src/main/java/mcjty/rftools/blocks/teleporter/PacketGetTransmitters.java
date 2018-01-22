package mcjty.rftools.blocks.teleporter;

import mcjty.lib.network.CommandHandler;
import mcjty.lib.network.PacketHandler;
import mcjty.lib.network.PacketRequestListFromServer;
import mcjty.lib.varia.Logging;
import mcjty.rftools.RFTools;
import mcjty.typed.Type;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

import java.util.List;

public class PacketGetTransmitters extends PacketRequestListFromServer<TransmitterInfo, PacketGetTransmitters, PacketTransmittersReady> {

    public PacketGetTransmitters() {
    }

    public PacketGetTransmitters(BlockPos pos) {
        super(RFTools.MODID, pos, DialingDeviceTileEntity.CMD_GETTRANSMITTERS);
    }

    public static class Handler implements IMessageHandler<PacketGetTransmitters, IMessage> {
        @Override
        public IMessage onMessage(PacketGetTransmitters message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> handle(message, ctx));
            return null;
        }

        private void handle(PacketGetTransmitters message, MessageContext ctx) {
            TileEntity te = ctx.getServerHandler().player.getEntityWorld().getTileEntity(message.pos);
            if(!(te instanceof CommandHandler)) {
                Logging.log("createStartScanPacket: TileEntity is not a CommandHandler!");
                return;
            }
            CommandHandler commandHandler = (CommandHandler) te;
            List<TransmitterInfo> list = commandHandler.executeWithResultList(message.command, message.args, Type.create(TransmitterInfo.class));
            SimpleNetworkWrapper wrapper = PacketHandler.modNetworking.get(message.modid);
            PacketTransmittersReady msg = new PacketTransmittersReady(message.pos, DialingDeviceTileEntity.CLIENTCMD_GETTRANSMITTERS, list);
            wrapper.sendTo(msg, ctx.getServerHandler().player);
        }

    }
}
