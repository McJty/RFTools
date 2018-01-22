package mcjty.rftools.blocks.teleporter;

import mcjty.lib.entity.GenericEnergyReceiverTileEntity;
import mcjty.lib.network.Argument;
import mcjty.lib.varia.GlobalCoordinate;
import mcjty.rftools.playerprops.FavoriteDestinationsProperties;
import mcjty.rftools.playerprops.PlayerExtendedProperties;
import mcjty.typed.Type;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DialingDeviceTileEntity extends GenericEnergyReceiverTileEntity {

    public static final String CMD_TELEPORT = "tp";
    public static final String CMD_GETRECEIVERS = "getReceivers";
    public static final String CLIENTCMD_GETRECEIVERS = "getReceivers";
    public static final String CMD_DIAL = "dial";
    public static final String CMD_DIALONCE = "dialOnce";
    public static final String CMD_FAVORITE = "favorite";
    public static final String CMD_SHOWFAVORITE = "showFavorite";
    public static final String CLIENTCMD_DIAL = "dialResult";
    public static final String CMD_GETTRANSMITTERS = "getTransmitters";
    public static final String CLIENTCMD_GETTRANSMITTERS = "getTransmitters";
    public static final String CMD_CHECKSTATUS = "checkStatus";
    public static final String CLIENTCMD_STATUS = "status";
    public static final int DIAL_RECEIVER_BLOCKED_MASK = 0x1;       // One value for blocked or not on receiver side
    public static final int DIAL_TRANSMITTER_BLOCKED_MASK = 0x2;    // One value for blocked or not on transmitter side
    public static final int DIAL_INVALID_DESTINATION_MASK = 0x4;    // The destination is somehow invalid
    public static final int DIAL_DIALER_POWER_LOW_MASK = 0x8;       // The dialer itself is low on power
    public static final int DIAL_RECEIVER_POWER_LOW_MASK = 0x10;    // The receiver is low on power
    public static final int DIAL_TRANSMITTER_NOACCESS = 0x20;       // No access to transmitter
    public static final int DIAL_RECEIVER_NOACCESS = 0x40;          // No access to receiver
    public static final int DIAL_INTERRUPTED = 0x80;                // The dial was interrupted
    public static final int DIAL_INVALID_SOURCE_MASK = 0x100;       // The source is somehow invalid
    public static final int DIAL_DIMENSION_POWER_LOW_MASK = 0x200;  // The destination dimension is low on power
    public static final int DIAL_INVALID_TRANSMITTER = 0x400;       // The transmitter is gone!
    public static final int DIAL_OK = 0;                            // All is ok
    public static final String COMPONENT_NAME = "dialing_device";

    private boolean showOnlyFavorites = false;

    public DialingDeviceTileEntity() {
        super(TeleportConfiguration.DIALER_MAXENERGY, TeleportConfiguration.DIALER_RECEIVEPERTICK);
    }

    /**
     * Calculate the distance (in string form) between a transmitter and receiver.
     * @param world
     * @param transmitterInfo
     * @param teleportDestination
     * @return the distance or else 'dimension warp' in case it is another dimension.
     */
    public static String calculateDistance(World world, TransmitterInfo transmitterInfo, TeleportDestination teleportDestination) {
        if (world.provider.getDimension() != teleportDestination.getDimension()) {
            return "dimension warp";
        } else {
            BlockPos c1 = transmitterInfo.getCoordinate();
            BlockPos c2 = teleportDestination.getCoordinate();
            double dist = new Vec3d(c1.getX(), c1.getY(), c1.getZ()).distanceTo(new Vec3d(c2.getX(), c2.getY(), c2.getZ()));
            return Integer.toString((int) dist);
        }
    }

    public static boolean isMatterBoosterAvailable(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (TeleporterSetup.matterBoosterBlock.equals(world.getBlockState(pos.offset(facing)).getBlock())) {
                return true;
            }
        }
        return false;
    }


    public static boolean isDestinationAnalyzerAvailable(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (TeleporterSetup.destinationAnalyzerBlock.equals(world.getBlockState(pos.offset(facing)).getBlock())) {
                return true;
            }
        }
        return false;
    }

    public boolean isShowOnlyFavorites() {
        return showOnlyFavorites;
    }

    public void setShowOnlyFavorites(boolean showOnlyFavorites) {
        this.showOnlyFavorites = showOnlyFavorites;
        markDirtyClient();
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);
        showOnlyFavorites = tagCompound.getBoolean("showFav");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        return tagCompound;
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        tagCompound.setBoolean("showFav", showOnlyFavorites);
    }

    private List<TeleportDestinationClientInfo> searchReceivers(String playerName) {
        TeleportDestinations destinations = TeleportDestinations.getDestinations(getWorld());
        return new ArrayList<>(destinations.getValidDestinations(getWorld(), playerName));
    }

    public List<TransmitterInfo> searchTransmitters() {
        int x = getPos().getX();
        int y = getPos().getY();
        int z = getPos().getZ();

        int hrange = TeleportConfiguration.horizontalDialerRange;
        int vrange = TeleportConfiguration.verticalDialerRange;

        List<TransmitterInfo> transmitters = new ArrayList<>();
        for (int dy = -vrange ; dy <= vrange ; dy++) {
            int yy = y + dy;
            if (yy >= 0 && yy < getWorld().getHeight()) {
                for (int dz = -hrange ; dz <= hrange; dz++) {
                    int zz = z + dz;
                    for (int dx = -hrange ; dx <= hrange ; dx++) {
                        int xx = x + dx;
                        if (dx != 0 || dy != 0 || dz != 0) {
                            BlockPos c = new BlockPos(xx, yy, zz);
                            TileEntity tileEntity = getWorld().getTileEntity(c);
                            if (tileEntity instanceof MatterTransmitterTileEntity) {
                                MatterTransmitterTileEntity matterTransmitterTileEntity = (MatterTransmitterTileEntity) tileEntity;
                                transmitters.add(new TransmitterInfo(c, matterTransmitterTileEntity.getName(), matterTransmitterTileEntity.getTeleportDestination()));
                            }
                        }
                    }
                }
            }
        }
        return transmitters;
    }

    // Server side only.
    private void changeFavorite(String playerName, BlockPos receiver, int dimension, boolean favorite) {
        List<EntityPlayerMP> list = ((WorldServer) getWorld()).getMinecraftServer().getPlayerList().getPlayers();
        for (EntityPlayerMP entityplayermp : list) {
            if (playerName.equals(entityplayermp.getName())) {
                FavoriteDestinationsProperties favoriteDestinations = PlayerExtendedProperties.getFavoriteDestinations(entityplayermp);
                favoriteDestinations.setDestinationFavorite(new GlobalCoordinate(receiver, dimension), favorite);
                return;
            }
        }
    }


    // Server side only
    private int dial(String player, BlockPos transmitter, int transDim, BlockPos coordinate, int dimension, boolean once) {
        return TeleportationTools.dial(getWorld(), this, player, transmitter, transDim, coordinate, dimension, once);
    }

    // Server side only
    private int checkStatus(BlockPos c, int dim) {
        int cost = TeleportConfiguration.rfPerCheck;
        cost = (int) (cost * (2.0f - getInfusedFactor()) / 2.0f);

        if (getEnergyStored() < cost) {
            return DialingDeviceTileEntity.DIAL_DIALER_POWER_LOW_MASK;
        }
        consumeEnergy(cost);

        World w = mcjty.lib.varia.TeleportationTools.getWorldForDimension(dim);
        if (w == null) {
            TeleportDestinations destinations = TeleportDestinations.getDestinations(getWorld());
            destinations.cleanupInvalid(getWorld());
            return DialingDeviceTileEntity.DIAL_INVALID_DESTINATION_MASK;
        }

        TileEntity tileEntity = w.getTileEntity(c);
        if (!(tileEntity instanceof MatterReceiverTileEntity)) {
            TeleportDestinations destinations = TeleportDestinations.getDestinations(getWorld());
            destinations.cleanupInvalid(getWorld());
            return DialingDeviceTileEntity.DIAL_INVALID_DESTINATION_MASK;
        }

        // @todo with plugin system
//        if (dimensionManager.getDimensionInformation(dim) != null) {
//            // This is an RFTools dimension. Check power.
//            DimensionStorage dimensionStorage = DimensionStorage.getDimensionStorage(w);
//            int energyLevel = dimensionStorage.getEnergyLevel(dim);
//            if (energyLevel < DimletConfiguration.DIMPOWER_WARN_TP) {
//                return DialingDeviceTileEntity.DIAL_DIMENSION_POWER_LOW_MASK;
//            }
//        }

        MatterReceiverTileEntity matterReceiverTileEntity = (MatterReceiverTileEntity) tileEntity;

        return matterReceiverTileEntity.checkStatus();
    }

    @Nonnull
    @Override
    public <T> List<T> executeWithResultList(String command, Map<String, Argument> args, Type<T> type) {
        List<T> rc = super.executeWithResultList(command, args, type);
        if (!rc.isEmpty()) {
            return rc;
        }
        if (CMD_GETRECEIVERS.equals(command)) {
            String playerName = args.get("player").getString();
            return type.convert(searchReceivers(playerName));
        } else if (CMD_GETTRANSMITTERS.equals(command)) {
            return type.convert(searchTransmitters());
        }
        return Collections.emptyList();
    }


    @Override
    public boolean execute(EntityPlayerMP playerMP, String command, Map<String, Argument> args) {
        boolean rc = super.execute(playerMP, command, args);
        if (rc) {
            return rc;
        }
        if (CMD_FAVORITE.equals(command)) {
            String player = args.get("player").getString();
            BlockPos receiver = args.get("receiver").getCoordinate();
            int dimension = args.get("dimension").getInteger();
            boolean favorite = args.get("favorite").getBoolean();
            changeFavorite(player, receiver, dimension, favorite);
            return true;
        } else if (CMD_SHOWFAVORITE.equals(command)) {
            boolean favorite = args.get("favorite").getBoolean();
            setShowOnlyFavorites(favorite);
            return true;
        }

        return false;
    }

    @Override
    public Integer executeWithResultInteger(String command, Map<String, Argument> args) {
        Integer rc = super.executeWithResultInteger(command, args);
        if (rc != null) {
            return rc;
        }
        if (CMD_CHECKSTATUS.equals(command)) {
            BlockPos c = args.get("c").getCoordinate();
            int dim = args.get("dim").getInteger();
            return checkStatus(c, dim);
        } else if (CMD_DIAL.equals(command)) {
            String player = args.get("player").getString();
            BlockPos transmitter = args.get("trans").getCoordinate();
            int transDim = args.get("transDim").getInteger();
            BlockPos c = args.get("c").getCoordinate();
            int dim = args.get("dim").getInteger();

            return dial(player, transmitter, transDim, c, dim, false);
        } else if (CMD_DIALONCE.equals(command)) {
            String player = args.get("player").getString();
            BlockPos transmitter = args.get("trans").getCoordinate();
            int transDim = args.get("transDim").getInteger();
            BlockPos c = args.get("c").getCoordinate();
            int dim = args.get("dim").getInteger();

            return dial(player, transmitter, transDim, c, dim, true);
        }
        return null;
    }

    @Override
    public <T> boolean execute(String command, List<T> list, Type<T> type) {
        boolean rc = super.execute(command, list, type);
        if (rc) {
            return true;
        }
        if (CLIENTCMD_GETRECEIVERS.equals(command)) {
            GuiDialingDevice.fromServer_receivers = Type.create(TeleportDestinationClientInfo.class).convert(list);
            return true;
        } else if (CLIENTCMD_GETTRANSMITTERS.equals(command)) {
            GuiDialingDevice.fromServer_transmitters = Type.create(TransmitterInfo.class).convert(list);
            return true;
        }
        return false;
    }

    @Override
    public boolean execute(String command, Integer result) {
        boolean rc = super.execute(command, result);
        if (rc) {
            return true;
        }
        if (CLIENTCMD_STATUS.equals(command)) {
            GuiDialingDevice.fromServer_receiverStatus = result;
            return true;
        } else if (CLIENTCMD_DIAL.equals(command)) {
            GuiDialingDevice.fromServer_dialResult = result;
            return true;
        }
        return false;
    }
}
