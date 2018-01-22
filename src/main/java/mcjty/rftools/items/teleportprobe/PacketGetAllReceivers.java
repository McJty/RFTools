package mcjty.rftools.items.teleportprobe;

import io.netty.buffer.ByteBuf;
import mcjty.rftools.blocks.teleporter.TeleportDestination;
import mcjty.rftools.blocks.teleporter.TeleportDestinationClientInfo;
import mcjty.rftools.blocks.teleporter.TeleportDestinations;
import mcjty.rftools.network.RFToolsMessages;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class PacketGetAllReceivers implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public PacketGetAllReceivers() {
    }

    public static class Handler implements IMessageHandler<PacketGetAllReceivers, IMessage> {
        @Override
        public IMessage onMessage(PacketGetAllReceivers message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> handle(message, ctx));
            return null;
        }

        private void handle(PacketGetAllReceivers message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            TeleportDestinations destinations = TeleportDestinations.getDestinations(player.getEntityWorld());
            List<TeleportDestinationClientInfo> destinationList = new ArrayList<> (destinations.getValidDestinations(player.getEntityWorld(), null));
            addDimensions(destinationList);
            addRfToolsDimensions(player.getEntityWorld(), destinationList);
            PacketAllReceiversReady msg = new PacketAllReceiversReady(destinationList);
            RFToolsMessages.INSTANCE.sendTo(msg, player);
        }

        private void addDimensions(List<TeleportDestinationClientInfo> destinationList) {
            WorldServer[] worlds = DimensionManager.getWorlds();
            for (WorldServer world : worlds) {
                int id = world.provider.getDimension();
                TeleportDestination destination = new TeleportDestination(new BlockPos(0, 70, 0), id);
                destination.setName("Dimension: " + id);
                TeleportDestinationClientInfo teleportDestinationClientInfo = new TeleportDestinationClientInfo(destination);
                String dimName = world.provider.getDimensionType().getName();
                teleportDestinationClientInfo.setDimensionName(dimName);
                destinationList.add(teleportDestinationClientInfo);
            }
        }

        private void addRfToolsDimensions(World world, List<TeleportDestinationClientInfo> destinationList) {
//        RfToolsDimensionManager dimensionManager = RfToolsDimensionManager.getDimensionManager(world);
//        for (Map.Entry<Integer,DimensionDescriptor> me : dimensionManager.getDimensions().entrySet()) {
//            int id = me.getKey();
//            TeleportDestination destination = new TeleportDestination(new Coordinate(0, 70, 0), id);
//            destination.setName("RfTools Dim: " + id);
//            TeleportDestinationClientInfo teleportDestinationClientInfo = new TeleportDestinationClientInfo(destination);
//            destinationList.add(teleportDestinationClientInfo);
//        }
        }

    }
}
