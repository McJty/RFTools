package mcjty.rftools.blocks.teleporter;

import mcjty.lib.varia.GlobalCoordinate;
import mcjty.lib.varia.Logging;
import mcjty.lib.varia.SoundTools;
import mcjty.rftools.RFTools;
import mcjty.rftools.blocks.environmental.NoTeleportAreaManager;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class TeleportationTools {
    public static final int STATUS_OK = 0;
    public static final int STATUS_WARN = 1;
    public static final int STATUS_UNKNOWN = 2;

    public static Potion confusion;
    public static Potion harm;
    public static Potion wither;

    public static void getPotions() {
        if (confusion == null) {
            confusion = Potion.REGISTRY.getObject(new ResourceLocation("nausea"));
            harm = Potion.REGISTRY.getObject(new ResourceLocation("instant_damage"));
            wither = Potion.REGISTRY.getObject(new ResourceLocation("wither"));
        }
    }

    public static void applyEffectForSeverity(EntityPlayer player, int severity, boolean boostNeeded) {
        getPotions();
        switch (severity) {
            case 1:
                if (boostNeeded) {
                    player.addPotionEffect(new PotionEffect(confusion, 100));
                    player.addPotionEffect(new PotionEffect(harm, 5));
                }
                break;
            case 2:
                player.addPotionEffect(new PotionEffect(harm, 100));
                break;
            case 3:
                player.addPotionEffect(new PotionEffect(harm, 100));
                player.attackEntityFrom(DamageSource.GENERIC, 0.5f);
                break;
            case 4:
                player.addPotionEffect(new PotionEffect(harm, 200));
                player.attackEntityFrom(DamageSource.GENERIC, 0.5f);
                break;
            case 5:
                player.addPotionEffect(new PotionEffect(harm, 200));
                player.attackEntityFrom(DamageSource.GENERIC, 1.0f);
                break;
            case 6:
                player.addPotionEffect(new PotionEffect(harm, 300));
                player.attackEntityFrom(DamageSource.GENERIC, 1.0f);
                break;
            case 7:
                player.addPotionEffect(new PotionEffect(harm, 300));
                player.addPotionEffect(new PotionEffect(wither, 200));
                player.attackEntityFrom(DamageSource.GENERIC, 2.0f);
                break;
            case 8:
                player.addPotionEffect(new PotionEffect(harm, 400));
                player.addPotionEffect(new PotionEffect(wither, 300));
                player.attackEntityFrom(DamageSource.GENERIC, 2.0f);
                break;
            case 9:
                player.addPotionEffect(new PotionEffect(harm, 400));
                player.addPotionEffect(new PotionEffect(wither, 400));
                player.attackEntityFrom(DamageSource.GENERIC, 3.0f);
                break;
            case 10:
                player.addPotionEffect(new PotionEffect(harm, 500));
                player.addPotionEffect(new PotionEffect(wither, 500));
                player.attackEntityFrom(DamageSource.GENERIC, 3.0f);
                break;
        }
    }

    /**
     * Calculate the cost of doing a dial between a transmitter and a destination.
     * @param world
     * @param c1 the start coordinate
     * @param teleportDestination
     * @return
     */
    public static int calculateRFCost(World world, BlockPos c1, TeleportDestination teleportDestination) {
        if (world.provider.getDimension() != teleportDestination.getDimension()) {
            return TeleportConfiguration.rfStartTeleportBaseDim;
        } else {
            BlockPos c2 = teleportDestination.getCoordinate();
            double dist = new Vec3d(c1.getX(), c1.getY(), c1.getZ()).distanceTo(new Vec3d(c2.getX(), c2.getY(), c2.getZ()));
            int rf = TeleportConfiguration.rfStartTeleportBaseLocal + (int)(TeleportConfiguration.rfStartTeleportDist * dist);
            if (rf > TeleportConfiguration.rfStartTeleportBaseDim) {
                rf = TeleportConfiguration.rfStartTeleportBaseDim;
            }
            return rf;
        }
    }

    /**
     * Calculate the time in ticks of doing a dial between a transmitter and a destination.
     * @param world
     * @param c1 the start coordinate
     * @param teleportDestination
     * @return
     */
    public static int calculateTime(World world, BlockPos c1, TeleportDestination teleportDestination) {
        if (world.provider.getDimension() != teleportDestination.getDimension()) {
            return TeleportConfiguration.timeTeleportBaseDim;
        } else {
            BlockPos c2 = teleportDestination.getCoordinate();
            double dist = new Vec3d(c1.getX(), c1.getY(), c1.getZ()).distanceTo(new Vec3d(c2.getX(), c2.getY(), c2.getZ()));
            int time = TeleportConfiguration.timeTeleportBaseLocal + (int)(TeleportConfiguration.timeTeleportDist * dist / 1000);
            if (time > TeleportConfiguration.timeTeleportBaseDim) {
                time = TeleportConfiguration.timeTeleportBaseDim;
            }
            return time;
        }
    }

    // Return true if we needed a boost.
    public static boolean performTeleport(EntityPlayer player, TeleportDestination dest, int bad, int good, boolean boosted) {
        BlockPos c = dest.getCoordinate();

        BlockPos old = new BlockPos((int)player.posX, (int)player.posY, (int)player.posZ);
        int oldId = player.getEntityWorld().provider.getDimension();

        if (!TeleportationTools.allowTeleport(player, oldId, old, dest.getDimension(), dest.getCoordinate())) {
            return false;
        }

        if (oldId != dest.getDimension()) {
            mcjty.lib.varia.TeleportationTools.teleportToDimension(player, dest.getDimension(), c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5);
        } else {
            player.setPositionAndUpdate(c.getX()+0.5, c.getY()+1, c.getZ()+0.5);
        }

        if (TeleportConfiguration.whooshMessage) {
            Logging.message(player, "Whoosh!");
        }
        // @todo achievements
//        Achievements.trigger(player, Achievements.firstTeleport);

        boolean boostNeeded = false;
        int severity = consumeReceiverEnergy(player, dest.getCoordinate(), dest.getDimension());
        if (severity > 0 && boosted) {
            boostNeeded = true;
            severity = 1;
        }

        severity = applyBadEffectIfNeeded(player, severity, bad, good, boostNeeded);
        if (severity <= 0) {
            if (TeleportConfiguration.teleportVolume >= 0.01) {
                SoundEvent sound = SoundEvent.REGISTRY.getObject(new ResourceLocation(RFTools.MODID, "teleport_whoosh"));
                if (sound == null) {
                    throw new RuntimeException("Could not find sound 'teleport_whoosh'!");
                } else {
                    SoundTools.playSound(player.getEntityWorld(), sound, player.posX, player.posY, player.posZ, TeleportConfiguration.teleportVolume, 1.0f);
                }
            }
        }
        if (TeleportConfiguration.logTeleportUsages) {
            Logging.log("Teleport: Player " + player.getName() + " from " + old + " (dim " + oldId + ") to " + dest.getCoordinate() + " (dim " + dest.getDimension() + ") with severity " + severity);
        }
        return boostNeeded;
    }

    // Server side only
    public static int dial(World worldObj, DialingDeviceTileEntity dialingDeviceTileEntity, String player, BlockPos transmitter, int transDim, BlockPos coordinate, int dimension, boolean once) {
        World transWorld = mcjty.lib.varia.TeleportationTools.getWorldForDimension(transDim);
        if (transWorld == null) {
            return DialingDeviceTileEntity.DIAL_INVALID_SOURCE_MASK;
        }
        MatterTransmitterTileEntity transmitterTileEntity = (MatterTransmitterTileEntity) transWorld.getTileEntity(transmitter);
        if (transmitterTileEntity == null) {
            return DialingDeviceTileEntity.DIAL_INVALID_TRANSMITTER;
        }

        if (player != null && !transmitterTileEntity.checkAccess(player)) {
            return DialingDeviceTileEntity.DIAL_TRANSMITTER_NOACCESS;
        }

        if (coordinate == null) {
            transmitterTileEntity.setTeleportDestination(null, false);
            return DialingDeviceTileEntity.DIAL_INTERRUPTED;
        }

        TeleportDestination teleportDestination = findDestination(worldObj, coordinate, dimension);
        if (teleportDestination == null) {
            return DialingDeviceTileEntity.DIAL_INVALID_DESTINATION_MASK;
        }

        BlockPos c = teleportDestination.getCoordinate();
        World recWorld = mcjty.lib.varia.TeleportationTools.getWorldForDimension(teleportDestination.getDimension());
        if (recWorld == null) {
            recWorld = worldObj.getMinecraftServer().getWorld(teleportDestination.getDimension());
            if (recWorld == null) {
                return DialingDeviceTileEntity.DIAL_INVALID_DESTINATION_MASK;
            }
        }

        // Only do this if not an rftools dimension.
        TileEntity tileEntity = recWorld.getTileEntity(c);
        if (!(tileEntity instanceof MatterReceiverTileEntity)) {
            return DialingDeviceTileEntity.DIAL_INVALID_DESTINATION_MASK;
        }
        MatterReceiverTileEntity matterReceiverTileEntity = (MatterReceiverTileEntity) tileEntity;
        matterReceiverTileEntity.updateDestination();       // Make sure destination is ok.
        if (player != null && !matterReceiverTileEntity.checkAccess(player)) {
            return DialingDeviceTileEntity.DIAL_RECEIVER_NOACCESS;
        }

        if (!checkBeam(transmitter, transWorld, 1, 4, 2)) {
            return DialingDeviceTileEntity.DIAL_TRANSMITTER_BLOCKED_MASK;
        }

        if (dialingDeviceTileEntity != null) {
            int cost = TeleportConfiguration.rfPerDial;
            cost = (int) (cost * (2.0f - dialingDeviceTileEntity.getInfusedFactor()) / 2.0f);

            if (dialingDeviceTileEntity.getEnergyStored() < cost) {
                return DialingDeviceTileEntity.DIAL_DIALER_POWER_LOW_MASK;
            }

            dialingDeviceTileEntity.consumeEnergy(cost);
        }

        transmitterTileEntity.setTeleportDestination(teleportDestination, once);

        return DialingDeviceTileEntity.DIAL_OK;
    }


    /**
     * Consume energy on the receiving side and return a number indicating how good this went.
     *
     * @param c
     * @param dimension
     * @return 0 in case of success. 10 in case of severe failure
     */
    private static int consumeReceiverEnergy(EntityPlayer player, BlockPos c, int dimension) {
        World world = DimensionManager.getWorld(dimension);
        TileEntity te = world.getTileEntity(c);
        if (!(te instanceof MatterReceiverTileEntity)) {
            Logging.warn(player, "Something went wrong with the destination!");
            return 0;
        }

        MatterReceiverTileEntity matterReceiverTileEntity = (MatterReceiverTileEntity) te;
        int rf = TeleportConfiguration.rfPerTeleportReceiver;
        rf = (int) (rf * (2.0f - matterReceiverTileEntity.getInfusedFactor()) / 2.0f);

        if (rf <= 0) {
            return 0;
        }

        int extracted = rf;
        if (rf > matterReceiverTileEntity.getEnergyStored()) {
            extracted = matterReceiverTileEntity.getEnergyStored();
        }
        matterReceiverTileEntity.consumeEnergy(rf);

        int remainingRf = matterReceiverTileEntity.getEnergyStored();
        if (remainingRf <= 1) {
            Logging.warn(player, "The matter receiver has run out of power!");
        } else if (remainingRf < (TeleportConfiguration.RECEIVER_MAXENERGY / 10)) {
            Logging.warn(player, "The matter receiver is getting very low on power!");
        } else if (remainingRf < (TeleportConfiguration.RECEIVER_MAXENERGY / 5)) {
            Logging.warn(player, "The matter receiver is getting low on power!");
        }

        return 10 - (extracted * 10 / rf);
    }

    /**
     * Return a number between 0 and 10 indicating the severity of the teleportation.
     * @return
     */
    public static int calculateSeverity(int bad, int total) {
        if (total == 0) {
            total = 1;
        }
        int severity = bad * 10 / total;
        if (mustInterrupt(bad, total)) {
            // If an interrupt was done then severity is worse.
            severity += 2;
        }
        if (severity > 10) {
            severity = 10;
        }
        return severity;
    }

    public static int applyBadEffectIfNeeded(EntityPlayer player, int severity, int bad, int total, boolean boostNeeded) {
        if (player == null) {
            return 0;
        }
        severity += calculateSeverity(bad, total);
        if (severity > 10) {
            severity = 10;
        }
        if (severity <= 0) {
            return 0;
        }

        if (TeleportConfiguration.teleportErrorVolume >= 0.01) {
            SoundEvent sound = SoundEvent.REGISTRY.getObject(new ResourceLocation(RFTools.MODID, "teleport_error"));
            if (sound == null) {
                throw new RuntimeException("Could not find sound 'teleport_error'!");
            } else {
                SoundTools.playSound(player.getEntityWorld(), sound, player.posX, player.posY, player.posZ, TeleportConfiguration.teleportVolume, 1.0f);
            }
        }

        applyEffectForSeverity(player, severity, boostNeeded);
        return severity;
    }

    public static boolean mustInterrupt(int bad, int total) {
        return bad > (total / 2);
    }

    public static boolean allowTeleport(Entity entity, int sourceDim, BlockPos source, int destDim, BlockPos dest) {
        if (NoTeleportAreaManager.isTeleportPrevented(entity, new GlobalCoordinate(source, sourceDim))) {
            return false;
        }
        if (NoTeleportAreaManager.isTeleportPrevented(entity, new GlobalCoordinate(dest, destDim))) {
            return false;
        }
        return true;
    }

    public static TeleportDestination findDestination(World worldObj, BlockPos coordinate, int dimension) {
        TeleportDestinations destinations = TeleportDestinations.getDestinations(worldObj);
        return destinations.getDestination(coordinate, dimension);
    }

    // Check if there is room for a beam.
    public static boolean checkBeam(BlockPos c, World world, int dy1, int dy2, int errory) {
        for (int dy = dy1 ; dy <= dy2 ; dy++) {
            BlockPos pos = new BlockPos(c.getX(), c.getY() + dy, c.getZ());
            IBlockState state = world.getBlockState(pos);
            Block b = state.getBlock();
            if (!b.isAir(state, world, pos)) {
                if (dy <= errory) {
                    // Everything below errory must be free.
                    return false;
                } else {
                    // Everything higher then errory doesn't have to be free.
                    break;
                }
            }
        }
        return true;
    }

    public static boolean checkValidTeleport(EntityPlayer player, int srcId, int dstId) {
        if (TeleportConfiguration.preventInterdimensionalTeleports) {
            if (srcId == dstId) {
                Logging.warn(player, "Teleportation in the same dimension is not allowed!");
                return false;
            }
        }
        if (TeleportConfiguration.getBlacklistedTeleportationDestinations().contains(dstId)) {
            Logging.warn(player, "Teleportation to that dimension is not allowed!");
            return false;
        }
        if (TeleportConfiguration.getBlacklistedTeleportationSources().contains(srcId)) {
            Logging.warn(player, "Teleportation from this dimension is not allowed!");
            return false;
        }
        return true;
    }
}
