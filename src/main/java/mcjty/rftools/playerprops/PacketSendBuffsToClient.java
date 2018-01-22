package mcjty.rftools.playerprops;

import io.netty.buffer.ByteBuf;
import mcjty.rftools.PlayerBuff;
import mcjty.rftools.RFTools;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PacketSendBuffsToClient implements IMessage {
    private List<PlayerBuff> buffs;

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readByte();
        buffs = new ArrayList<>(size);
        for (int i = 0 ; i < size ; i++) {
            buffs.add(PlayerBuff.values()[buf.readByte()]);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(buffs.size());
        for (PlayerBuff buff : buffs) {
            buf.writeByte(buff.ordinal());
        }
    }

    public PacketSendBuffsToClient() {
        buffs = null;
    }

    public PacketSendBuffsToClient(Map<PlayerBuff,Integer> buffs) {
        this.buffs = new ArrayList<>(buffs.keySet());
    }

    public List<PlayerBuff> getBuffs() {
        return buffs;
    }

    public static class Handler implements IMessageHandler<PacketSendBuffsToClient, IMessage> {
        @Override
        public IMessage onMessage(PacketSendBuffsToClient message, MessageContext ctx) {
            RFTools.proxy.addScheduledTaskClient(() -> handle(message, ctx));
            return null;
        }

        private void handle(PacketSendBuffsToClient message, MessageContext ctx) {
            SendBuffsToClientHelper.setBuffs(message);
        }
    }

}
