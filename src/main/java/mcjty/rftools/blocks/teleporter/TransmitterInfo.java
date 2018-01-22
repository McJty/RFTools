package mcjty.rftools.blocks.teleporter;

import io.netty.buffer.ByteBuf;
import mcjty.lib.network.NetworkTools;
import net.minecraft.util.math.BlockPos;

public class TransmitterInfo {
    private final BlockPos coordinate;
    private final String name;
    private final TeleportDestination teleportDestination;

    public TransmitterInfo(ByteBuf buf) {
        coordinate = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        name = NetworkTools.readString(buf);
        teleportDestination = new TeleportDestination(buf);
    }

    public TransmitterInfo(BlockPos coordinate, String name, TeleportDestination destination) {
        this.coordinate = coordinate;
        this.name = name;
        if (destination == null) {
            this.teleportDestination = new TeleportDestination(null, 0);
        } else {
            this.teleportDestination = destination;
        }
    }

    public void toBytes(ByteBuf buf) {
        buf.writeInt(coordinate.getX());
        buf.writeInt(coordinate.getY());
        buf.writeInt(coordinate.getZ());
        NetworkTools.writeString(buf, getName());
        teleportDestination.toBytes(buf);
    }

    public BlockPos getCoordinate() {
        return coordinate;
    }

    public String getName() {
        return name;
    }

    public TeleportDestination getTeleportDestination() {
        return teleportDestination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TransmitterInfo that = (TransmitterInfo) o;

        if (coordinate != null ? !coordinate.equals(that.coordinate) : that.coordinate != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (!teleportDestination.equals(that.teleportDestination)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = coordinate != null ? coordinate.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (teleportDestination.hashCode());
        return result;
    }
}
